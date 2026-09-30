package com.trimax.linkhub.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.scheduling.annotation.Scheduled;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;private final Clock clock;private final MeterRegistry metrics;private final int retentionDays;
    public AuditService(JdbcTemplate jdbc,Clock clock,MeterRegistry metrics,@Value("${linkhub.audit.retention-days:180}") int retentionDays){
        if(retentionDays<1||retentionDays>3650)throw new IllegalArgumentException("Audit retention must be between 1 and 3650 days");
        this.jdbc=jdbc;this.clock=clock;this.metrics=metrics;this.retentionDays=retentionDays;
        metrics.counter("linkhub.audit.write.failures");
    }
    public enum Outcome {SUCCESS,DENIED,FAILURE}
    public record Item(long id,Instant createdAt,String actorType,UUID actorId,String action,String resourceType,String resourceId,Outcome outcome,int httpStatus,String requestId,long durationMs){}
    public record Summary(long total,long success,long denied,long failure){}
    public record Page(List<Item> items,Long nextCursor,Summary summary){}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void record(UUID actor,String action,String resource,String resourceId,int status,String requestId,long duration){
        Outcome outcome=status<400?Outcome.SUCCESS:status==401||status==403||status==429?Outcome.DENIED:Outcome.FAILURE;
        jdbc.update("insert into audit_logs(created_at,actor_type,actor_id,action,resource_type,resource_id,outcome,http_status,request_id,duration_ms) values (?,?,?,?,?,?,?,?,?,?)",
            Timestamp.from(clock.instant()),actor==null?"ANONYMOUS":"USER",actor,action,resource,resourceId,outcome.name(),status,requestId,Math.max(0,duration));
    }
    public void unavailable(String requestId){
        metrics.counter("linkhub.audit.write.failures").increment();
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Audit record unavailable; requestId={}",requestId);
    }
    @Transactional(readOnly=true)
    public Page find(Instant from,Instant to,String action,UUID actor,Outcome outcome,String requestId,Long before,int size){
        StringBuilder where=new StringBuilder(" where created_at>=? and created_at<=?");List<Object> args=new ArrayList<>(List.of(Timestamp.from(from),Timestamp.from(to)));
        if(action!=null&&!action.isBlank()){where.append(" and action=?");args.add(action);}
        if(actor!=null){where.append(" and actor_id=?");args.add(actor);}
        if(outcome!=null){where.append(" and outcome=?");args.add(outcome.name());}
        if(requestId!=null&&!requestId.isBlank()){where.append(" and request_id=?");args.add(requestId);}
        Map<String,Long> counts=new HashMap<>();jdbc.query("select outcome,count(*) from audit_logs"+where+" group by outcome",r->{counts.put(r.getString(1),r.getLong(2));},args.toArray());
        long success=counts.getOrDefault("SUCCESS",0L),denied=counts.getOrDefault("DENIED",0L),failure=counts.getOrDefault("FAILURE",0L);
        if(before!=null){where.append(" and id<?");args.add(before);}
        args.add(size+1);
        var rows=jdbc.query("select * from audit_logs"+where+" order by id desc limit ?",(r,n)->new Item(r.getLong("id"),r.getTimestamp("created_at").toInstant(),r.getString("actor_type"),r.getObject("actor_id",UUID.class),r.getString("action"),r.getString("resource_type"),r.getString("resource_id"),Outcome.valueOf(r.getString("outcome")),r.getInt("http_status"),r.getString("request_id"),r.getLong("duration_ms")),args.toArray());
        boolean more=rows.size()>size;var items=List.copyOf(rows.subList(0,Math.min(rows.size(),size)));
        return new Page(items,more?items.getLast().id():null,new Summary(success+denied+failure,success,denied,failure));
    }
    @Scheduled(fixedDelay=3600000,initialDelay=300000)
    public void purge(){jdbc.update("delete from audit_logs where id in (select id from audit_logs where created_at<? order by id limit 5000)",Timestamp.from(clock.instant().minus(Duration.ofDays(retentionDays))));}
}
