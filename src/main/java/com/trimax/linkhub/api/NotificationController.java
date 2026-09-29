package com.trimax.linkhub.api;

import com.trimax.linkhub.service.*;
import jakarta.servlet.http.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.UUID;

@RestController @RequestMapping("/api/me/notifications") @RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notifications;
    private final NotificationStream streams;
    private final AccountSecurityService accounts;
    private final ObjectProvider<SessionRepository<?>> sessions;
    @GetMapping public NotificationService.Inbox inbox(Authentication auth,@RequestParam(defaultValue="0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue="false") boolean unreadOnly) {return notifications.inbox(UUID.fromString(auth.getName()),page,unreadOnly);}
    @PatchMapping("/{id}/read") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(Authentication auth,@PathVariable UUID id){notifications.read(UUID.fromString(auth.getName()),id);}
    @PatchMapping("/read-all") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(Authentication auth){notifications.readAll(UUID.fromString(auth.getName()));}
    @GetMapping(value="/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(Authentication auth,HttpServletRequest request) {
        HttpSession servletSession=request.getSession(false);
        String sessionId=servletSession.getId(),principal=auth.getName();
        Object storedVersion=servletSession.getAttribute("AUTH_VERSION");
        long version=storedVersion instanceof Number n?n.longValue():0;
        var emitter=streams.connect(UUID.fromString(principal),()->{
            try {
                if(accounts.version(UUID.fromString(principal))!=version)return false;
                SecurityContext context;
                var repository=sessions.getIfAvailable();
                if(repository!=null){var session=repository.findById(sessionId);if(session==null)return false;context=session.getAttribute("SPRING_SECURITY_CONTEXT");}
                else context=(SecurityContext)servletSession.getAttribute("SPRING_SECURITY_CONTEXT");
                return context!=null && context.getAuthentication()!=null && context.getAuthentication().isAuthenticated() && principal.equals(context.getAuthentication().getName());
            }catch(RuntimeException e){return false;}
        });
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"no-cache, no-store").header("X-Accel-Buffering","no").body(emitter);
    }
}
