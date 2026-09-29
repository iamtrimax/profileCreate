package com.trimax.linkhub.api;

import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestControllerAdvice
public class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", e.getReason() == null ? "Yêu cầu không hợp lệ." : e.getReason())); }
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<?> authentication(AuthenticationException e) { return error(401, "Email hoặc mật khẩu không đúng."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException e) {
        for (Throwable cause=e; cause!=null; cause=cause.getCause()) {
            if(cause instanceof java.sql.SQLException sql && "23P01".equals(sql.getSQLState()))
                return ResponseEntity.status(409).body(Map.of("code","BOOKING_OVERLAP","message","Khung giờ vừa được đặt. Vui lòng chọn thời gian khác."));
        }
        return error(409, "Dữ liệu bị trùng hoặc không hợp lệ.");
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng kiểm tra dữ liệu nhập.", "errors",
                e.getBindingResult().getFieldErrors().stream().map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage()))).toList()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, HandlerMethodValidationException.class})
    ResponseEntity<?> invalid(Exception e) { return error(400, "Dữ liệu hoặc tham số không hợp lệ."); }
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ResponseEntity<?> unavailable(Exception e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(Map.of("message", "Dịch vụ dữ liệu đang bận hoặc mất kết nối. Vui lòng thử lại sau ít giây."));
    }
    private ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }
}
