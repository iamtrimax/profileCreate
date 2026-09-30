package com.trimax.linkhub.api;

import com.trimax.linkhub.service.AuditService;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.UUID;

@RestController @RequestMapping("/api/ops/audit-logs") @RequiredArgsConstructor
public class AuditController {
    private final AuditService audit;private final Clock clock;
    @GetMapping public ResponseEntity<AuditService.Page> list(
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(required=false) @Pattern(regexp="[A-Z_]{1,60}") String action,
        @RequestParam(required=false) UUID actorId,@RequestParam(required=false) AuditService.Outcome outcome,
        @RequestParam(required=false) @Pattern(regexp="[a-zA-Z0-9-]{1,64}") String requestId,
        @RequestParam(required=false) @Min(1) Long before,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){
        Instant end=to==null?clock.instant():to,start=from==null?end.minus(Duration.ofDays(7)):from;
        if(start.isAfter(end)||Duration.between(start,end).compareTo(Duration.ofDays(90))>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Chọn khoảng thời gian hợp lệ, tối đa 90 ngày.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(audit.find(start,end,action,actorId,outcome,requestId,before,size));
    }
}
