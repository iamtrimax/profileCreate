package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class NotificationService {
    private final JdbcTemplate jdbc;
    public record Item(UUID id,String kind,String title,String body,String target,Instant createdAt,Instant readAt) {}
    public record Inbox(List<Item> items,long total,long unread,int page) {}
    public record Mail(String recipient,String subject,String body) {}

    @Transactional(propagation=Propagation.MANDATORY)
    public void record(UUID owner,String eventKey,String kind,String title,String body,String target,List<Mail> emails) {
        UUID id=UUID.randomUUID();
        int inserted=jdbc.update("insert into notifications(id,owner_id,event_key,kind,title,body,target) values (?,?,?,?,?,?,?) on conflict do nothing",
                id,owner,eventKey,kind,title,body.length()>4000?body.substring(0,4000):body,target);
        if(inserted==0)return;
        Set<String> recipients=new HashSet<>();
        for(Mail mail:emails) {
            String recipient=mail.recipient().strip().toLowerCase(Locale.ROOT);
            if(!recipients.add(recipient))continue;
            jdbc.update("insert into mail_outbox(id,notification_id,recipient,subject,body) values (?,?,?,?,?)",
                    UUID.randomUUID(),id,recipient,mail.subject(),mail.body());
        }
    }
    public String ownerEmail(UUID owner) {return jdbc.queryForObject("select email from accounts where id=?",String.class,owner);}
    @Transactional(readOnly=true)
    public Inbox inbox(UUID owner,int page,boolean unreadOnly) {
        String filter=unreadOnly?" and read_at is null":"";
        List<Item> items=jdbc.query("select * from notifications where owner_id=?"+filter+" order by created_at desc,id desc limit 20 offset ?",(r,n)->new Item(
                r.getObject("id",UUID.class),r.getString("kind"),r.getString("title"),r.getString("body"),r.getString("target"),r.getTimestamp("created_at").toInstant(),
                r.getTimestamp("read_at")==null?null:r.getTimestamp("read_at").toInstant()),owner,page*20);
        long total=jdbc.queryForObject("select count(*) from notifications where owner_id=?"+filter,Long.class,owner);
        long unread=jdbc.queryForObject("select count(*) from notifications where owner_id=? and read_at is null",Long.class,owner);
        return new Inbox(items,total,unread,page);
    }
    @Transactional public void read(UUID owner,UUID id) {
        if(jdbc.update("update notifications set read_at=coalesce(read_at,current_timestamp) where owner_id=? and id=?",owner,id)==0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy thông báo.");
    }
    @Transactional public void readAll(UUID owner) {jdbc.update("update notifications set read_at=current_timestamp where owner_id=? and read_at is null",owner);}
}
