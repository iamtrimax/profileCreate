package com.trimax.linkhub;

import com.trimax.linkhub.api.RecruitmentModels.*;
import com.trimax.linkhub.seed.RecruitmentSeedService;
import com.trimax.linkhub.service.RecruitmentService;
import com.trimax.linkhub.service.RateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.trimax.linkhub.seed.RecruitmentSeedService.id;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles({"test","seed-recruitment"}) @Transactional
class RecruitmentSeedTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired RecruitmentSeedService seed;
    @Autowired RecruitmentService recruitment;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @MockitoBean RateLimiter limiter;
    @BeforeEach void limits() {when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);}
    @Test void fixturesIncludeLoginAccountsPipelineChatInterviewsAndExistingFeatures() throws Exception {
        assertEquals(146,jdbc.queryForObject("select count(*) from accounts where email like 'demo_%@linkhub.test'",Integer.class));
        assertEquals(134,jdbc.queryForObject("select count(*) from candidate_profiles c join accounts a on a.id=c.owner_id where a.email like 'demo_%@linkhub.test'",Integer.class));
        assertTrue(encoder.matches(RecruitmentSeedService.PASSWORD,jdbc.queryForObject("select password_hash from accounts where id=?",String.class,id("account:tech"))));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"demo_recruiter@linkhub.test\",\"password\":\"LinkHubDemo!2026\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.username").value("demo_recruiter"));
        assertEquals(5,recruitment.jobs(id("account:tech")).size());
        assertEquals(1,recruitment.jobs(id("account:creative")).size());
        assertEquals(Set.of("PENDING","ACCEPTED","DECLINED","SKIPPED"),new HashSet<>(jdbc.queryForList("select status from recruitment_matches where job_id=?",String.class,id("job:java-pipeline"))));
        assertEquals(2,recruitment.messages(id("account:an"),id("match:accepted")).size());
        assertEquals(Set.of(InterviewStatus.PENDING,InterviewStatus.CONFIRMED,InterviewStatus.CANCELLED),new HashSet<>(recruitment.interviews(id("account:tech"),id("match:accepted")).stream().map(Interview::status).toList()));
        assertTrue(recruitment.interviews(id("account:tech"),id("match:accepted")).stream().allMatch(i->i.startsAt().isAfter(Instant.now())));
        assertEquals(1,jdbc.queryForObject("select count(*) from social_links where owner_id=?",Integer.class,id("account:lan")));
        assertEquals(5,jdbc.queryForObject("select count(*) from availability_rules where owner_id=?",Integer.class,id("account:lan")));
        assertEquals(3,jdbc.queryForObject("select count(*) from notifications where event_key like 'demo:recruitment:%'",Integer.class));
    }
    @Test void reseedingDoesNotDuplicateOrOverwriteUserActions() {
        UUID recruiter=id("account:tech"),candidate=id("account:lan");String originalHash=jdbc.queryForObject("select password_hash from accounts where id=?",String.class,recruiter);
        recruitment.setOpen(recruiter,id("job:java-live"),false);
        jdbc.update("update candidate_profiles set seeking=false,summary='User edited' where owner_id=?",candidate);
        recruitment.reply(id("account:bao"),id("match:pending"),MatchStatus.DECLINED);
        recruitment.interviewReply(id("account:an"),id("match:accepted"),id("interview:pending"),InterviewStatus.CANCELLED);
        int accountsBefore=jdbc.queryForObject("select count(*) from accounts",Integer.class);
        int jobsBefore=jdbc.queryForObject("select count(*) from recruitment_jobs",Integer.class);
        int candidatesBefore=jdbc.queryForObject("select count(*) from candidate_profiles",Integer.class);
        UUID expandedCandidate=id("account:frontend_01"),expandedJob=id("job:frontend-24");
        jdbc.update("update candidate_profiles set summary='Edited expanded profile',seeking=false where owner_id=?",expandedCandidate);
        jdbc.update("update recruitment_jobs set open=false where id=?",expandedJob);
        var result=seed.seed();assertEquals(0,result.accountsCreated());
        assertEquals(146,result.accountsTotal());assertEquals(246,result.jobsTotal());
        assertEquals(accountsBefore,jdbc.queryForObject("select count(*) from accounts",Integer.class));
        assertEquals(jobsBefore,jdbc.queryForObject("select count(*) from recruitment_jobs",Integer.class));
        assertEquals(candidatesBefore,jdbc.queryForObject("select count(*) from candidate_profiles",Integer.class));
        assertEquals("Edited expanded profile",recruitment.candidate(expandedCandidate).summary());
        assertFalse(recruitment.candidate(expandedCandidate).seeking());
        assertFalse(recruitment.jobs(id("account:recruiter_frontend")).stream().filter(j->j.id().equals(expandedJob)).findFirst().orElseThrow().open());
        assertFalse(recruitment.jobs(recruiter).stream().filter(j->j.id().equals(id("job:java-live"))).findFirst().orElseThrow().open());
        assertFalse(recruitment.candidate(candidate).seeking());assertEquals("User edited",recruitment.candidate(candidate).summary());
        assertEquals("DECLINED",jdbc.queryForObject("select status from recruitment_matches where id=?",String.class,id("match:pending")));
        assertEquals("CANCELLED",jdbc.queryForObject("select status from recruitment_interviews where id=?",String.class,id("interview:pending")));
        assertEquals(originalHash,jdbc.queryForObject("select password_hash from accounts where id=?",String.class,recruiter));
        assertEquals(2,recruitment.messages(recruiter,id("match:accepted")).size());
        assertEquals(3,jdbc.queryForObject("select count(*) from notifications where event_key like 'demo:recruitment:%'",Integer.class));
    }
    @Test void expandedTracksHaveJobsAndDiscoverableCandidates() {
        for (String track : List.of("frontend","node","python","mobile","qa","devops","data","ba","product","security")) {
            UUID recruiter=id("account:recruiter_"+track);
            assertEquals(24,recruitment.jobs(recruiter).size());
            assertEquals(24,recruitment.jobs(recruiter).stream().map(Job::title).distinct().count());
            for (int index=1;index<=24;index++) {
                var suggestion=recruitment.discover(recruiter,id("job:"+track+"-"+index),null);
                assertNotNull(suggestion,track+" job "+index);
                assertTrue(suggestion.candidate().seeking());
                assertEquals(recruitment.candidate(id("account:"+track+"_01")).desiredRole(),suggestion.candidate().desiredRole());
            }
            assertFalse(recruitment.candidate(id("account:"+track+"_12")).seeking());
        }
        assertEquals(246,jdbc.queryForObject("select count(*) from recruitment_jobs j join accounts a on a.id=j.owner_id where a.email like 'demo_%@linkhub.test'",Integer.class));
    }
    @Test void demoJobsExerciseFilteringAndNextWithoutRepeats() {
        UUID recruiter=id("account:tech"),job=id("job:java-live");
        Set<UUID> found=new HashSet<>();Suggestion suggestion=recruitment.discover(recruiter,job,null);
        while(suggestion!=null) {assertTrue(found.add(suggestion.candidate().ownerId()));suggestion=recruitment.discover(recruiter,job,suggestion.matchId());}
        assertEquals(Set.of(id("account:lan"),id("account:minh"),id("account:linh"),id("account:an"),id("account:bao"),id("account:chi"),id("account:dung"),id("account:junior"),id("account:missing"),id("account:salary")),found);
        assertNull(recruitment.discover(recruiter,id("job:empty"),null));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->recruitment.discover(recruiter,id("job:closed"),null));
        assertEquals(id("account:designer"),recruitment.discover(id("account:creative"),id("job:designer"),null).candidate().ownerId());
        found.clear();job=id("job:java-remote");suggestion=recruitment.discover(recruiter,job,null);
        while(suggestion!=null){assertTrue(found.add(suggestion.candidate().ownerId()));suggestion=recruitment.discover(recruiter,job,suggestion.matchId());}
        assertEquals(Set.of(id("account:remote"),id("account:linh")),found);
    }
}
