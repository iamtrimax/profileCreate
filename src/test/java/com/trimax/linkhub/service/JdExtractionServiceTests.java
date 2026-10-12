package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdExtractionServiceTests {
    private final JdParser rules=mock(JdParser.class);
    private final GroqJdExtractor llm=mock(GroqJdExtractor.class);
    private final Clock clock=mock(Clock.class);
    private final Instant now=Instant.parse("2026-10-12T00:00:00Z");
    private final ParsedJd output=new ParsedJd(new JobInput("Role","Company","Role","JD",0,"Remote",WorkMode.REMOTE,0,1,List.of("Java"),List.of()),List.of(),"groq",List.of());
    private JdExtractionService service(){when(clock.instant()).thenReturn(now);when(llm.enabled()).thenReturn(true);when(llm.extract(anyString())).thenReturn(output);return new JdExtractionService(rules,llm,clock);}
    @Test void cachesByOwnerAndExactTextAndSavesOnlyReviewedResult() {
        var service=service();UUID owner=UUID.randomUUID();var parsed=service.parse(owner,"JD");
        assertSame(parsed,service.parse(owner,"JD"));assertSame(parsed,service.forSave(owner,"JD",parsed.extractionId()));verify(llm,times(1)).extract("JD");
        assertThrows(ResponseStatusException.class,()->service.forSave(UUID.randomUUID(),"JD",parsed.extractionId()));
        assertThrows(ResponseStatusException.class,()->service.forSave(owner,"changed",parsed.extractionId()));
        assertThrows(ResponseStatusException.class,()->service.forSave(owner,"JD",null));
        service.parse(UUID.randomUUID(),"JD");verify(llm,times(2)).extract("JD");
    }
    @Test void expiredPreviewRequiresReparseAndDoesNotSilentlyChangeBeforeSave() {
        var service=service();UUID owner=UUID.randomUUID();var parsed=service.parse(owner,"JD");
        when(clock.instant()).thenReturn(now.plusSeconds(901));
        assertThrows(ResponseStatusException.class,()->service.forSave(owner,"JD",parsed.extractionId()));
        var fresh=service.parse(owner,"JD");assertNotEquals(parsed.extractionId(),fresh.extractionId());verify(llm,times(2)).extract("JD");
    }
    @Test void failedCallsAreNotCachedOrSilentlyReplacedByRules() {
        var service=service();UUID owner=UUID.randomUUID();when(llm.extract("JD")).thenThrow(new IllegalStateException("failed")).thenReturn(output);
        assertThrows(IllegalStateException.class,()->service.parse(owner,"JD"));assertNotNull(service.parse(owner,"JD"));verify(llm,times(2)).extract("JD");verifyNoInteractions(rules);
    }
    @Test void disabledLlmUsesLocalParser() {
        var service=service();when(llm.enabled()).thenReturn(false);when(rules.parse("JD")).thenReturn(output);
        assertEquals(output,service.parse(UUID.randomUUID(),"JD"));assertEquals(output,service.forSave(UUID.randomUUID(),"JD",null));verify(llm,never()).extract(anyString());
    }
    @Test void duplicateConcurrentRequestsShareOneExtraction() throws Exception {
        var service=service();UUID owner=UUID.randomUUID();var started=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(llm.extract("JD")).thenAnswer(call->{started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return output;});
        try(var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(()->service.parse(owner,"JD"));assertTrue(started.await(2,TimeUnit.SECONDS));
            var second=executor.submit(()->service.parse(owner,"JD"));release.countDown();assertSame(first.get(3,TimeUnit.SECONDS),second.get(3,TimeUnit.SECONDS));verify(llm,times(1)).extract("JD");
        }finally{release.countDown();}
    }
}
