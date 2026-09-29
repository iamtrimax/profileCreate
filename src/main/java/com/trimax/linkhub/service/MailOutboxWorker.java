package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="linkhub.mail.enabled",havingValue="true")
public class MailOutboxWorker {
    private final MailOutboxStore store;
    private final OutboxMailer mailer;
    @Scheduled(fixedDelayString="${linkhub.mail.poll-ms:5000}")
    public void deliver() {
        for(int i=0;i<10;i++) {
            var next=store.claim();if(next.isEmpty())return;
            var job=next.get();
            try {if(store.eligible(job)){mailer.send(job);store.sent(job);}}
            catch(Exception e){store.failed(job,e);}
        }
    }
}
