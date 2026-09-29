package com.trimax.linkhub.service;

import com.trimax.linkhub.api.*;
import com.trimax.linkhub.domain.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class LinkHubService {
    private static final Set<String> RESERVED = Set.of("api", "app", "admin", "login", "register", "logout", "dashboard", "assets", "static", "error", "actuator", "health", "www", "support", "index", "favicon", "robots");
    private final EntityManager em;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AnalyticsService events;

    @Transactional
    public Views.Me register(Requests.Register input) {
        String username = input.username().toLowerCase(Locale.ROOT);
        String email = normalizeEmail(input.email());
        if (RESERVED.contains(username)) throw badRequest("Username này được dành riêng cho hệ thống.");
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw badRequest("Mật khẩu tối đa 72 byte UTF-8.");
        if (em.createQuery("select count(a) from Account a where a.email=:email or a.username=:username", Long.class)
                .setParameter("email", email).setParameter("username", username).getSingleResult() > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email hoặc username đã được sử dụng.");
        Account a = new Account();
        a.setEmail(email); a.setUsername(username); a.setDisplayName(input.displayName().strip());
        a.setPasswordHash(encoder.encode(input.password()));
        em.persist(a); em.flush();
        return Views.Me.of(a);
    }

    public Optional<Account> findByEmail(String email) {
        return em.createQuery("select a from Account a where a.email=:email", Account.class)
                .setParameter("email", normalizeEmail(email)).getResultStream().findFirst();
    }
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    public Views.Me me(UUID owner) { return Views.Me.of(account(owner)); }
    private Account account(UUID id) {
        Account a = em.find(Account.class, id);
        if (a == null) throw notFound();
        return a;
    }
    private Account published(String username) {
        return em.createQuery("select a from Account a where a.username=:username and a.published=true", Account.class)
                .setParameter("username", username.toLowerCase(Locale.ROOT)).getResultStream().findFirst().orElseThrow(LinkHubService::notFound);
    }
    @Transactional
    public Views.Me updateProfile(UUID owner, Requests.Profile input) {
        Account a = account(owner);
        a.setDisplayName(input.displayName().strip()); a.setBio(input.bio().strip()); a.setPublished(input.published());
        // Older clients can still update profile details without resetting the selected template.
        if (input.template() != null) a.setProfileTemplate(input.template());
        return Views.Me.of(a);
    }
    public Views.Profile publicProfile(String username) {
        Account a = published(username);
        return new Views.Profile(a.getUsername(), a.getDisplayName(), a.getBio(), links(a.getId()), services(a.getId()), a.getProfileTemplate(), blocks(a.getId()).stream().filter(Views.Block::enabled).toList());
    }
    public List<Views.Block> blocks(UUID owner) {
        return em.createQuery("select b from LandingBlock b where b.ownerId=:owner order by b.sortOrder, b.id", LandingBlock.class)
                .setParameter("owner", owner).getResultStream().map(Views.Block::of).toList();
    }
    @Transactional
    public Views.Block saveBlock(UUID owner, UUID id, Requests.Block input) {
        String url = input.url().strip();
        if (!url.isEmpty()) validateUrl(url);
        em.lock(account(owner), LockModeType.PESSIMISTIC_WRITE);
        LandingBlock block;
        if (id == null) {
            if (blocks(owner).size() >= 30) throw badRequest("Tối đa 30 mục nội dung cho mỗi profile.");
            block = new LandingBlock(); block.setOwnerId(owner);
        } else {
            block = em.find(LandingBlock.class, id);
            if (block == null || !block.getOwnerId().equals(owner)) throw notFound();
        }
        block.setType(input.type()); block.setTitle(input.title().strip()); block.setBody(input.body().strip());
        block.setUrl(url); block.setSortOrder(input.sortOrder()); block.setEnabled(input.enabled());
        if (id == null) em.persist(block);
        return Views.Block.of(block);
    }
    @Transactional
    public void deleteBlock(UUID owner, UUID id) {
        LandingBlock block = em.find(LandingBlock.class, id);
        if (block == null || !block.getOwnerId().equals(owner)) throw notFound();
        em.remove(block);
    }
    public List<Views.Link> links(UUID owner) {
        return em.createQuery("select l from SocialLink l where l.ownerId=:owner order by l.sortOrder, l.id", SocialLink.class)
                .setParameter("owner", owner).getResultStream().map(Views.Link::of).toList();
    }
    @Transactional
    public Views.Link saveLink(UUID owner, UUID id, Requests.Link input) {
        validateUrl(input.url());
        em.lock(account(owner), LockModeType.PESSIMISTIC_WRITE);
        SocialLink l;
        if (id == null) {
            if (links(owner).size() >= 30) throw badRequest("Tối đa 30 link cho mỗi profile.");
            l = new SocialLink(); l.setOwnerId(owner);
        } else {
            l = em.find(SocialLink.class, id);
            if (l == null || !l.getOwnerId().equals(owner)) throw notFound();
        }
        l.setTitle(input.title().strip()); l.setUrl(input.url()); l.setSortOrder(input.sortOrder());
        if (id == null) em.persist(l);
        return Views.Link.of(l);
    }
    @Transactional
    public void deleteLink(UUID owner, UUID id) {
        SocialLink l = em.find(SocialLink.class, id);
        if (l == null || !l.getOwnerId().equals(owner)) throw notFound();
        em.remove(l);
    }
    public List<Views.Offering> services(UUID owner) {
        return em.createQuery("select s from ServiceOffering s where s.ownerId=:owner order by s.title, s.id", ServiceOffering.class)
                .setParameter("owner", owner).getResultStream().map(Views.Offering::of).toList();
    }
    @Transactional
    public Views.Offering saveService(UUID owner, UUID id, Requests.Offering input) {
        em.lock(account(owner), LockModeType.PESSIMISTIC_WRITE);
        ServiceOffering s;
        if (id == null) {
            if (services(owner).size() >= 30) throw badRequest("Tối đa 30 dịch vụ cho mỗi profile.");
            s = new ServiceOffering(); s.setOwnerId(owner);
        } else {
            s = em.find(ServiceOffering.class, id);
            if (s == null || !s.getOwnerId().equals(owner)) throw notFound();
        }
        s.setTitle(input.title().strip()); s.setDescription(input.description().strip()); s.setPrice(input.price());
        if (input.durationMinutes() != null) s.setDurationMinutes(input.durationMinutes());
        if (id == null) em.persist(s);
        return Views.Offering.of(s);
    }
    @Transactional
    public void deleteService(UUID owner, UUID id) {
        em.lock(account(owner), LockModeType.PESSIMISTIC_WRITE);
        ServiceOffering s = em.find(ServiceOffering.class, id);
        if (s == null || !s.getOwnerId().equals(owner)) throw notFound();
        em.remove(s);
    }
    @Transactional
    public UUID contact(String username, Requests.Contact input) {
        Account a = published(username);
        ContactRequest c = new ContactRequest();
        c.setOwnerId(a.getId()); c.setName(input.name().strip()); c.setEmail(normalizeEmail(input.email())); c.setMessage(input.message().strip());
        em.persist(c);
        String title="Bạn có yêu cầu liên hệ mới";
        String message=c.getName()+" ("+c.getEmail()+")\n\n"+c.getMessage();
        notifications.record(a.getId(),"contact:"+c.getId(),"CONTACT_NEW",title,message,"inbox",
                List.of(new NotificationService.Mail(a.getEmail(),title,message+"\n\nMở dashboard LinkHub để xem và xử lý yêu cầu.")));
        return c.getId();
    }
    public Views.Page<Views.Contact> contacts(UUID owner, int page, int size) {
        var items = em.createQuery("select c from ContactRequest c where c.ownerId=:owner order by c.createdAt desc, c.id", ContactRequest.class)
                .setParameter("owner", owner).setFirstResult(page * size).setMaxResults(size).getResultStream().map(Views.Contact::of).toList();
        long total = em.createQuery("select count(c) from ContactRequest c where c.ownerId=:owner", Long.class).setParameter("owner", owner).getSingleResult();
        return new Views.Page<>(items, page, size, total);
    }
    @Transactional
    public Views.Contact contactStatus(UUID owner, UUID id, ContactRequest.Status status) {
        ContactRequest c = em.find(ContactRequest.class, id);
        if (c == null || !c.getOwnerId().equals(owner)) throw notFound();
        c.setStatus(status);
        return Views.Contact.of(c);
    }
    public Views.Analytics analytics(UUID owner, int days) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        LocalDate start = today.minusDays(days - 1L);
        var rows = em.createQuery("select v from DailyVisit v where v.ownerId=:owner and v.visitDate>=:start order by v.visitDate", DailyVisit.class)
                .setParameter("owner", owner).setParameter("start", start).getResultList();
        Map<LocalDate, Long> counts = new HashMap<>();
        rows.forEach(v -> counts.put(v.getVisitDate(), v.getViews()));
        events.daily(owner,start).forEach((date,count)->counts.merge(date,count,Long::sum));
        List<Views.Day> daily = start.datesUntil(today.plusDays(1)).map(d -> new Views.Day(d, counts.getOrDefault(d, 0L))).toList();
        long total = em.createQuery("select coalesce(sum(v.views),0) from DailyVisit v where v.ownerId=:owner", Long.class).setParameter("owner", owner).getSingleResult();
        long contacts = em.createQuery("select count(c) from ContactRequest c where c.ownerId=:owner", Long.class).setParameter("owner", owner).getSingleResult();
        long unread = em.createQuery("select count(c) from ContactRequest c where c.ownerId=:owner and c.status=:status", Long.class)
                .setParameter("owner", owner).setParameter("status", ContactRequest.Status.NEW).getSingleResult();
        return new Views.Analytics(total+events.total(owner), contacts, unread, daily);
    }
    private static void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw badRequest("Link phải là URL http hoặc https hợp lệ, không chứa thông tin đăng nhập."); }
    }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."); }
}
