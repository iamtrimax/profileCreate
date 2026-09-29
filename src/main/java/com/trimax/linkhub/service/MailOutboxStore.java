package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class MailOutboxStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record Job(UUID id,UUID token,String recipient,String subject,String body,int attempts,boolean encrypted) {
        public Job(UUID id,UUID token,String recipient,String subject,String body,int attempts){this(id,token,recipient,subject,body,attempts,false);}
        @Override public String toString(){return "MailJob["+id+"]";}
    }
    @Transactional public Optional<Job> claim() {
        Timestamp now=Timestamp.from(clock.instant());
        jdbc.update("update mail_outbox set status='FAILED',lease_token=null,lease_until=null,last_error='Retry limit reached after expired lease' where status='SENDING' and lease_until<=? and attempts>=8",now);
        var ids=jdbc.query("select id from mail_outbox where attempts<8 and ((status='PENDING' and next_attempt_at<=?) or (status='SENDING' and lease_until<=?)) order by created_at,id limit 20",(r,n)->r.getObject(1,UUID.class),now,now);
        for(UUID id:ids) {
            UUID token=UUID.randomUUID();
            int updated=jdbc.update("update mail_outbox set status='SENDING',attempts=attempts+1,lease_token=?,lease_until=? where id=? and attempts<8 and ((status='PENDING' and next_attempt_at<=?) or (status='SENDING' and lease_until<=?))",
                    token,Timestamp.from(clock.instant().plusSeconds(120)),id,now,now);
            if(updated==1)return jdbc.query("select recipient,subject,body,attempts,encrypted from mail_outbox where id=?",(r,n)->new Job(id,token,r.getString(1),r.getString(2),r.getString(3),r.getInt(4),r.getBoolean(5)),id).stream().findFirst();
        }
        return Optional.empty();
    }
    @Transactional public void sent(Job job) {
        jdbc.update("update mail_outbox set status='SENT',sent_at=?,lease_until=null,lease_token=null,last_error=null where id=? and status='SENDING' and lease_token=?",Timestamp.from(clock.instant()),job.id(),job.token());
    }
    @Transactional public boolean eligible(Job job) {
        if(jdbc.update("update mail_outbox set status='FAILED',last_error='EMAIL_EXPIRED',lease_token=null,lease_until=null where id=? and lease_token=? and expires_at<=?",job.id(),job.token(),Timestamp.from(clock.instant()))>0)return false;
        var obsolete=jdbc.query("""
            select b.status,r.expires_at from mail_outbox m
            join booking_reminders r on r.notification_id=m.notification_id
            join bookings b on b.id=r.booking_id where m.id=?
            """,(r,n)->!r.getString(1).equals("CONFIRMED")||!r.getTimestamp(2).toInstant().isAfter(clock.instant()),job.id());
        if(!obsolete.isEmpty() && obsolete.getFirst()) {
            jdbc.update("update mail_outbox set status='FAILED',last_error='REMINDER_CANCELLED_OR_EXPIRED',lease_token=null,lease_until=null where id=? and status='SENDING' and lease_token=?",job.id(),job.token());
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("select count(*)=1 from mail_outbox where id=? and status='SENDING' and lease_token=? and lease_until>?",Boolean.class,job.id(),job.token(),Timestamp.from(clock.instant())));
    }
    @Transactional public void failed(Job job,Exception error) {
        long delay=Math.min(3600,30L << Math.min(job.attempts()-1,7));
        // Do not persist SMTP responses, which can contain credentials or customer data.
        jdbc.update("update mail_outbox set status=?,next_attempt_at=?,lease_until=null,lease_token=null,last_error=? where id=? and status='SENDING' and lease_token=?",
                job.attempts()>=8?"FAILED":"PENDING",Timestamp.from(clock.instant().plusSeconds(delay)),error.getClass().getSimpleName(),job.id(),job.token());
    }
}
