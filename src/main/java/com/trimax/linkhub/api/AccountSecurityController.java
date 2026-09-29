package com.trimax.linkhub.api;

import com.trimax.linkhub.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api") @RequiredArgsConstructor
public class AccountSecurityController {
    private final AccountSecurityService security;private final RateLimiter limiter;
    public record Email(@NotBlank @jakarta.validation.constraints.Email @Size(max=254) String email) {}
    public record Token(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token){@Override public String toString(){return "Token[redacted]";}}
    public record Reset(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{43}") String token,@NotBlank @Size(min=10,max=64) String password){@Override public String toString(){return "Reset[redacted]";}}
    @PostMapping("/auth/password-reset/request") public Map<String,String> request(@Valid @RequestBody Email input,HttpServletRequest request) {
        limit("reset-ip:"+request.getRemoteAddr(),10);limit("reset-email:"+input.email().strip().toLowerCase(Locale.ROOT),5);security.requestReset(input.email());
        return Map.of("message","Nếu email đã đăng ký, hướng dẫn đặt lại mật khẩu sẽ được gửi đến bạn.");
    }
    @PostMapping("/auth/password-reset/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody Reset input,HttpServletRequest request){limit("token:"+request.getRemoteAddr(),20);security.reset(input.token(),input.password());}
    @PostMapping("/auth/email-verification/confirm") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verify(@Valid @RequestBody Token input,HttpServletRequest request){limit("token:"+request.getRemoteAddr(),20);security.verify(input.token());}
    @PostMapping("/me/email-verification") public Map<String,String> verification(Authentication auth){limit("verify:"+auth.getName(),5);security.requestVerification(UUID.fromString(auth.getName()));return Map.of("message","Hướng dẫn xác minh đã được đưa vào hàng đợi email.");}
    private void limit(String key,int count){if(!limiter.allow("security:"+AccountSecurityService.hash(key),count,3600))throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Vui lòng thử lại sau.");}
}
