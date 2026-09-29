package com.trimax.linkhub.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OperationsMetrics {
    private final JdbcTemplate jdbc;private final Map<String,AtomicLong> counts=new HashMap<>();
    public OperationsMetrics(JdbcTemplate jdbc,MeterRegistry registry){this.jdbc=jdbc;for(String status:List.of("PENDING","SENDING","SENT","FAILED")){var value=new AtomicLong();counts.put(status,value);registry.gauge("linkhub.mail.jobs",io.micrometer.core.instrument.Tags.of("status",status),value);}}
    @Scheduled(fixedDelay=30000,initialDelay=10000) public void refresh(){Map<String,Long> snapshot=new HashMap<>();jdbc.query("select status,count(*) from mail_outbox group by status",r->{snapshot.put(r.getString(1),r.getLong(2));});counts.forEach((status,value)->value.set(snapshot.getOrDefault(status,0L)));}
}
