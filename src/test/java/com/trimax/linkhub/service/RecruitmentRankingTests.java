package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecruitmentRankingTests {
    private final RecruitmentScorer scorer=new RecruitmentScorer();
    static Job job() {return new Job(UUID.randomUUID(),UUID.randomUUID(),"Recruiter","Frontend","Company","Front-End Developer",
        "ReactJS TypeScript required",2,"Seoul",WorkMode.REMOTE,0,999999999999L,true,Instant.EPOCH,List.of("reactjs","typescript","html5","css3"),List.of("docker"));}
    static Candidate candidate(String role,List<String> skills) {return new Candidate(UUID.randomUUID(),"Name",true,role,"Frontend applications",2,"Hồ Chí Minh",WorkMode.REMOTE,10000000,15000000,skills);}
    @Test void equivalentFrontendNamesAndSkillAliasesMatchWithoutInventingToolkit() {
        Job j=job();Candidate c=candidate("frontend developer",List.of("React","TS","HTML","CSS","Docker"));
        var score=scorer.assess(j,c,null);assertEquals(100,score.score());assertTrue(score.missingSkills().isEmpty());assertTrue(scorer.compatible(j,c));
        assertNotEquals(RecruitmentScorer.skill("redux"),RecruitmentScorer.skill("redux toolkit"));
        assertNotEquals(RecruitmentScorer.skill("c"),RecruitmentScorer.skill("c++"));
    }
    @Test void partialQualificationsHaveLowerScoreAndVisibleGaps() {
        var score=scorer.assess(job(),candidate("frontend developer",List.of("React","HTML")),null);
        assertEquals(List.of("typescript","css3"),score.missingSkills());assertTrue(scorer.recommend(score));assertFalse(score.gaps().isEmpty());assertTrue(score.score()<100);
    }
    @Test void aiCannotOverrideOptOutSelfOrWorkModeConflict() {
        Job j=job();Candidate c=candidate("frontend developer",List.of("React"));
        assertFalse(scorer.compatible(j,new Candidate(c.ownerId(),c.displayName(),false,c.desiredRole(),c.summary(),2,c.location(),c.workMode(),0,1,c.skills())));
        assertFalse(scorer.compatible(j,new Candidate(j.ownerId(),c.displayName(),true,c.desiredRole(),c.summary(),2,c.location(),c.workMode(),0,1,c.skills())));
        assertFalse(scorer.compatible(j,new Candidate(c.ownerId(),c.displayName(),true,c.desiredRole(),c.summary(),2,c.location(),WorkMode.ONSITE,0,1,c.skills())));
    }
    @Test void unrelatedRoleRequiresStrongSemanticEvidenceAndNoSkillsNeverQualifies() {
        Job j=job();Candidate c=candidate("Accountant",List.of("React","TS","HTML","CSS"));
        assertFalse(scorer.recommend(scorer.assess(j,c,null)));
        assertFalse(scorer.shortlist(j,candidate("frontend developer",List.of("Excel"))));
    }
    @Test void ranksByCombinedScoreAndFallsBackToRules() {
        Job j=job();Candidate full=candidate("Frontend Developer",List.of("React","TS","HTML","CSS","Docker")),partial=candidate("Frontend Developer",List.of("React","HTML"));
        var llm=mock(GroqMatchEvaluator.class);var ranking=new RecruitmentRanking(scorer,llm);
        when(llm.evaluate(any(),anyList())).thenReturn(Map.of());
        assertEquals(full.ownerId(),ranking.rank(j,List.of(partial,full)).getFirst().candidate().ownerId());
        when(llm.evaluate(any(),anyList())).thenReturn(Map.of(full.ownerId(),50,partial.ownerId(),100));
        var ranked=ranking.rank(j,List.of(partial,full));assertEquals("rules+groq",ranked.getFirst().assessment().source());
        assertEquals(90,ranked.stream().filter(r->r.candidate().equals(full)).findFirst().orElseThrow().assessment().score());
    }
}
