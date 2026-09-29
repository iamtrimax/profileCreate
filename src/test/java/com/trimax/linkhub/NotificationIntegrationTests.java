package com.trimax.linkhub;

import com.trimax.linkhub.api.Requests;
import com.trimax.linkhub.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class NotificationIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Autowired NotificationStream streams;
    @Autowired LinkHubService links;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    @MockitoBean RateLimiter limiter;
    UUID owner;String username;MockHttpSession session;
    @BeforeEach void account() {
        when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);
        owner=UUID.randomUUID();username="n"+owner.toString().replace("-","").substring(0,20);
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,'test',?,'Notification test',true,current_timestamp)",owner,username+"@example.com",username);
        session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner.toString(),null,List.of()));session.setAttribute("SPRING_SECURITY_CONTEXT",context);
    }
    private UUID reminderBooking(Instant start,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into bookings(id,owner_id,service_title,customer_name,customer_email,message,starts_at,ends_at,timezone,status) values (?,?,'Tư vấn','Guest','guest@example.com','',?,?,'Asia/Ho_Chi_Minh',?)",id,owner,java.sql.Timestamp.from(start),java.sql.Timestamp.from(start.plusSeconds(1800)),status);
        return id;
    }
    @Test void remindersUseTwoWindowsDeduplicateAndSkipPendingPastAndCancelled() {
        Instant now=Instant.parse("2030-02-01T00:00:00Z");
        var service=new BookingReminderService(jdbc,Clock.fixed(now,ZoneOffset.UTC),notifications);
        UUID confirmed=reminderBooking(now.plusSeconds(86400),"CONFIRMED");
        UUID pending=reminderBooking(now.plusSeconds(1800),"PENDING");
        UUID cancelled=reminderBooking(now.plusSeconds(3600),"CANCELLED");
        UUID past=reminderBooking(now.minusSeconds(7200),"CONFIRMED");
        assertTrue(service.dueIds().contains(confirmed));assertFalse(service.dueIds().contains(pending));assertFalse(service.dueIds().contains(cancelled));assertFalse(service.dueIds().contains(past));
        var tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(s->{service.create(confirmed);service.create(confirmed);service.create(pending);service.create(cancelled);service.create(past);});
        assertEquals(1,jdbc.queryForObject("select count(*) from booking_reminders where booking_id=?",Integer.class,confirmed));
        assertEquals("guest@example.com",jdbc.queryForObject("select m.recipient from mail_outbox m join booking_reminders r on r.notification_id=m.notification_id where r.booking_id=?",String.class,confirmed));
        assertFalse(service.dueIds().contains(confirmed));
        var hour=new BookingReminderService(jdbc,Clock.fixed(now.plusSeconds(82800),ZoneOffset.UTC),notifications);
        assertTrue(hour.dueIds().contains(confirmed));
        tx.executeWithoutResult(s->{hour.create(confirmed);hour.create(confirmed);});
        assertEquals(2,jdbc.queryForObject("select count(*) from booking_reminders where booking_id=?",Integer.class,confirmed));
        assertEquals(1,service.upcoming(owner).pending());assertEquals(1,service.upcoming(owner).total());
    }
    @Test void reminderEmailsAreSuppressedAfterCancellationOrExpiry() {
        Instant now=Instant.parse("2030-03-01T00:00:00Z");
        var service=new BookingReminderService(jdbc,Clock.fixed(now,ZoneOffset.UTC),notifications);
        UUID booking=reminderBooking(now.plusSeconds(1800),"CONFIRMED");
        new TransactionTemplate(transactions).executeWithoutResult(s->service.create(booking));
        UUID mail=jdbc.queryForObject("select m.id from mail_outbox m join booking_reminders r on r.notification_id=m.notification_id where r.booking_id=?",UUID.class,booking),token=UUID.randomUUID();
        jdbc.update("update mail_outbox set status='SENDING',lease_token=?,lease_until=? where id=?",token,java.sql.Timestamp.from(now.plusSeconds(120)),mail);
        var job=new MailOutboxStore.Job(mail,token,"guest@example.com","Reminder","Body",1);
        var store=new MailOutboxStore(jdbc,Clock.fixed(now,ZoneOffset.UTC));assertTrue(store.eligible(job));
        var expired=new MailOutboxStore(jdbc,Clock.fixed(now.plusSeconds(1800),ZoneOffset.UTC));assertFalse(expired.eligible(job));
        assertEquals("REMINDER_CANCELLED_OR_EXPIRED",jdbc.queryForObject("select last_error from mail_outbox where id=?",String.class,mail));
        jdbc.update("update mail_outbox set status='SENDING',lease_token=?,lease_until=? where id=?",token,java.sql.Timestamp.from(now.plusSeconds(120)),mail);
        jdbc.update("update bookings set status='CANCELLED' where id=?",booking);
        assertFalse(store.eligible(job));assertTrue(service.upcoming(owner).items().isEmpty());
    }
    @Test void upcomingEndpointRequiresSessionAndOnlyReturnsOwnersBookings() throws Exception {
        reminderBooking(Instant.now().plusSeconds(1800),"CONFIRMED");
        mvc.perform(get("/api/me/bookings/upcoming")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/bookings/upcoming").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].name").value("Guest"));
        UUID otherId=UUID.randomUUID();jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,'test',?,'Other',true,current_timestamp)",otherId,otherId+"@example.com","o"+otherId.toString().replace("-","").substring(0,20));
        var other=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(otherId.toString(),null,List.of()));other.setAttribute("SPRING_SECURITY_CONTEXT",context);
        mvc.perform(get("/api/me/bookings/upcoming").session(other)).andExpect(jsonPath("$.total").value(0));
    }
    private void record(String key) {
        new TransactionTemplate(transactions).executeWithoutResult(s->notifications.record(owner,key,"CONTACT_NEW","New contact","Message","inbox",List.of(new NotificationService.Mail(username+"@example.com","New contact","Message"))));
    }
    @Test void contactAndOutboxRollbackTogetherAndCommitTogether() {
        new TransactionTemplate(transactions).executeWithoutResult(s->{links.contact(username,new Requests.Contact("Guest","guest@example.com","Rollback"));s.setRollbackOnly();});
        assertEquals(0,jdbc.queryForObject("select count(*) from notifications where owner_id=?",Integer.class,owner));
        assertEquals(0,jdbc.queryForObject("select count(*) from contact_requests where owner_id=?",Integer.class,owner));
        links.contact(username,new Requests.Contact("Guest","guest@example.com","x".repeat(4000)));
        assertEquals(1,jdbc.queryForObject("select count(*) from notifications where owner_id=?",Integer.class,owner));
        assertEquals(1,jdbc.queryForObject("select count(*) from mail_outbox m join notifications n on m.notification_id=n.id where n.owner_id=?",Integer.class,owner));
        assertEquals(1,jdbc.queryForObject("select count(*) from contact_requests where owner_id=?",Integer.class,owner));
    }
    @Test void eventDeduplicationOwnershipReadAndUnreadRecovery() throws Exception {
        String key="test:"+owner;record(key);record(key);
        var inbox=notifications.inbox(owner,0,false);assertEquals(1,inbox.total());assertEquals(1,inbox.unread());
        UUID id=inbox.items().getFirst().id();
        assertEquals(1,jdbc.queryForObject("select count(*) from mail_outbox where notification_id=?",Integer.class,id));
        mvc.perform(get("/api/me/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/notifications").session(session)).andExpect(jsonPath("$.unread").value(1));
        mvc.perform(patch("/api/me/notifications/"+id+"/read").session(session)).andExpect(status().isForbidden());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->notifications.read(UUID.randomUUID(),id));
        assertEquals(0,notifications.inbox(UUID.randomUUID(),0,false).total());
        mvc.perform(patch("/api/me/notifications/"+id+"/read").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications?unreadOnly=true").session(session)).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/me/notifications").session(session)).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.unread").value(0));
    }
    @Test void sseSnapshotReconnectAndSessionInvalidation() throws Exception {
        record("sse:"+owner);
        mvc.perform(get("/api/me/notifications/stream")).andExpect(status().isUnauthorized());
        var result=mvc.perform(get("/api/me/notifications/stream").session(session)).andExpect(request().asyncStarted()).andReturn();
        streams.pulse();
        String output=result.getResponse().getContentAsString();assertTrue(output.contains("event:notifications"));assertTrue(output.contains("New contact"));
        var reconnect=mvc.perform(get("/api/me/notifications/stream").session(session)).andExpect(request().asyncStarted()).andReturn();streams.pulse();
        assertTrue(reconnect.getResponse().getContentAsString().contains("New contact"));
        session.invalidate();streams.pulse();
        assertNull(result.getAsyncResult(1000));assertNull(reconnect.getAsyncResult(1000));
    }
    @Test void leaseRecoveryBackoffAndStaleAcknowledgement() {
        record("lease:"+owner);
        UUID id=jdbc.queryForObject("select m.id from mail_outbox m join notifications n on n.id=m.notification_id where n.owner_id=?",UUID.class,owner);
        // Restrict this worker fixture to its own mail by using future time for other queued fixtures.
        Instant now=Instant.now().plusSeconds(1);
        jdbc.update("update mail_outbox set next_attempt_at=? where id<>? and status='PENDING'",java.sql.Timestamp.from(now.plusSeconds(10000)),id);
        var firstStore=new MailOutboxStore(jdbc,Clock.fixed(now,ZoneOffset.UTC));
        var first=firstStore.claim().orElseThrow();assertEquals(id,first.id());assertTrue(firstStore.claim().isEmpty());
        firstStore.failed(first,new IllegalStateException("secret SMTP message"));
        assertTrue(firstStore.claim().isEmpty());
        assertEquals("IllegalStateException",jdbc.queryForObject("select last_error from mail_outbox where id=?",String.class,id));
        var secondStore=new MailOutboxStore(jdbc,Clock.fixed(now.plusSeconds(31),ZoneOffset.UTC));var second=secondStore.claim().orElseThrow();assertEquals(2,second.attempts());
        var recoveredStore=new MailOutboxStore(jdbc,Clock.fixed(now.plusSeconds(152),ZoneOffset.UTC));var recovered=recoveredStore.claim().orElseThrow();
        secondStore.sent(second);assertEquals("SENDING",jdbc.queryForObject("select status from mail_outbox where id=?",String.class,id));
        recoveredStore.sent(recovered);assertEquals("SENT",jdbc.queryForObject("select status from mail_outbox where id=?",String.class,id));
        jdbc.update("update mail_outbox set status='PENDING',attempts=7,next_attempt_at=? where id=?",java.sql.Timestamp.from(now),id);
        var finalAttempt=recoveredStore.claim().orElseThrow();assertEquals(8,finalAttempt.attempts());
        recoveredStore.failed(finalAttempt,new IllegalStateException());
        assertEquals("FAILED",jdbc.queryForObject("select status from mail_outbox where id=?",String.class,id));
        assertTrue(recoveredStore.claim().isEmpty());
    }
}
