package com.trimax.linkhub.api;

import com.trimax.linkhub.api.RecruitmentModels.*;
import com.trimax.linkhub.service.RecruitmentService;
import com.trimax.linkhub.service.RateLimiter;
import com.trimax.linkhub.service.JdExtractionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/me/recruitment") @RequiredArgsConstructor
public class RecruitmentController {
    private final RecruitmentService service;
    private final RateLimiter limiter;
    private final JdExtractionService jdParser;
    private UUID owner(Authentication auth) {return UUID.fromString(auth.getName());}
    private void throttle(UUID owner,String action) {
        int limit=action.equals("parse-jd")||action.equals("from-jd")?12:60;
        if(!limiter.allow("recruitment:"+action+":"+owner,limit,60))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
    }
    @GetMapping("/candidate") public ResponseEntity<Candidate> candidate(Authentication auth) {
        Candidate candidate=service.candidate(owner(auth));
        return candidate==null?ResponseEntity.noContent().build():ResponseEntity.ok(candidate);
    }
    @PutMapping("/candidate") public Candidate candidate(Authentication auth,@Valid @RequestBody CandidateInput input) {return service.saveCandidate(owner(auth),input);}
    @GetMapping("/jobs") public List<Job> jobs(Authentication auth) {return service.jobs(owner(auth));}
    @PostMapping("/jobs/parse-jd") public ParsedJd parseJd(Authentication auth,@Valid @RequestBody JdInput input) {
        throttle(owner(auth),"parse-jd");return jdParser.parse(owner(auth),input.description());
    }
    private JobInput parsed(UUID owner,JdInput input) {
        ParsedJd result=jdParser.forSave(owner,input.description(),input.extractionId());
        if(!result.missingFields().isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Bổ sung vào JD: "+String.join(", ",result.missingFields()));
        return result.data();
    }
    @PostMapping("/jobs/from-jd") @ResponseStatus(HttpStatus.CREATED) public Job createFromJd(Authentication auth,@Valid @RequestBody JdInput input) {
        throttle(owner(auth),"from-jd");return service.saveJob(owner(auth),null,parsed(owner(auth),input));
    }
    @PutMapping("/jobs/{id}/from-jd") public Job editFromJd(Authentication auth,@PathVariable UUID id,@Valid @RequestBody JdInput input) {
        throttle(owner(auth),"from-jd");return service.saveJob(owner(auth),id,parsed(owner(auth),input));
    }
    @GetMapping("/feed") public List<Job> feed(@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page) {return service.feed(page);}
    @PostMapping("/jobs") @ResponseStatus(HttpStatus.CREATED) public Job create(Authentication auth,@Valid @RequestBody JobInput input) {return service.saveJob(owner(auth),null,input);}
    @PutMapping("/jobs/{id}") public Job edit(Authentication auth,@PathVariable UUID id,@Valid @RequestBody JobInput input) {return service.saveJob(owner(auth),id,input);}
    @PatchMapping("/jobs/{id}") public Job open(Authentication auth,@PathVariable UUID id,@Valid @RequestBody OpenInput input) {return service.setOpen(owner(auth),id,input.open());}
    @PostMapping("/jobs/{id}/match") public Map<String,Suggestion> discover(Authentication auth,@PathVariable UUID id) {
        throttle(owner(auth),"discover");var result=new HashMap<String,Suggestion>();result.put("suggestion",service.discover(owner(auth),id,null));return result;
    }
    @PostMapping("/jobs/{id}/next/{matchId}") public Map<String,Suggestion> next(Authentication auth,@PathVariable UUID id,@PathVariable UUID matchId) {
        throttle(owner(auth),"discover");var result=new HashMap<String,Suggestion>();result.put("suggestion",service.discover(owner(auth),id,matchId));return result;
    }
    @GetMapping("/matches") public List<Match> matches(Authentication auth) {return service.matches(owner(auth));}
    @PostMapping("/matches/{id}/invite") public Match invite(Authentication auth,@PathVariable UUID id) {throttle(owner(auth),"invite");return service.invite(owner(auth),id);}
    @PatchMapping("/matches/{id}") public Match reply(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Reply input) {return service.reply(owner(auth),id,input.status());}
    @GetMapping("/matches/{id}/messages") public List<Message> messages(Authentication auth,@PathVariable UUID id) {return service.messages(owner(auth),id);}
    @PostMapping("/matches/{id}/messages") @ResponseStatus(HttpStatus.NO_CONTENT) public void send(Authentication auth,@PathVariable UUID id,@Valid @RequestBody MessageInput input) {throttle(owner(auth),"message");service.send(owner(auth),id,input.body());}
    @GetMapping("/matches/{id}/interviews") public List<Interview> interviews(Authentication auth,@PathVariable UUID id) {return service.interviews(owner(auth),id);}
    @PostMapping("/matches/{id}/interviews") @ResponseStatus(HttpStatus.CREATED) public Interview propose(Authentication auth,@PathVariable UUID id,@Valid @RequestBody InterviewInput input) {return service.propose(owner(auth),id,input);}
    @PatchMapping("/matches/{matchId}/interviews/{id}") public Interview interviewReply(Authentication auth,@PathVariable UUID matchId,@PathVariable UUID id,@Valid @RequestBody InterviewReply input) {return service.interviewReply(owner(auth),matchId,id,input.status());}
}
