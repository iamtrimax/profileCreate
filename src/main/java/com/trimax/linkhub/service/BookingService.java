package com.trimax.linkhub.service;

import com.trimax.linkhub.api.BookingModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class BookingService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final NotificationService notifications;
    private record Owner(UUID id, String timezone) {}
    private Owner owner(String username, boolean lock) {
        String suffix = lock ? " for share" : "";
        // H2 has no FOR SHARE. It is not used to verify concurrent booking safety.
        if (lock && Boolean.TRUE.equals(jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName().equals("H2")))) suffix = " for update";
        return jdbc.query("select id, booking_timezone from accounts where username=? and published=true" + suffix,
                (r,n) -> new Owner(r.getObject(1, UUID.class), r.getString(2)), username.toLowerCase(Locale.ROOT)).stream().findFirst().orElseThrow(BookingService::missing);
    }
    public Schedule schedule(UUID owner) {
        String zone = jdbc.queryForObject("select booking_timezone from accounts where id=?", String.class, owner);
        var rules = jdbc.query("select day_of_week,start_minute,end_minute from availability_rules where owner_id=? order by day_of_week,start_minute",
                (r,n) -> new Rule(r.getInt(1),r.getInt(2),r.getInt(3)),owner);
        var exceptions = jdbc.query("select exception_date,closed,start_minute,end_minute from availability_exceptions where owner_id=? order by exception_date",
                (r,n) -> new ExceptionDay(r.getObject(1,LocalDate.class),r.getBoolean(2),r.getObject(3,Integer.class),r.getObject(4,Integer.class)),owner);
        return new Schedule(zone,rules,exceptions);
    }
    @Transactional public Schedule saveSchedule(UUID owner, Schedule input) {
        try { ZoneId.of(input.timezone()); } catch (DateTimeException e) { throw bad("Múi giờ không hợp lệ."); }
        for (Rule r : input.rules()) if(r.startMinute() >= r.endMinute()) throw bad("Giờ kết thúc phải sau giờ bắt đầu.");
        for (int i=0;i<input.rules().size();i++) for(int j=i+1;j<input.rules().size();j++) {
            Rule a=input.rules().get(i), b=input.rules().get(j);
            if(a.dayOfWeek()==b.dayOfWeek() && a.startMinute()<b.endMinute() && b.startMinute()<a.endMinute()) throw bad("Các khung giờ trong cùng ngày không được chồng lấn.");
        }
        Set<LocalDate> dates=new HashSet<>();
        for(ExceptionDay e:input.exceptions()) {
            if(!dates.add(e.date())) throw bad("Ngày ngoại lệ bị trùng.");
            if(!e.closed() && (e.startMinute()==null || e.endMinute()==null || e.startMinute()>=e.endMinute())) throw bad("Giờ ngoại lệ không hợp lệ.");
        }
        jdbc.queryForObject("select id from accounts where id=? for update",UUID.class,owner);
        jdbc.update("update accounts set booking_timezone=? where id=?",input.timezone(),owner);
        jdbc.update("delete from availability_rules where owner_id=?",owner);
        jdbc.update("delete from availability_exceptions where owner_id=?",owner);
        for(Rule r:input.rules()) jdbc.update("insert into availability_rules values (?,?,?,?,?)",UUID.randomUUID(),owner,r.dayOfWeek(),r.startMinute(),r.endMinute());
        for(ExceptionDay e:input.exceptions()) jdbc.update("insert into availability_exceptions values (?,?,?,?,?,?)",UUID.randomUUID(),owner,e.date(),e.closed(),e.closed()?null:e.startMinute(),e.closed()?null:e.endMinute());
        return schedule(owner);
    }
    public Slots slots(String username, UUID serviceId, LocalDate date) {
        Owner owner=owner(username,false);
        return new Slots(owner.timezone(), available(owner,serviceId,date));
    }
    private List<Slot> available(Owner owner, UUID serviceId, LocalDate date) {
        ZoneId zone=ZoneId.of(owner.timezone());
        LocalDate today=LocalDate.now(clock.withZone(zone));
        if(date.isBefore(today) || date.isAfter(today.plusDays(90))) throw bad("Chỉ đặt lịch trong 90 ngày tới.");
        Integer duration = jdbc.query("select duration_minutes from service_offerings where id=? and owner_id=?",
                (r, n) -> r.getInt(1), serviceId, owner.id()).stream().findFirst().orElseThrow(BookingService::missing);
        Schedule schedule=schedule(owner.id());
        List<Rule> windows=new ArrayList<>(schedule.rules().stream().filter(r->r.dayOfWeek()==date.getDayOfWeek().getValue()).toList());
        schedule.exceptions().stream().filter(e->e.date().equals(date)).findFirst().ifPresent(e->{windows.clear();if(!e.closed())windows.add(new Rule(date.getDayOfWeek().getValue(),e.startMinute(),e.endMinute()));});
        Instant dayStart=date.atStartOfDay(zone).toInstant(), dayEnd=date.plusDays(1).atStartOfDay(zone).toInstant();
        List<Slot> busy=jdbc.query("select starts_at,ends_at from bookings where owner_id=? and status in ('PENDING','CONFIRMED') and starts_at<? and ends_at>?",
                (r,n)->new Slot(r.getTimestamp(1).toInstant(),r.getTimestamp(2).toInstant()),owner.id(),Timestamp.from(dayEnd),Timestamp.from(dayStart));
        TreeMap<Instant,Slot> result=new TreeMap<>();
        Instant now=clock.instant();
        for(Rule w:windows) {
            LocalDateTime from=date.atStartOfDay().plusMinutes(w.startMinute()), to=date.atStartOfDay().plusMinutes(w.endMinute());
            // Skip nonexistent local starts; emit both offsets during a fall-back overlap.
            for(LocalDateTime local=from;local.isBefore(to);local=local.plusMinutes(15)) for(ZoneOffset offset:zone.getRules().getValidOffsets(local)) {
                Instant start=local.toInstant(offset), end=start.plusSeconds(duration*60L);
                if(!start.isAfter(now) || end.isAfter(dayEnd)) continue;
                // Every elapsed minute must remain inside this local availability window across DST.
                boolean inside=true;
                for(Instant cursor=start;cursor.isBefore(end);cursor=cursor.plusSeconds(60)) {
                    LocalDateTime clock=LocalDateTime.ofInstant(cursor,zone);
                    if(clock.isBefore(from)||!clock.isBefore(to)){inside=false;break;}
                }
                if(inside && busy.stream().noneMatch(b->start.isBefore(b.endsAt())&&b.startsAt().isBefore(end))) result.put(start,new Slot(start,end));
            }
        }
        return List.copyOf(result.values());
    }
    @Transactional public Receipt create(String username, Create input) {
        Owner owner=owner(username,true); // Shared lock keeps schedule/service edits stable; concurrent bookings still compete at the exclusion constraint.
        Slot slot=available(owner,input.serviceId(),input.startsAt().atZone(ZoneId.of(owner.timezone())).toLocalDate()).stream()
                .filter(s->s.startsAt().equals(input.startsAt())).findFirst().orElseThrow(()->conflict("Khung giờ không còn trống hoặc ngoài lịch rảnh. Vui lòng chọn lại."));
        String title=jdbc.queryForObject("select title from service_offerings where id=? and owner_id=?",String.class,input.serviceId(),owner.id());
        UUID id=UUID.randomUUID();
        // Do not catch integrity errors here: the entire transaction must roll back.
        jdbc.update("insert into bookings (id,owner_id,service_id,service_title,customer_name,customer_email,message,starts_at,ends_at,timezone,status) values (?,?,?,?,?,?,?,?,?,?,?)",
                id,owner.id(),input.serviceId(),title,input.name().strip(),input.email().strip().toLowerCase(Locale.ROOT),input.message().strip(),Timestamp.from(slot.startsAt()),Timestamp.from(slot.endsAt()),owner.timezone(),"PENDING");
        notifyBooking(owner.id(),id,"PENDING");
        return new Receipt(id,Status.PENDING,slot.startsAt(),slot.endsAt(),owner.timezone());
    }
    public List<Booking> bookings(UUID owner, int page) {
        return jdbc.query("select * from bookings where owner_id=? order by starts_at desc,id limit 20 offset ?",(r,n)->new Booking(r.getObject("id",UUID.class),r.getString("service_title"),r.getString("customer_name"),r.getString("customer_email"),r.getString("message"),r.getTimestamp("starts_at").toInstant(),r.getTimestamp("ends_at").toInstant(),r.getString("timezone"),Status.valueOf(r.getString("status"))),owner,page*20);
    }
    @Transactional public void change(UUID owner, UUID id, Status next) {
        var current=jdbc.query("select status,ends_at from bookings where id=? and owner_id=? for update",(r,n)->Map.entry(Status.valueOf(r.getString(1)),r.getTimestamp(2).toInstant()),id,owner).stream().findFirst().orElseThrow(BookingService::missing);
        if(current.getKey()==next)return;
        boolean allowed=(current.getKey()==Status.PENDING && (next==Status.CONFIRMED||next==Status.CANCELLED)) || (current.getKey()==Status.CONFIRMED && (next==Status.CANCELLED||next==Status.COMPLETED));
        if(!allowed)throw conflict("Không thể chuyển trạng thái lịch hẹn này.");
        if(next==Status.COMPLETED && current.getValue().isAfter(clock.instant()))throw bad("Chỉ hoàn tất sau khi lịch hẹn kết thúc.");
        jdbc.update("update bookings set status=? where id=? and owner_id=?",next.name(),id,owner);
        notifyBooking(owner,id,next.name());
    }
    private void notifyBooking(UUID owner,UUID id,String state) {
        var booking=jdbc.queryForMap("select service_title,customer_name,customer_email,starts_at,ends_at,timezone from bookings where id=? and owner_id=?",id,owner);
        String label=switch(state){case "PENDING"->"Chờ xác nhận";case "CONFIRMED"->"Đã xác nhận";case "CANCELLED"->"Đã hủy";default->"Hoàn tất";};
        String title=state.equals("PENDING")?"Có lịch hẹn mới":"Lịch hẹn: "+label;
        var times=jdbc.query("select starts_at,ends_at from bookings where id=?",(r,n)->List.of(r.getTimestamp(1).toInstant(),r.getTimestamp(2).toInstant()),id).getFirst();
        var formatter=java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm XXX").withZone(ZoneId.of((String)booking.get("timezone")));
        String body=booking.get("service_title")+"\n"+formatter.format(times.get(0))+" → "+formatter.format(times.get(1))+" ("+booking.get("timezone")+")\nTrạng thái: "+label+"\nKhách: "+booking.get("customer_name")+"\nMã lịch: "+id;
        notifications.record(owner,"booking:"+id+":"+state,"BOOKING_"+state,title,body,"bookings",List.of(
                new NotificationService.Mail(notifications.ownerEmail(owner),title,body+"\n\nMở dashboard LinkHub để quản lý lịch hẹn."),
                new NotificationService.Mail((String)booking.get("customer_email"),"LinkHub · Lịch hẹn "+label.toLowerCase(Locale.ROOT),body)));
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy profile, dịch vụ hoặc lịch hẹn.");}
    private static ResponseStatusException bad(String m){return new ResponseStatusException(HttpStatus.BAD_REQUEST,m);}
    private static ResponseStatusException conflict(String m){return new ResponseStatusException(HttpStatus.CONFLICT,m);}
}
