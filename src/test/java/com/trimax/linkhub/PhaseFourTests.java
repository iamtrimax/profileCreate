package com.trimax.linkhub;

import com.trimax.linkhub.service.*;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class PhaseFourTests {
    @Autowired JdbcTemplate jdbc;@Autowired AccountSecurityService accounts;@Autowired SecurityMailCipher cipher;
    @Autowired PasswordEncoder encoder;@Autowired MockMvc mvc;@Autowired AnalyticsService analytics;
    @Autowired NotificationStream streams;
    @MockitoBean RateLimiter limiter;
    UUID owner;String username,email;MockHttpSession session;
    @BeforeEach void setup(){
        when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);
        owner=UUID.randomUUID();username="p"+owner.toString().replace("-","").substring(0,20);email=username+"@example.com";
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,?,?,'Phase four',true,current_timestamp)",owner,email,encoder.encode("Old-password123"),username);
        session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner.toString(),null,List.of()));session.setAttribute("SPRING_SECURITY_CONTEXT",context);session.setAttribute("AUTH_VERSION",0L);
    }
    String token(String purpose){
        String encrypted=jdbc.queryForObject("select m.body from mail_outbox m join notifications n on n.id=m.notification_id where n.owner_id=? order by m.created_at desc limit 1",String.class,owner);
        String body=cipher.decrypt(encrypted);String value=body.split("#"+purpose+"=")[1].substring(0,43);
        assertFalse(encrypted.contains(value));assertEquals(1,jdbc.queryForObject("select count(*) from account_tokens where token_hash=?",Integer.class,AccountSecurityService.hash(value)));return value;
    }
    @Test void resetIsSingleUseAndRevokesSession()throws Exception{
        var stream=mvc.perform(get("/api/me/notifications/stream").session(session)).andExpect(request().asyncStarted()).andReturn();
        accounts.requestReset(email);String token=token("reset");accounts.reset(token,"New-password123");
        streams.pulse();assertNull(stream.getAsyncResult(1000));
        assertThrows(ResponseStatusException.class,()->accounts.reset(token,"Another-password123"));
        assertTrue(encoder.matches("New-password123",jdbc.queryForObject("select password_hash from accounts where id=?",String.class,owner)));
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"Old-password123\"}")).andExpect(status().isUnauthorized());
        var logged=mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\",\"password\":\"New-password123\"}")).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/me").session((MockHttpSession)logged.getRequest().getSession())).andExpect(status().isOk());
    }
    @Test void verificationRejectsWrongPurposeExpiredAndReusedTokens(){
        accounts.requestVerification(owner);String token=token("verify");
        assertThrows(ResponseStatusException.class,()->accounts.reset(token,"New-password123"));
        jdbc.update("update account_tokens set expires_at=current_timestamp - interval '1' day where owner_id=?",owner);
        assertThrows(ResponseStatusException.class,()->accounts.verify(token));
        jdbc.update("update account_tokens set expires_at=current_timestamp + interval '1' day where owner_id=?",owner);
        accounts.verify(token);assertThrows(ResponseStatusException.class,()->accounts.verify(token));
        assertTrue(jdbc.queryForObject("select email_verified from accounts where id=?",Boolean.class,owner));
    }
    @Test void resetRequestDoesNotRevealUnknownAccountAndRequiresCsrf()throws Exception{
        mvc.perform(post("/api/auth/password-reset/request").contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isForbidden());
        var known=mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\""+email+"\"}")).andExpect(status().isOk()).andReturn();
        var unknown=mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\"missing@example.com\"}")).andExpect(status().isOk()).andReturn();
        assertEquals(known.getResponse().getContentAsString(),unknown.getResponse().getContentAsString());
    }
    @Test void analyticsCountsUniqueDailyAndFiltersBotsAndPrivacyHeaders()throws Exception{
        String path="/api/public/"+username+"/visits";Cookie visitor=new Cookie("LH_VISITOR",UUID.randomUUID().toString());
        for(int i=0;i<2;i++)mvc.perform(post(path).with(csrf()).cookie(visitor).contentType("application/json").content("{\"referrer\":\"https://example.org/private?secret=123\"}")).andExpect(status().isNoContent());
        mvc.perform(post(path).with(csrf()).header("User-Agent","Googlebot")).andExpect(status().isNoContent());
        mvc.perform(post(path).with(csrf()).header("DNT","1")).andExpect(status().isNoContent()).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(post(path).with(csrf()).header("Sec-GPC","1")).andExpect(status().isNoContent());
        var result=analytics.engagement(owner,30);assertEquals(2,result.views());assertEquals(1,result.dailyUniqueVisitors());assertEquals("example.org",result.referrers().getFirst().label());
        mvc.perform(get("/api/me/analytics").session(session)).andExpect(jsonPath("$.totalViews").value(2));
        UUID link=UUID.randomUUID();jdbc.update("insert into social_links(id,owner_id,title,url) values (?,?,'GitHub','https://github.com')",link,owner);
        mvc.perform(post("/api/public/"+username+"/clicks/"+link).with(csrf()).cookie(visitor)).andExpect(status().isNoContent());
        mvc.perform(post("/api/public/"+username+"/clicks/"+UUID.randomUUID()).with(csrf())).andExpect(status().isNotFound());
        assertEquals(1,analytics.engagement(owner,30).clicks());
    }
    @Test void metricsArePrivateAndCorrelationIdIsReturned()throws Exception{
        mvc.perform(get("/ops/metrics")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"ops-token\"")));
        mvc.perform(get("/assets/metrics.js")).andExpect(status().isOk());
        mvc.perform(get("/actuator/metrics").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/env").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/csrf").header("X-Request-ID","test-request-42")).andExpect(status().isOk()).andExpect(header().string("X-Request-ID","test-request-42"));
    }
}
