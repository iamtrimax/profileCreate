package com.trimax.linkhub;

import com.trimax.linkhub.service.*;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailDeliveryTests {
    @Test void workerDoesNotSendObsoleteReminder() throws Exception {
        MailOutboxStore store=mock(MailOutboxStore.class);OutboxMailer mailer=mock(OutboxMailer.class);
        var job=new MailOutboxStore.Job(UUID.randomUUID(),UUID.randomUUID(),"guest@example.com","Reminder","Body",1);
        when(store.claim()).thenReturn(Optional.of(job),Optional.empty());when(store.eligible(job)).thenReturn(false);
        new MailOutboxWorker(store,mailer).deliver();verifyNoInteractions(mailer);verify(store,never()).sent(any());
    }
    @Test void sendsUtf8PlainTextAndStableMessageIdWithoutExternalSmtp() throws Exception {
        JavaMailSender sender=mock(JavaMailSender.class);
        MimeMessage message=new MimeMessage(Session.getInstance(new Properties()));when(sender.createMimeMessage()).thenReturn(message);
        var job=new MailOutboxStore.Job(UUID.randomUUID(),UUID.randomUUID(),"guest@example.com","Xác nhận lịch","Nội dung <script> không phải HTML",1);
        new OutboxMailer(sender,"no-reply@example.com",new SecurityMailCipher("")).send(job);
        verify(sender).send(message);
        assertEquals(job.subject(),message.getSubject());assertEquals(job.body(),message.getContent());
        assertTrue(message.isMimeType("text/plain"));assertEquals("<"+job.id()+"@linkhub.local>",message.getMessageID());
    }
    @Test void workerAcknowledgesOnlySuccessfulSendAndRetriesFailures() throws Exception {
        MailOutboxStore store=mock(MailOutboxStore.class);OutboxMailer mailer=mock(OutboxMailer.class);
        var job=new MailOutboxStore.Job(UUID.randomUUID(),UUID.randomUUID(),"guest@example.com","Subject","Body",1);
        when(store.claim()).thenReturn(Optional.of(job),Optional.empty());
        when(store.eligible(job)).thenReturn(true);
        new MailOutboxWorker(store,mailer).deliver();
        var order=inOrder(store,mailer);order.verify(store).claim();order.verify(mailer).send(job);order.verify(store).sent(job);
        reset(store,mailer);when(store.claim()).thenReturn(Optional.of(job),Optional.empty());
        when(store.eligible(job)).thenReturn(true);
        doThrow(new jakarta.mail.MessagingException("SMTP offline")).when(mailer).send(job);
        new MailOutboxWorker(store,mailer).deliver();verify(store,never()).sent(any());verify(store).failed(eq(job),any());
    }
}
