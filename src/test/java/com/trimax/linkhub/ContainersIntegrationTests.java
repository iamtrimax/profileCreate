package com.trimax.linkhub;

import com.trimax.linkhub.service.RateLimiter;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers @EnabledIfSystemProperty(named="containers",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"linkhub.mail.enabled=false","linkhub.reminders.enabled=false","linkhub.ops.token=0123456789abcdef0123456789abcdef"})
@ActiveProfiles("containers")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class ContainersIntegrationTests {
    @Container static GenericContainer<?> postgres=new GenericContainer<>("postgres:17-alpine").withEnv("POSTGRES_USER","linkhub").withEnv("POSTGRES_PASSWORD","container-test").withEnv("POSTGRES_DB","linkhub").withExposedPorts(5432).withTmpFs(Map.of("/var/lib/postgresql/data","rw")).withStartupTimeout(java.time.Duration.ofMinutes(3));
    @Container static GenericContainer<?> redis=new GenericContainer<>("redis:7-alpine").withExposedPorts(6379).withStartupTimeout(java.time.Duration.ofMinutes(3));
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:postgresql://"+postgres.getHost()+":"+postgres.getMappedPort(5432)+"/linkhub");
        registry.add("spring.datasource.username",()->"linkhub");registry.add("spring.datasource.password",()->"container-test");
        registry.add("spring.data.redis.host",redis::getHost);registry.add("spring.data.redis.port",()->redis.getMappedPort(6379));
    }
    @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired StringRedisTemplate redisTemplate;@Autowired RateLimiter limiter;
    @Test void redisSessionsRateLimitsAndPostgresHealthThroughRealHttp()throws Exception{
        CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        try(HttpClient client=HttpClient.newBuilder().cookieHandler(cookies).build()){
            String base="http://localhost:"+port;
            var csrf=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,csrf.statusCode());
            assertTrue(cookies.getCookieStore().getCookies().stream().anyMatch(c->c.getName().equals("SESSION")));
            assertFalse(redisTemplate.keys("linkhub:sessions:*").isEmpty());
            var health=client.send(HttpRequest.newBuilder(URI.create(base+"/actuator/health")).GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,health.statusCode());assertTrue(health.body().contains("UP"));assertFalse(health.body().contains("components"));
            assertEquals(401,client.send(HttpRequest.newBuilder(URI.create(base+"/actuator/prometheus")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
            var metrics=client.send(HttpRequest.newBuilder(URI.create(base+"/actuator/prometheus")).header("Authorization","Bearer 0123456789abcdef0123456789abcdef").GET().build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,metrics.statusCode());assertTrue(metrics.body().contains("jvm_memory"));
            var json=tools.jackson.databind.json.JsonMapper.builder().build();var csrfData=json.readTree(csrf.body());
            String header=csrfData.path("headerName").asText(),token=csrfData.path("token").asText();
            var registered=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/register")).header(header,token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"container@example.com\",\"username\":\"containeruser\",\"displayName\":\"Container\",\"password\":\"Container-password123\"}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(201,registered.statusCode());
            var logged=client.send(HttpRequest.newBuilder(URI.create(base+"/api/auth/login")).header(header,token).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"container@example.com\",\"password\":\"Container-password123\"}")).build(),HttpResponse.BodyHandlers.ofString());assertEquals(200,logged.statusCode());
            assertEquals(200,client.send(HttpRequest.newBuilder(URI.create(base+"/api/me")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        }
        String key="container-test:"+UUID.randomUUID();assertTrue(limiter.allow(key,1,60));assertFalse(limiter.allow(key,1,60));assertTrue(redisTemplate.getExpire("linkhub:rate:"+key)>0);
        assertTrue(jdbc.queryForObject("select count(*) from pg_constraint where contype='x'",Integer.class)>0);
        assertEquals(0,postgres.execInContainer("pg_dump","-U","linkhub","-d","linkhub","-Fc","-f","/tmp/validation.dump").getExitCode());
        assertEquals(0,postgres.execInContainer("createdb","-U","linkhub","linkhub_restore_validation").getExitCode());
        var restored=postgres.execInContainer("pg_restore","-U","linkhub","-d","linkhub_restore_validation","--no-owner","--no-acl","--exit-on-error","--single-transaction","/tmp/validation.dump");assertEquals(0,restored.getExitCode(),restored.getStderr());
        assertEquals("1",postgres.execInContainer("psql","-U","linkhub","-d","linkhub_restore_validation","-tAc","select count(*) from accounts").getStdout().strip());
    }
}
