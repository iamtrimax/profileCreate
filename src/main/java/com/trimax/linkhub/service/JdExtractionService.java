package com.trimax.linkhub.service;

import com.trimax.linkhub.api.RecruitmentModels.ParsedJd;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

@Service @RequiredArgsConstructor
public class JdExtractionService {
    private final JdParser rules;
    private final GroqJdExtractor llm;
    private final Clock clock;
    private record Key(UUID owner,String description) {}
    private record Entry(Instant expires,String id,CompletableFuture<ParsedJd> result) {}
    private final Map<Key,Entry> cache=new LinkedHashMap<>();
    public ParsedJd parse(UUID owner,String description) {
        if(!llm.enabled())return formatted(rules.parse(description));
        Key key=new Key(owner,description);Entry entry;boolean creator=false;
        synchronized(cache){
            cache.entrySet().removeIf(item->item.getValue().result().isDone()&&!item.getValue().expires().isAfter(clock.instant()));
            entry=cache.get(key);
            if(entry==null){
                if(cache.size()>=128){var oldest=cache.entrySet().stream().filter(item->item.getValue().result().isDone()).findFirst();if(oldest.isPresent())cache.remove(oldest.get().getKey());else throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Phân tích JD đang bận. Vui lòng thử lại.");}
                entry=new Entry(clock.instant().plusSeconds(900),UUID.randomUUID().toString(),new CompletableFuture<>());cache.put(key,entry);creator=true;
            }
        }
        if(creator){try{ParsedJd parsed=formatted(llm.extract(description));entry.result().complete(new ParsedJd(parsed.data(),parsed.missingFields(),parsed.source(),parsed.warnings(),entry.id(),parsed.formattedDescription()));}catch(RuntimeException error){entry.result().completeExceptionally(error);synchronized(cache){cache.remove(key,entry);}}}
        try{return entry.result().join();}catch(CompletionException error){if(error.getCause() instanceof RuntimeException cause)throw cause;throw error;}
    }
    private static ParsedJd formatted(ParsedJd parsed) {
        return new ParsedJd(parsed.data(),parsed.missingFields(),parsed.source(),parsed.warnings(),parsed.extractionId(),JdFormatter.format(parsed.data().description()));
    }
    public ParsedJd forSave(UUID owner,String description,String extractionId) {
        if(!llm.enabled())return rules.parse(description);
        synchronized(cache){Entry entry=cache.get(new Key(owner,description));
            if(entry==null||!entry.id().equals(extractionId)||!entry.expires().isAfter(clock.instant())||!entry.result().isDone()||entry.result().isCompletedExceptionally())
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Bản phân tích JD đã hết hạn hoặc không khớp. Hãy phân tích lại trước khi đăng.");
            return entry.result().join();
        }
    }
}
