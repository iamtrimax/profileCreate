package com.trimax.linkhub;

import com.trimax.linkhub.service.*;
import com.trimax.linkhub.config.AuditFilter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest(properties="linkhub.ops.token=audit-test-token-at-least-32-characters")
@AutoConfigureMockMvc @ActiveProfiles("test")
class AuditIntegrationTests {
    static final String TOKEN="Bearer audit-test-token-at-least-32-characters";
    @Autowired JdbcTemplate jdbc; @Autowired AuditService audit; @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder; @Autowired PlatformTransactionManager transactions;
    @MockitoBean RateLimiter limiter;
    UUID owner; String email; MockHttpSession session;
    @BeforeEach void setup(){
        when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);
        owner=UUID.randomUUID();String username="a"+owner.toString().replace("-","").substring(0,20);email=username+"@example.com";
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,?,?,'Audit',true,current_timestamp)",owner,email,encoder.encode("Secret-password123"),username);
        session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner.toString(),null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);session.setAttribute("AUTH_VERSION",0L);
    }
    Map<String,Object> event(String request){return jdbc.queryForMap("select * from audit_logs where request_id=?",request);}
    @Test void recordsMutationDenialAndBusinessFailureWithoutContent()throws Exception{
        String id=UUID.randomUUID().toString();
        mvc.perform(put("/api/me/profile").session(session).with(csrf()).header("X-Request-ID",id).contentType("application/json")
            .content("{\"displayName\":\"Private profile body\",\"bio\":\"Private biography\",\"published\":true}")).andExpect(status().isOk());
        var row=event(id);assertEquals(owner,row.get("actor_id"));assertEquals("PROFILE_UPDATE",row.get("action"));assertEquals("SUCCESS",row.get("outcome"));
        assertFalse(row.toString().contains("Private profile body"));
        String denied=UUID.randomUUID().toString();
        mvc.perform(put("/api/me/profile").session(session).header("X-Request-ID",denied)).andExpect(status().isForbidden());
        assertEquals("DENIED",event(denied).get("outcome"));
        String failed=UUID.randomUUID().toString();
        mvc.perform(delete("/api/me/links/"+UUID.randomUUID()).session(session).with(csrf()).header("X-Request-ID",failed)).andExpect(status().isNotFound());
        assertEquals("FAILURE",event(failed).get("outcome"));
    }
    @Test void loginAndLogoutPreserveCorrectActorAndHideCredentials()throws Exception{
        String id=UUID.randomUUID().toString();
        var result=mvc.perform(post("/api/auth/login").with(csrf()).header("X-Request-ID",id).contentType("application/json")
            .content("{\"email\":\""+email+"\",\"password\":\"Secret-password123\"}")).andExpect(status().isOk()).andReturn();
        assertEquals(owner,event(id).get("actor_id"));assertFalse(event(id).toString().contains(email));
        assertFalse(event(id).toString().contains("Secret-password123"));
        String logout=UUID.randomUUID().toString();
        mvc.perform(post("/api/auth/logout").session((MockHttpSession)result.getRequest().getSession()).with(csrf()).header("X-Request-ID",logout)).andExpect(status().isNoContent());
        assertEquals(owner,event(logout).get("actor_id"));
        String bad=UUID.randomUUID().toString();
        mvc.perform(post("/api/auth/login").with(csrf()).header("X-Request-ID",bad).contentType("application/json")
            .content("{\"email\":\""+email+"\",\"password\":\"Wrong-password123\"}")).andExpect(status().isUnauthorized());
        assertNull(event(bad).get("actor_id"));assertEquals("DENIED",event(bad).get("outcome"));
    }
    @Test void opsRequiresBearerAndValidatesFilters()throws Exception{
        mvc.perform(get("/api/ops/audit-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ops/audit-logs").session(session)).andExpect(status().isForbidden());
        mvc.perform(get("/api/ops/audit-logs").header("Authorization","Bearer wrong")).andExpect(status().isUnauthorized());
        String id=UUID.randomUUID().toString();audit.record(owner,"PROFILE_UPDATE","PROFILE",owner.toString(),200,id,1);
        mvc.perform(get("/api/ops/audit-logs").header("Authorization",TOKEN).param("actorId",owner.toString()).param("action","PROFILE_UPDATE").param("outcome","SUCCESS").param("requestId",id))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.summary.total").value(1));
        mvc.perform(get("/api/ops/audit-logs").header("Authorization",TOKEN).param("size","101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ops/audit-logs").header("Authorization",TOKEN).param("from","2020-01-01T00:00:00Z")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/ops/audit-logs").header("Authorization",TOKEN).param("actorId","invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/ops/metrics")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"audit-rows\"")));
        mvc.perform(get("/assets/audit.js")).andExpect(status().isOk());
    }
    @Test void cursorKeepsTotalsAndAuditSurvivesOuterRollback(){
        for(int i=0;i<3;i++)audit.record(owner,"LINK_DELETE","LINK",null,i==0?404:204,null,1);
        Instant from=Instant.now().minusSeconds(60),to=Instant.now().plusSeconds(60);
        var first=audit.find(from,to,null,owner,null,null,null,2);
        var second=audit.find(from,to,null,owner,null,null,first.nextCursor(),2);
        assertEquals(3,first.summary().total());assertEquals(3,second.summary().total());
        assertEquals(2,first.items().size());assertEquals(1,second.items().size());assertNull(second.nextCursor());
        assertTrue(first.items().stream().noneMatch(item->item.id()==second.items().getFirst().id()));
        String id=UUID.randomUUID().toString();
        new TransactionTemplate(transactions).executeWithoutResult(tx->{audit.record(owner,"LINK_DELETE","LINK",null,404,id,1);tx.setRollbackOnly();});
        assertEquals("FAILURE",event(id).get("outcome"));
    }
    @Test void retentionRemovesOnlyExpiredRows(){
        String old=UUID.randomUUID().toString(),fresh=UUID.randomUUID().toString();
        audit.record(owner,"LINK_DELETE","LINK",null,204,old,1);audit.record(owner,"LINK_DELETE","LINK",null,204,fresh,1);
        jdbc.update("update audit_logs set created_at=? where request_id=?",java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(181))),old);
        audit.purge();assertEquals(0,jdbc.queryForObject("select count(*) from audit_logs where request_id=?",Integer.class,old));
        assertEquals("SUCCESS",event(fresh).get("outcome"));
    }
    @Test void storageFailureDoesNotChangeCompletedResponse()throws Exception{
        AuditService broken=mock(AuditService.class);AccountSecurityService accounts=mock(AccountSecurityService.class);
        doThrow(new IllegalStateException("offline")).when(broken).record(any(),anyString(),anyString(),any(),anyInt(),anyString(),anyLong());
        MockHttpServletRequest req=new MockHttpServletRequest("POST","/api/auth/login");MockHttpServletResponse res=new MockHttpServletResponse();
        new AuditFilter(broken,accounts).doFilter(req,res,(request,response)->((jakarta.servlet.http.HttpServletResponse)response).setStatus(401));
        assertEquals(401,res.getStatus());verify(broken).unavailable(anyString());
    }
}
