package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

@Service @RequiredArgsConstructor
public class RecruitmentRanking {
    private final RecruitmentScorer scorer;
    private final GroqMatchEvaluator llm;
    public record Ranked(Candidate candidate,MatchAssessment assessment) {}
    public boolean recommendable(Job job,Candidate c) {
        return scorer.shortlist(job,c)&&scorer.recommend(scorer.assess(job,c,llm.cachedScore(job,c)));
    }
    public List<Ranked> rank(Job job,List<Candidate> candidates) {
        var shortlist=candidates.stream().filter(c->scorer.shortlist(job,c))
            .sorted(Comparator.<Candidate>comparingInt(c->scorer.assess(job,c,null).score()).reversed().thenComparing(Candidate::ownerId)).toList();
        var semantic=llm.evaluate(job,shortlist.stream().limit(5).toList());
        return shortlist.stream().map(c->new Ranked(c,scorer.assess(job,c,semantic.get(c.ownerId()))))
            .filter(r->scorer.recommend(r.assessment()))
            .sorted(Comparator.<Ranked>comparingInt(r->r.assessment().score()).reversed().thenComparing(r->r.candidate().ownerId())).toList();
    }
}
