package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.LoggerFactory;

@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="linkhub.reminders.enabled",havingValue="true",matchIfMissing=true)
public class BookingReminderWorker {
    private final BookingReminderService reminders;
    @Scheduled(fixedDelayString="${linkhub.reminders.poll-ms:60000}",initialDelayString="${linkhub.reminders.poll-ms:60000}")
    public void tick() {
        for(var id:reminders.dueIds()) {
            try {reminders.create(id);}
            catch(RuntimeException e){LoggerFactory.getLogger(getClass()).warn("Reminder generation failed for booking {} ({})",id,e.getClass().getSimpleName());}
        }
    }
}
