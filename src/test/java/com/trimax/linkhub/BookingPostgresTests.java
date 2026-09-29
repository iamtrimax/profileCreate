package com.trimax.linkhub;

import com.trimax.linkhub.api.BookingModels.*;
import com.trimax.linkhub.service.BookingService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses an isolated schema on real PostgreSQL. Never substitutes H2 for exclusion tests. */
@org.junit.jupiter.api.condition.EnabledIf("enabled")
class BookingPostgresTests {
    static boolean enabled(){return Boolean.getBoolean("containers")||System.getProperty("booking.pg.url")!=null;}
    static org.testcontainers.containers.GenericContainer<?> postgres;
    static DriverManagerDataSource admin, data;
    static JdbcTemplate jdbc;
    static String schema;
    static BookingService service;
    static TransactionTemplate tx;
    UUID owner, offering;
    String username;
    @BeforeAll static void database() {
        String url=System.getProperty("booking.pg.url");
        String user=System.getProperty("booking.pg.user","linkhub"), password=System.getProperty("booking.pg.password","linkhub_dev");
        if(Boolean.getBoolean("containers")){
            postgres=new org.testcontainers.containers.GenericContainer<>("postgres:17-alpine").withEnv("POSTGRES_USER",user).withEnv("POSTGRES_PASSWORD",password).withEnv("POSTGRES_DB","linkhub").withExposedPorts(5432).withTmpFs(Map.of("/var/lib/postgresql/data","rw")).withStartupTimeout(Duration.ofMinutes(3));
            postgres.start();url="jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/linkhub";
        }
        schema="booking_test_"+UUID.randomUUID().toString().replace("-", "");
        admin=new DriverManagerDataSource(url,user,password);
        new JdbcTemplate(admin).execute("create schema "+schema);
        data=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema+",public",user,password);
        Flyway.configure().dataSource(data).schemas(schema).defaultSchema(schema).load().migrate();
        jdbc=new JdbcTemplate(data);service=new BookingService(jdbc,Clock.systemUTC(),new com.trimax.linkhub.service.NotificationService(jdbc));tx=new TransactionTemplate(new DataSourceTransactionManager(data));
    }
    @AfterAll static void cleanup() {try{if(admin!=null && schema!=null)new JdbcTemplate(admin).execute("drop schema "+schema+" cascade");}finally{if(postgres!=null)postgres.stop();}}
    @BeforeEach void account() {
        owner=UUID.randomUUID();offering=UUID.randomUUID();username="b"+owner.toString().replace("-","").substring(0,20);
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,?,?,?,true,current_timestamp)",owner,username+"@example.com","test",username,"Booking test");
        jdbc.update("insert into service_offerings(id,owner_id,title,description,price,duration_minutes) values (?,?,?,'',0,60)",offering,owner,"Consultation");
    }
    private UUID insert(UUID who, Instant start, Instant end, String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into bookings(id,owner_id,service_title,customer_name,customer_email,message,starts_at,ends_at,timezone,status) values (?,?,'Test','Guest','guest@example.com','',?,?,'UTC',?)",id,who,Timestamp.from(start),Timestamp.from(end),status);
        return id;
    }
    @Test void exclusionBoundariesCancellationAndUpdate() {
        Instant start=Instant.parse("2030-01-01T02:00:00Z"),end=start.plusSeconds(3600);
        UUID first=insert(owner,start,end,"PENDING");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->insert(owner,start.plusSeconds(900),end.plusSeconds(900),"CONFIRMED"));
        UUID adjacent=insert(owner,end,end.plusSeconds(3600),"CONFIRMED");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update bookings set starts_at=? where id=?",Timestamp.from(start.plusSeconds(1800)),adjacent));
        jdbc.update("update bookings set status='CANCELLED' where id=?",first);
        insert(owner,start,end,"PENDING");
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->jdbc.update("update bookings set status='CONFIRMED' where id=?",first));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->insert(owner,end,end,"PENDING"));
        // A different provider is independent.
        UUID other=UUID.randomUUID();jdbc.update("insert into accounts(id,email,password_hash,username,display_name,created_at) values (?,?, 'test',?, 'Other',current_timestamp)",other,other+"@example.com","o"+other.toString().substring(0,20));
        insert(other,start,end,"PENDING");
    }
    @Test void simultaneousTransactionsBothSeeFreeButOnlyOneCommits() throws Exception {
        var barrier=new CyclicBarrier(2);
        Instant start=Instant.parse("2031-01-01T02:00:00Z");
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<String> compete=()->{
                try(Connection connection=data.getConnection()) {
                    connection.setAutoCommit(false);
                    try {
                        try(var check=connection.prepareStatement("select count(*) from bookings where owner_id=?")) {check.setObject(1,owner);try(var r=check.executeQuery()){r.next();assertEquals(0,r.getInt(1));}}
                        barrier.await(10,TimeUnit.SECONDS);
                        try(var statement=connection.prepareStatement("insert into bookings(id,owner_id,service_title,customer_name,customer_email,message,starts_at,ends_at,timezone,status) values (?,?,'Race','Guest','guest@example.com','',?,?,'UTC','PENDING')")) {
                            statement.setQueryTimeout(15);statement.setObject(1,UUID.randomUUID());statement.setObject(2,owner);statement.setTimestamp(3,Timestamp.from(start));statement.setTimestamp(4,Timestamp.from(start.plusSeconds(3600)));statement.executeUpdate();
                        }
                        connection.commit();return "COMMITTED";
                    }catch(SQLException e){connection.rollback();return e.getSQLState();}
                }
            };
            var a=workers.submit(compete);var b=workers.submit(compete);
            var outcomes=List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS));
            assertEquals(1,Collections.frequency(outcomes,"COMMITTED"));
            assertTrue(outcomes.stream().allMatch(s->Set.of("COMMITTED","23P01","40P01").contains(s)),outcomes.toString());
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from bookings where owner_id=?",Integer.class,owner));
    }
    @Test void availabilityDurationExceptionsAndStateMachine() {
        LocalDate date=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("Asia/Ho_Chi_Minh",List.of(new Rule(date.getDayOfWeek().getValue(),540,660)),List.of())));
        var slots=service.slots(username,offering,date);assertEquals(5,slots.slots().size());
        Instant start=slots.slots().getFirst().startsAt();
        Receipt receipt=tx.execute(s->service.create(username,new Create(offering,start,"Guest","guest@example.com","Hi")));
        assertEquals(Status.PENDING,receipt.status());
        assertEquals(1,service.slots(username,offering,date).slots().size());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.execute(s->service.create(username,new Create(offering,start,"Other","other@example.com",""))));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.executeWithoutResult(s->service.change(UUID.randomUUID(),receipt.id(),Status.CONFIRMED)));
        tx.executeWithoutResult(s->service.change(owner,receipt.id(),Status.CONFIRMED));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.executeWithoutResult(s->service.change(owner,receipt.id(),Status.COMPLETED)));
        tx.executeWithoutResult(s->service.change(owner,receipt.id(),Status.CANCELLED));
        assertEquals(5,service.slots(username,offering,date).slots().size());
        tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("Asia/Ho_Chi_Minh",List.of(),List.of(new ExceptionDay(date,false,600,720)))));
        assertEquals(5,service.slots(username,offering,date).slots().size());
        tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("Asia/Ho_Chi_Minh",List.of(new Rule(date.getDayOfWeek().getValue(),540,660)),List.of(new ExceptionDay(date,true,null,null)))));
        assertTrue(service.slots(username,offering,date).slots().isEmpty());
    }
    @Test void overlappingWeeklyRulesRejectedAndPastDateRejected() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("UTC",List.of(new Rule(1,540,660),new Rule(1,600,720)),List.of()))));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.slots(username,offering,LocalDate.now().minusDays(2)));
    }
    @Test void dstSkipsMissingHourAndKeepsRepeatedHourDistinct() {
        tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("America/New_York",List.of(new Rule(7,60,240)),List.of())));
        var spring=new BookingService(jdbc,Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"),ZoneOffset.UTC),new com.trimax.linkhub.service.NotificationService(jdbc));
        var springSlots=spring.slots(username,offering,LocalDate.of(2026,3,8)).slots();
        assertEquals(5,springSlots.size());
        assertTrue(springSlots.stream().noneMatch(s->s.startsAt().atZone(ZoneId.of("America/New_York")).getHour()==2));
        assertTrue(springSlots.stream().allMatch(s->Duration.between(s.startsAt(),s.endsAt()).toMinutes()==60));
        tx.executeWithoutResult(s->service.saveSchedule(owner,new Schedule("America/New_York",List.of(new Rule(7,60,180)),List.of())));
        var fall=new BookingService(jdbc,Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"),ZoneOffset.UTC),new com.trimax.linkhub.service.NotificationService(jdbc));
        var fallSlots=fall.slots(username,offering,LocalDate.of(2026,11,1)).slots();
        assertEquals(9,fallSlots.size());
        assertEquals(9,fallSlots.stream().map(Slot::startsAt).distinct().count());
        assertEquals(8,fallSlots.stream().filter(s->s.startsAt().atZone(ZoneId.of("America/New_York")).getHour()==1).count());
    }
    @Test void concurrentReminderWorkersEnqueueOnlyOneCustomerMail() throws Exception {
        Instant now=Instant.parse("2032-01-01T00:00:00Z");UUID booking=insert(owner,now.plusSeconds(3600),now.plusSeconds(7200),"CONFIRMED");
        var reminders=new com.trimax.linkhub.service.BookingReminderService(jdbc,Clock.fixed(now,ZoneOffset.UTC),new com.trimax.linkhub.service.NotificationService(jdbc));
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> run=()->{barrier.await(10,TimeUnit.SECONDS);tx.executeWithoutResult(s->reminders.create(booking));return true;};
            var a=workers.submit(run);var b=workers.submit(run);assertTrue(a.get(20,TimeUnit.SECONDS));assertTrue(b.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from booking_reminders where booking_id=?",Integer.class,booking));
        assertEquals(1,jdbc.queryForObject("select count(*) from mail_outbox m join booking_reminders r on r.notification_id=m.notification_id where r.booking_id=?",Integer.class,booking));
    }
    @Test void concurrentOutboxWorkersClaimOnlyOnce() throws Exception {
        var notifications=new com.trimax.linkhub.service.NotificationService(jdbc);
        tx.executeWithoutResult(s->notifications.record(owner,"mail:"+owner,"TEST","Subject","Body","inbox",List.of(new com.trimax.linkhub.service.NotificationService.Mail(username+"@example.com","Subject","Body"))));
        UUID id=jdbc.queryForObject("select m.id from mail_outbox m join notifications n on n.id=m.notification_id where n.owner_id=?",UUID.class,owner);
        jdbc.update("update mail_outbox set next_attempt_at=? where id<>? and status='PENDING'",Timestamp.from(Instant.now().plusSeconds(10000)),id);
        var store=new com.trimax.linkhub.service.MailOutboxStore(jdbc,Clock.systemUTC());
        var barrier=new CyclicBarrier(2);
        try(var workers=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> claim=()->{barrier.await(10,TimeUnit.SECONDS);return tx.execute(s->store.claim().isPresent());};
            var first=workers.submit(claim);var second=workers.submit(claim);
            assertNotEquals(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        }
        assertEquals(1,jdbc.queryForObject("select attempts from mail_outbox where id=?",Integer.class,id));
    }
}
