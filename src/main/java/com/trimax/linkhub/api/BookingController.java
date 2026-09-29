package com.trimax.linkhub.api;

import com.trimax.linkhub.api.BookingModels.*;
import com.trimax.linkhub.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class BookingController {
    private final BookingService service;
    private final RateLimiter limiter;
    @GetMapping("/me/availability") public Schedule schedule(Authentication auth) {return service.schedule(UUID.fromString(auth.getName()));}
    @PutMapping("/me/availability") public Schedule save(Authentication auth,@Valid @RequestBody Schedule input) {return service.saveSchedule(UUID.fromString(auth.getName()),input);}
    @GetMapping("/me/bookings") public List<Booking> bookings(Authentication auth,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page) {return service.bookings(UUID.fromString(auth.getName()),page);}
    @PatchMapping("/me/bookings/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(Authentication auth,@PathVariable UUID id,@Valid @RequestBody Change input) {service.change(UUID.fromString(auth.getName()),id,input.status());}
    @GetMapping("/public/{username}/slots")
    public Slots slots(@PathVariable String username,@RequestParam UUID serviceId,@RequestParam LocalDate date,HttpServletRequest request) {
        throttle(request,"slots",120,60);return service.slots(username,serviceId,date);
    }
    @PostMapping("/public/{username}/bookings") @ResponseStatus(HttpStatus.CREATED)
    public Receipt create(@PathVariable String username,@Valid @RequestBody Create input,HttpServletRequest request) {
        throttle(request,"booking",5,600);
        // Each invocation crosses the service transaction proxy; a failed transaction
        // has rolled back before retrying. Never retry inside an aborted transaction.
        for(int attempt=0;attempt<3;attempt++) {
            try {return service.create(username,input);}
            catch(org.springframework.dao.PessimisticLockingFailureException e) {
                if(attempt==2)throw new ResponseStatusException(HttpStatus.CONFLICT,"Lịch đang được cập nhật. Vui lòng tải lại giờ trống và thử lại.");
            }
        }
        throw new IllegalStateException("Unreachable");
    }
    private void throttle(HttpServletRequest request,String action,int limit,int seconds) {
        try {
            String key=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8)));
            if(!limiter.allow(action+":"+key,limit,seconds))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
