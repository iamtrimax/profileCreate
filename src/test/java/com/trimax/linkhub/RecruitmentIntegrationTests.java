package com.trimax.linkhub;

import com.trimax.linkhub.api.RecruitmentModels.*;
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
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class RecruitmentIntegrationTests {
    @Autowired RecruitmentService service;
    @Autowired BookingService bookings;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired tools.jackson.databind.ObjectMapper json;
    @MockitoBean RateLimiter limiter;
    @MockitoBean GroqJdExtractor llm;
    UUID recruiter,candidate,other;
    final Set<UUID> createdAccounts=new HashSet<>();
    String role;
    @BeforeEach void setup() {
        when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);
        recruiter=account();candidate=account();other=account();role="Java "+UUID.randomUUID();
    }
    UUID account() {
        UUID id=UUID.randomUUID();String username="r"+id.toString().replace("-","").substring(0,20);
        jdbc.update("insert into accounts(id,email,password_hash,username,display_name,published,created_at) values (?,?,'test',?,'Test user',true,current_timestamp)",id,username+"@example.com",username);
        createdAccounts.add(id);
        return id;
    }
    @AfterEach void hideTestProfiles() {
        for(UUID id:createdAccounts)jdbc.update("update candidate_profiles set seeking=false where owner_id=?",id);
    }
    MockHttpSession session(UUID owner) {
        var session=new MockHttpSession();var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(owner.toString(),null,List.of()));
        session.setAttribute("SPRING_SECURITY_CONTEXT",context);return session;
    }
    CandidateInput profile(boolean seeking,int years,WorkMode mode,String location,long min,long max,List<String> skills) {
        return new CandidateInput(seeking,role,"Java experience",years,location,mode,min,max,skills);
    }
    void suitable(UUID owner) {service.saveCandidate(owner,profile(true,3,WorkMode.HYBRID,"Hồ Chí Minh",20000000,30000000,List.of("Java"," Spring Boot ","JAVA","Docker")));}
    Job job(WorkMode mode) {return service.saveJob(recruiter,null,new JobInput("Java Developer","LinkHub Test",role,"Build services",2,"Hồ Chí Minh",mode,25000000,35000000,List.of("JAVA","Spring Boot"),List.of("Docker")));}
    UUID accepted() {suitable(candidate);UUID id=service.discover(recruiter,job(WorkMode.HYBRID).id(),null).matchId();service.invite(recruiter,id);service.reply(candidate,id,MatchStatus.ACCEPTED);return id;}
    @Test void selectionRanksFitRetainsHardPreferencesAndReportsPartialGaps() {
        suitable(candidate);assertEquals(List.of("docker","java","spring boot"),service.candidate(candidate).skills());
        suitable(recruiter);
        service.saveCandidate(other,profile(false,3,WorkMode.HYBRID,"Hồ Chí Minh",20000000,30000000,List.of("Java","Spring Boot")));
        UUID junior=account(),wrongPlace=account(),missingSkill=account(),wrongSalary=account(),wrongMode=account(),wrongRole=account();
        service.saveCandidate(junior,profile(true,1,WorkMode.HYBRID,"Hồ Chí Minh",20000000,30000000,List.of("Java","Spring Boot")));
        service.saveCandidate(wrongPlace,profile(true,3,WorkMode.HYBRID,"Hà Nội",20000000,30000000,List.of("Java","Spring Boot")));
        service.saveCandidate(missingSkill,profile(true,3,WorkMode.HYBRID,"Hồ Chí Minh",20000000,30000000,List.of("Java")));
        service.saveCandidate(wrongSalary,profile(true,3,WorkMode.HYBRID,"Hồ Chí Minh",40000000,50000000,List.of("Java","Spring Boot")));
        service.saveCandidate(wrongMode,profile(true,3,WorkMode.REMOTE,"Hồ Chí Minh",20000000,30000000,List.of("Java","Spring Boot")));
        service.saveCandidate(wrongRole,new CandidateInput(true,"Other role","",3,"Hồ Chí Minh",WorkMode.HYBRID,20000000,30000000,List.of("Java","Spring Boot")));
        Job job=job(WorkMode.HYBRID);Suggestion result=service.discover(recruiter,job.id(),null);
        assertEquals(candidate,result.candidate().ownerId());assertTrue(result.reasons().stream().anyMatch(s->s.contains("docker")));
        assertEquals(result.matchId(),service.discover(recruiter,job.id(),null).matchId());
        Set<UUID> remaining=new HashSet<>();Suggestion next=service.discover(recruiter,job.id(),result.matchId());
        while(next!=null){remaining.add(next.candidate().ownerId());assertFalse(next.assessment().gaps().isEmpty());next=service.discover(recruiter,job.id(),next.matchId());}
        assertEquals(Set.of(junior,missingSkill,wrongSalary),remaining);assertNull(service.discover(recruiter,job.id(),null));
        assertThrows(ResponseStatusException.class,()->service.discover(recruiter,job.id(),result.matchId()));
    }
    @Test void nextNeverRepeatsAndRemoteIgnoresLocation() {
        service.saveCandidate(candidate,profile(true,4,WorkMode.REMOTE,"Hà Nội",20000000,30000000,List.of("Java","Spring Boot")));
        service.saveCandidate(other,profile(true,4,WorkMode.ANY,"Đà Nẵng",20000000,30000000,List.of("Java","Spring Boot")));
        Job job=job(WorkMode.REMOTE);Suggestion one=service.discover(recruiter,job.id(),null);Suggestion two=service.discover(recruiter,job.id(),one.matchId());
        assertNotEquals(one.candidate().ownerId(),two.candidate().ownerId());assertNull(service.discover(recruiter,job.id(),two.matchId()));
    }
    @Test void invitationsRequireConsentProtectContactsAndRecheckCandidateOptIn() {
        suitable(candidate);Job job=job(WorkMode.HYBRID);UUID id=service.discover(recruiter,job.id(),null).matchId();
        assertTrue(service.matches(candidate).isEmpty());assertThrows(ResponseStatusException.class,()->service.invite(candidate,id));
        service.saveCandidate(candidate,profile(false,3,WorkMode.HYBRID,"Hồ Chí Minh",20000000,30000000,List.of("Java","Spring Boot")));
        assertThrows(ResponseStatusException.class,()->service.invite(recruiter,id));
        service.saveCandidate(candidate,new CandidateInput(true,"Accountant","",3,"Hồ Chí Minh",WorkMode.HYBRID,20000000,30000000,List.of("Java","Spring Boot")));
        assertThrows(ResponseStatusException.class,()->service.invite(recruiter,id));suitable(candidate);
        Match pending=service.invite(recruiter,id);assertNull(pending.contactEmail());assertNull(service.matches(candidate).getFirst().contactEmail());
        assertEquals(id,service.invite(recruiter,id).id());
        assertThrows(ResponseStatusException.class,()->service.messages(recruiter,id));
        assertThrows(ResponseStatusException.class,()->service.reply(recruiter,id,MatchStatus.ACCEPTED));
        Match match=service.reply(candidate,id,MatchStatus.ACCEPTED);assertNotNull(match.contactEmail());assertNotNull(service.matches(recruiter).getFirst().contactEmail());
        assertEquals(MatchStatus.ACCEPTED,service.reply(candidate,id,MatchStatus.ACCEPTED).status());
        service.send(recruiter,id,"Hello");assertEquals("Hello",service.messages(candidate,id).getFirst().body());
        assertThrows(ResponseStatusException.class,()->service.messages(other,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from notifications where owner_id=? and kind='RECRUITMENT_INVITE'",Integer.class,candidate));
        assertThrows(ResponseStatusException.class,()->service.saveJob(recruiter,job.id(),new JobInput("Changed","Company",role,"Changed",2,"Hồ Chí Minh",WorkMode.HYBRID,1,2,List.of("java"),List.of())));
    }
    @Test void interviewNeedsCandidateConfirmationAndPreventsOverlapForBothPeople() {
        UUID match=accepted();Instant start=Instant.now().plusSeconds(86400).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        Interview interview=service.propose(recruiter,match,new InterviewInput(start,30,"Online meeting"));
        assertEquals(InterviewStatus.PENDING,interview.status());
        assertThrows(ResponseStatusException.class,()->service.interviewReply(recruiter,match,interview.id(),InterviewStatus.CONFIRMED));
        assertThrows(ResponseStatusException.class,()->service.propose(recruiter,match,new InterviewInput(start.plusSeconds(60),30,"Overlap")));
        assertThrows(ResponseStatusException.class,()->service.propose(candidate,match,new InterviewInput(start.plusSeconds(3600),30,"Wrong actor")));
        assertThrows(ResponseStatusException.class,()->service.interviews(other,match));
        assertEquals(InterviewStatus.CONFIRMED,service.interviewReply(candidate,match,interview.id(),InterviewStatus.CONFIRMED).status());
        service.interviewReply(recruiter,match,interview.id(),InterviewStatus.CANCELLED);
        assertNotNull(service.propose(recruiter,match,new InterviewInput(start,30,"New time")));
        Instant busy=start.plusSeconds(7200);
        jdbc.update("insert into bookings(id,owner_id,service_title,customer_name,customer_email,message,starts_at,ends_at,timezone,status) values (?,?,'Service','Guest','guest@example.com','',?,?,'Asia/Ho_Chi_Minh','CONFIRMED')",UUID.randomUUID(),candidate,java.sql.Timestamp.from(busy),java.sql.Timestamp.from(busy.plusSeconds(1800)));
        assertThrows(ResponseStatusException.class,()->service.propose(recruiter,match,new InterviewInput(busy,30,"Existing booking")));
    }
    @Test void closedJobsAndInvalidSalaryAreRejected() {
        suitable(candidate);Job job=job(WorkMode.HYBRID);UUID id=service.discover(recruiter,job.id(),null).matchId();service.invite(recruiter,id);service.setOpen(recruiter,job.id(),false);
        assertThrows(ResponseStatusException.class,()->service.discover(recruiter,job.id(),null));assertThrows(ResponseStatusException.class,()->service.reply(candidate,id,MatchStatus.ACCEPTED));
        assertEquals(MatchStatus.DECLINED,service.reply(candidate,id,MatchStatus.DECLINED).status());
        assertThrows(ResponseStatusException.class,()->service.saveCandidate(other,profile(true,3,WorkMode.ANY,"Hà Nội",10,1,List.of("Java"))));
    }
    @Test void existingPublicBookingSlotsRespectRecruitmentInterviews() {
        UUID match=accepted();ZoneId zone=ZoneId.of("Asia/Ho_Chi_Minh");LocalDate date=LocalDate.now(zone).plusDays(2);
        Instant start=date.atTime(10,0).atZone(zone).toInstant();Interview interview=service.propose(recruiter,match,new InterviewInput(start,30,"Meeting"));
        for(UUID person:List.of(recruiter,candidate)) {
            UUID offering=UUID.randomUUID();jdbc.update("insert into service_offerings(id,owner_id,title,description,price,duration_minutes) values (?,?,'Service','',0,30)",offering,person);
            bookings.saveSchedule(person,new com.trimax.linkhub.api.BookingModels.Schedule(zone.getId(),List.of(new com.trimax.linkhub.api.BookingModels.Rule(date.getDayOfWeek().getValue(),540,1020)),List.of()));
            String username=jdbc.queryForObject("select username from accounts where id=?",String.class,person);
            assertTrue(bookings.slots(username,offering,date).slots().stream().noneMatch(slot->slot.startsAt().isBefore(interview.endsAt())&&interview.startsAt().isBefore(slot.endsAt())));
        }
        service.interviewReply(candidate,match,interview.id(),InterviewStatus.CANCELLED);
        UUID offering=jdbc.queryForObject("select id from service_offerings where owner_id=?",UUID.class,recruiter);String username=jdbc.queryForObject("select username from accounts where id=?",String.class,recruiter);
        assertTrue(bookings.slots(username,offering,date).slots().stream().anyMatch(slot->slot.startsAt().equals(start)));
    }
    @Test void jdOnlyApiParsesPersistsAndRejectsIncompleteInput() throws Exception {
        String body="{\"description\":\"Position: Java Developer\\nCompany: LinkHub\\nLocation: Hanoi\\nWork mode: Hybrid\\nRequired skills: Java, Spring Boot\\nPreferred skills: Docker\\nExperience: 2 years\\nSalary: 20-30 million VND\"}";
        mvc.perform(post("/api/me/recruitment/jobs/parse-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.missingFields").isEmpty()).andExpect(jsonPath("$.data.role").value("Java Developer")).andExpect(jsonPath("$.formattedDescription").isString());
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.requiredSkills[0]").value("java"));
        Job saved=service.jobs(recruiter).getFirst();assertEquals(20000000,saved.salaryMin());assertEquals(List.of("docker"),saved.preferredSkills());
        assertEquals(json.readTree(body).path("description").asText(),saved.description());
        assertTrue(saved.formattedDescription().contains("**Position:** Java Developer"));
        assertEquals(saved.description(),jdbc.queryForObject("select description from recruitment_jobs where id=?",String.class,saved.id()));
        mvc.perform(put("/api/me/recruitment/jobs/"+saved.id()+"/from-jd").session(session(other)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isNotFound());
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content("{\"description\":\"Java Developer\"}"))
            .andExpect(status().isBadRequest());
        assertEquals(1,service.jobs(recruiter).size());
        mvc.perform(post("/api/me/recruitment/jobs/parse-jd").session(session(recruiter)).contentType("application/json").content(body)).andExpect(status().isForbidden());
    }
    @Test void llmPreviewTokenIsRequiredBoundToOwnerAndSavedWithoutSecondLlmCall() throws Exception {
        String description="LinkHub tuyển Java Developer tại Hà Nội, hybrid, cần Java và Spring Boot, 2 năm kinh nghiệm, lương 20-30 triệu VND/tháng.";
        JobInput extracted=new JobInput("Java Developer","LinkHub","Java Developer",description,2,"Hà Nội",WorkMode.HYBRID,20000000,30000000,List.of("java","spring boot"),List.of());
        when(llm.enabled()).thenReturn(true);when(llm.extract(description)).thenReturn(new ParsedJd(extracted,List.of(),"groq",List.of()));
        String input=json.writeValueAsString(Map.of("description",description));
        String preview=mvc.perform(post("/api/me/recruitment/jobs/parse-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(input))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("groq")).andReturn().getResponse().getContentAsString();
        String token=json.readTree(preview).path("extractionId").asText();assertFalse(token.isBlank());
        String reviewed=json.writeValueAsString(Map.of("description",description,"extractionId",token));
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(other)).with(csrf()).contentType("application/json").content(reviewed)).andExpect(status().isConflict());
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(input)).andExpect(status().isConflict());
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(reviewed)).andExpect(status().isCreated());
        assertEquals(description,service.jobs(recruiter).getFirst().description());assertEquals(extracted.requiredSkills(),service.jobs(recruiter).getFirst().requiredSkills());
        verify(llm,times(1)).extract(description);
        String changed=json.writeValueAsString(Map.of("description",description+" Updated","extractionId",token));
        mvc.perform(post("/api/me/recruitment/jobs/from-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content(changed)).andExpect(status().isConflict());
        assertEquals(1,service.jobs(recruiter).size());
    }
    @Test void invalidGroqKeyReturnsNonRetryableErrorWithoutProviderDetails() throws Exception {
        when(llm.enabled()).thenReturn(true);
        when(llm.extract(anyString())).thenThrow(new GroqRequestException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
            "GROQ_AUTH_FAILED","Groq từ chối xác thực.",401,"req_private",false,null));
        mvc.perform(post("/api/me/recruitment/jobs/parse-jd").session(session(recruiter)).with(csrf()).contentType("application/json").content("{\"description\":\"Sample JD\"}"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("GROQ_AUTH_FAILED"))
            .andExpect(jsonPath("$.retryable").value(false)).andExpect(jsonPath("$.providerRequestId").doesNotExist())
            .andExpect(header().doesNotExist("Retry-After"));
    }
    @Test void apiEnforcesAuthenticationCsrfOwnershipAndValidation() throws Exception {
        Job job=job(WorkMode.HYBRID);
        mvc.perform(get("/api/me/recruitment/candidate").session(session(candidate))).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/recruitment/jobs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/recruitment/jobs").session(session(recruiter))).andExpect(status().isOk()).andExpect(jsonPath("$[0].company").value("LinkHub Test"));
        mvc.perform(post("/api/me/recruitment/jobs/"+job.id()+"/match").session(session(recruiter))).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/recruitment/jobs/"+job.id()+"/match").session(session(other)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/api/me/recruitment/jobs/"+job.id()+"/match").session(session(recruiter)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.suggestion").doesNotExist());
        mvc.perform(put("/api/me/recruitment/candidate").session(session(candidate)).with(csrf()).contentType("application/json").content("{\"seeking\":true}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/recruitment/feed?page=-1").session(session(candidate))).andExpect(status().isBadRequest());
    }
}
