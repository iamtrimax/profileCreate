package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class BookingReminderService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final NotificationService notifications;
    public record UpcomingItem(UUID id,String serviceTitle,String name,Instant startsAt,Instant endsAt,String timezone) {}
    public record Upcoming(List<UpcomingItem> items,long total,long pending,Instant serverTime) {}

    @Transactional(readOnly=true)
    public List<UUID> dueIds() {
        Instant now=clock.instant();
        return jdbc.query("""
            select b.id from bookings b where b.status='CONFIRMED' and b.starts_at>? and b.starts_at<=?
            and not exists (select 1 from booking_reminders r where r.booking_id=b.id
              and r.stage=case when b.starts_at<=? then 'HOUR' else 'DAY' end)
            order by b.starts_at,b.id limit 100
            """,(r,n)->r.getObject(1,UUID.class),Timestamp.from(now),Timestamp.from(now.plusSeconds(86400)),Timestamp.from(now.plusSeconds(3600)));
    }
    @Transactional
    public void create(UUID id) {
        var rows=jdbc.query("select * from bookings where id=? for update",(r,n)->new ReminderBooking(
                r.getObject("owner_id",UUID.class),r.getString("status"),r.getString("service_title"),r.getString("customer_name"),r.getString("customer_email"),
                r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("timezone")),id);
        if(rows.isEmpty())return;
        var b=rows.getFirst();Instant now=clock.instant();
        if(!b.status().equals("CONFIRMED")||!b.start().isAfter(now)||b.start().isAfter(now.plusSeconds(86400)))return;
        String stage=b.start().isAfter(now.plusSeconds(3600))?"DAY":"HOUR";
        if(jdbc.queryForObject("select count(*) from booking_reminders where booking_id=? and stage=?",Integer.class,id,stage)>0)return;
        String title=stage.equals("DAY")?"Bạn có lịch hẹn trong 24 giờ tới":"Lịch hẹn sắp bắt đầu trong 1 giờ tới";
        var format=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm XXX").withZone(ZoneId.of(b.zone()));
        String body=b.title()+"\nKhách hàng: "+b.name()+"\n"+format.format(b.start())+" → "+format.format(b.end())+" ("+b.zone()+")\nMã lịch: "+id;
        String key="reminder:"+id+":"+stage;
        notifications.record(b.owner(),key,"BOOKING_REMINDER",title,body,"bookings",List.of(
                new NotificationService.Mail(b.email(),"LinkHub · Nhắc lịch hẹn", "Xin chào "+b.name()+",\n\nBạn có lịch hẹn đã xác nhận:\n"+body+"\n\nVui lòng chuẩn bị trước giờ hẹn. Nếu cần thay đổi, hãy liên hệ chủ profile.")));
        UUID notification=jdbc.queryForObject("select id from notifications where event_key=?",UUID.class,key);
        Instant expires=stage.equals("DAY")?b.start().minusSeconds(3600):b.start();
        jdbc.update("insert into booking_reminders(booking_id,stage,notification_id,expires_at) values (?,?,?,?)",id,stage,notification,Timestamp.from(expires));
    }
    private record ReminderBooking(UUID owner,String status,String title,String name,String email,Instant start,Instant end,String zone) {}
    @Transactional(readOnly=true)
    public Upcoming upcoming(UUID owner) {
        Instant now=clock.instant();Timestamp time=Timestamp.from(now),until=Timestamp.from(now.plusSeconds(86400));
        var items=jdbc.query("select * from bookings where owner_id=? and status='CONFIRMED' and ends_at>? and starts_at<=? order by starts_at,id limit 5",
                (r,n)->new UpcomingItem(r.getObject("id",UUID.class),r.getString("service_title"),r.getString("customer_name"),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("timezone")),owner,time,until);
        long total=jdbc.queryForObject("select count(*) from bookings where owner_id=? and status='CONFIRMED' and ends_at>? and starts_at<=?",Long.class,owner,time,until);
        long pending=jdbc.queryForObject("select count(*) from bookings where owner_id=? and status='PENDING' and starts_at>? and starts_at<=?",Long.class,owner,time,until);
        return new Upcoming(items,total,pending,now);
    }
}
