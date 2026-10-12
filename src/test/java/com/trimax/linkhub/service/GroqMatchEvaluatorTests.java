package com.trimax.linkhub.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GroqMatchEvaluatorTests {
    private final JsonMapper json=JsonMapper.builder().build();
    @SuppressWarnings("unchecked")
    @Test void groundedScoresAreCachedAndProfileChangesInvalidateThem() throws Exception {
        var job=RecruitmentRankingTests.job();var c=RecruitmentRankingTests.candidate("frontend developer",List.of("react"));
        var http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        String output=json.writeValueAsString(Map.of("evaluations",List.of(Map.of("id",0,"score",85,"jdEvidence","ReactJS","candidateEvidence","frontend developer"))));
        when(response.body()).thenReturn(json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",output))))));
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new GroqMatchEvaluator(json,Clock.systemUTC(),http,true,"private-key","openai/gpt-oss-120b");
        assertEquals(85,client.evaluate(job,List.of(c)).get(c.ownerId()));assertEquals(85,client.evaluate(job,List.of(c)).get(c.ownerId()));
        var captor=ArgumentCaptor.forClass(HttpRequest.class);verify(http,times(1)).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        assertEquals("https://api.groq.com/openai/v1/chat/completions",captor.getValue().uri().toString());assertEquals(Duration.ofSeconds(8),captor.getValue().timeout().orElseThrow());
        var edited=RecruitmentRankingTests.candidate("backend developer",List.of("react"));
        assertTrue(client.evaluate(job,List.of(edited)).isEmpty());verify(http,times(2)).send(any(),any(HttpResponse.BodyHandler.class));
    }
    @SuppressWarnings("unchecked")
    @Test void rejectsInventedEvidenceDuplicateIdsInvalidScoresAndTruncatedOutput() throws Exception {
        var job=RecruitmentRankingTests.job();var c=RecruitmentRankingTests.candidate("frontend developer",List.of("react"));
        var http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new GroqMatchEvaluator(json,Clock.systemUTC(),http,true,"private-key","model");
        for(var bad:List.of(Map.of("id",0,"score",101,"jdEvidence","ReactJS","candidateEvidence","frontend developer"),
            Map.of("id",0,"score",90,"jdEvidence","Invented","candidateEvidence","frontend developer"),
            Map.of("id",5,"score",90,"jdEvidence","ReactJS","candidateEvidence","frontend developer"),
            Map.of("id",0,"score",90,"jdEvidence","ReactJS","candidateEvidence","Invented"))) {
            String output=json.writeValueAsString(Map.of("evaluations",List.of(bad)));
            when(response.body()).thenReturn(json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",output))))));
            assertTrue(client.evaluate(job,List.of(c)).isEmpty());
        }
        when(response.body()).thenReturn("{\"choices\":[{\"finish_reason\":\"length\"}]}");assertTrue(client.evaluate(job,List.of(c)).isEmpty());
        when(response.body()).thenReturn("not json");assertTrue(client.evaluate(job,List.of(c)).isEmpty());
    }
    @SuppressWarnings("unchecked")
    @Test void providerQuotaTimeoutAndMissingKeyFallBackWithoutBlockingMatch() throws Exception {
        var job=RecruitmentRankingTests.job();var c=RecruitmentRankingTests.candidate("frontend developer",List.of("react"));
        var http=mock(HttpClient.class);var disabled=new GroqMatchEvaluator(json,Clock.systemUTC(),http,true,"","model");
        assertTrue(disabled.evaluate(job,List.of(c)).isEmpty());verifyNoInteractions(http);
        HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(429);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new GroqMatchEvaluator(json,Clock.systemUTC(),http,true,"key","model");assertTrue(client.evaluate(job,List.of(c)).isEmpty());
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("private-key"));assertTrue(client.evaluate(job,List.of(c)).isEmpty());
    }
}
