package com.trimax.linkhub.service;

import jakarta.servlet.http.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AnalyticsService {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public AnalyticsService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void record(String username, UUID link, String referrer, HttpServletRequest request,
            HttpServletResponse response) {
        var owners = jdbc.query("select id from accounts where username=? and published=true",
                (r, n) -> r.getObject(1, UUID.class), username.toLowerCase(Locale.ROOT));
        if (owners.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        UUID owner = owners.getFirst();
        String title = null;
        if (link != null) {
            var titles = jdbc.query("select title from social_links where id=? and owner_id=?",
                    (r, n) -> r.getString(1), link, owner);
            if (titles.isEmpty())
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            title = titles.getFirst();
        }
        String agent = Objects.toString(request.getHeader("User-Agent"), "").toLowerCase(Locale.ROOT);
        if ("1".equals(request.getHeader("DNT")) || "1".equals(request.getHeader("Sec-GPC"))
                || agent.matches(".*(bot|crawler|spider|headless|curl|wget|preview).*"))
            return;
        String visitor = null;
        if (request.getCookies() != null)
            for (Cookie cookie : request.getCookies())
                if (cookie.getName().equals("LH_VISITOR") && cookie.getValue().matches("[0-9a-f-]{36}"))
                    visitor = cookie.getValue();
        if (visitor == null) {
            visitor = UUID.randomUUID().toString();
            response.addHeader(HttpHeaders.SET_COOKIE,
                    ResponseCookie.from("LH_VISITOR", visitor).httpOnly(true).secure(request.isSecure()).sameSite("Lax")
                            .path("/").maxAge(Duration.ofDays(1)).build().toString());
        }
        LocalDate date = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        String host = "";
        try {
            URI uri = URI.create(Objects.toString(referrer, ""));
            if (Set.of("http", "https").contains(Objects.toString(uri.getScheme(), "")) && uri.getHost() != null
                    && !uri.getHost().equalsIgnoreCase(request.getServerName()) && uri.getHost().length() <= 253)
                host = uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
        }
        jdbc.update(
                "insert into analytics_events(id,owner_id,event_type,event_date,visitor_hash,referrer_host,link_id,link_title,created_at) values (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), owner, link == null ? "VIEW" : "CLICK", date,
                AccountSecurityService.hash(owner + ":" + date + ":" + visitor), host, link, title,
                Timestamp.from(clock.instant()));
    }

    public long total(UUID owner) {
        return jdbc.queryForObject("select count(*) from analytics_events where owner_id=? and event_type='VIEW'",
                Long.class, owner);
    }

    public Map<LocalDate, Long> daily(UUID owner, LocalDate start) {
        Map<LocalDate, Long> result = new HashMap<>();
        jdbc.query(
                "select event_date,count(*) as n from analytics_events where owner_id=? and event_type='VIEW' and event_date>=? group by event_date",
                r -> {
                    result.put(r.getDate(1).toLocalDate(), r.getLong(2));
                }, owner, start);
        return result;
    }

    public record Count(String label, long count) {
    }

    public record Engagement(long views, long dailyUniqueVisitors, long clicks, List<Count> referrers,
            List<Count> links) {
    }

    public Engagement engagement(UUID owner, int days) {
        LocalDate start = LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh"))).minusDays(days - 1L);
        long views = jdbc.queryForObject(
                "select count(*) from analytics_events where owner_id=? and event_date>=? and event_type='VIEW'",
                Long.class, owner, start);
        long unique = jdbc.queryForObject(
                "select count(*) from (select event_date,visitor_hash from analytics_events where owner_id=? and event_date>=? and event_type='VIEW' and visitor_hash is not null group by event_date,visitor_hash) u",
                Long.class, owner, start);
        long clicks = jdbc.queryForObject(
                "select count(*) from analytics_events where owner_id=? and event_date>=? and event_type='CLICK'",
                Long.class, owner, start);
        var refs = jdbc.query(
                "select referrer_host,count(*) as n from analytics_events where owner_id=? and event_date>=? and event_type='VIEW' group by referrer_host order by n desc,referrer_host limit 10",
                (r, n) -> new Count(r.getString(1).isEmpty() ? "Trực tiếp / không xác định" : r.getString(1),
                        r.getLong(2)),
                owner, start);
        var links = jdbc.query(
                "select link_title,count(*) as n from analytics_events where owner_id=? and event_date>=? and event_type='CLICK' group by link_id,link_title order by n desc,link_title limit 10",
                (r, n) -> new Count(r.getString(1), r.getLong(2)), owner, start);
        return new Engagement(views, unique, clicks, refs, links);
    }

    @Scheduled(fixedDelay = 86400000, initialDelay = 60000)
    public void anonymize() {
        jdbc.update(
                "update analytics_events set visitor_hash=null,referrer_host='' where event_date<? and (visitor_hash is not null or referrer_host<>'')",
                LocalDate.now(clock).minusDays(90));
    }
}
