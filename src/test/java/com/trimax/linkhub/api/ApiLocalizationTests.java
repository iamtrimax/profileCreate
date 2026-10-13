package com.trimax.linkhub.api;

import com.trimax.linkhub.config.LanguageConfig;
import com.trimax.linkhub.service.GroqRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiLocalizationTests {
    private ApiMessages messages;
    private MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        messages = new ApiMessages(JsonMapper.builder().build());
        mvc = MockMvcBuilders.standaloneSetup(new TestEndpoints())
                .setControllerAdvice(new ApiErrors(), new ApiResponseLanguage(messages))
                .setLocaleResolver(new LanguageConfig().localeResolver()).build();
    }

    @Test void translatesAuthenticationAndPreservesVietnameseDefault() throws Exception {
        mvc.perform(get("/auth-error").header("Accept-Language", "en-US,en;q=0.9,vi;q=0.5"))
                .andExpect(status().isUnauthorized()).andExpect(header().string("Content-Language", "en"))
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        mvc.perform(get("/auth-error")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng."));
        mvc.perform(get("/auth-error").header("Accept-Language", "vi-VN,en;q=0.8"))
                .andExpect(jsonPath("$.message").value("Email hoặc mật khẩu không đúng."));
    }

    @Test void translatesParameterizedJdErrors() throws Exception {
        mvc.perform(get("/jd-error").header("Accept-Language", "en"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add to the job description: company, location"));
    }

    @Test void preservesGroqCodeRetryFlagAndRetryAfter() throws Exception {
        mvc.perform(get("/groq-error").header("Accept-Language", "en"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.code").value("GROQ_RATE_LIMITED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.retryAfterSeconds").value(7))
                .andExpect(jsonPath("$.message").value("Groq's request or token limit has been reached. Please wait and analyze again."));
    }

    @Test void translatesValidationEnvelopeAndFieldErrors() throws Exception {
        mvc.perform(post("/validate").header("Accept-Language", "en").contentType("application/json")
                        .content("{\"email\":\"invalid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please check your input."))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[0].message").value("must be a well-formed email address"));
    }

    @Test void localizesSecurityErrorsBeforeMvc() throws Exception {
        var request = new MockHttpServletRequest(); request.addHeader("Accept-Language", "en");
        var response = new MockHttpServletResponse();
        messages.writeSecurityError(request, response, 403, "Không có quyền truy cập hoặc mã CSRF không hợp lệ.", "ACCESS_DENIED");
        assertEquals(403, response.getStatus());
        assertEquals("en", response.getHeader("Content-Language"));
        assertEquals("Access denied or invalid CSRF token.", JsonMapper.builder().build().readTree(response.getContentAsString()).path("message").asText());
        var anonymous = new MockHttpServletResponse();
        messages.writeSecurityError(new MockHttpServletRequest(), anonymous, 401, "Cần đăng nhập để tiếp tục.", "AUTHENTICATION_REQUIRED");
        assertEquals("vi", anonymous.getHeader("Content-Language"));
    }

    @Test void neverTranslatesAuthoredDtoContent() throws Exception {
        mvc.perform(get("/authored").header("Accept-Language", "en"))
                .andExpect(jsonPath("$.message").value("Đăng nhập"))
                .andExpect(jsonPath("$.title").value("Tuyển dụng"));
    }

    @Test void unknownFailuresHaveALocalizedSafeMessage() throws Exception {
        mvc.perform(get("/unexpected").header("Accept-Language", "en"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("The server encountered an error. Please try again later."));
    }

    @RestController static class TestEndpoints {
        record Input(@NotBlank @Email String email) {}
        record Authored(String title, String message) {}
        @GetMapping("/auth-error") Object auth() { throw new BadCredentialsException("private detail"); }
        @GetMapping("/jd-error") Object jd() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bổ sung vào JD: công ty, địa điểm"); }
        @GetMapping("/groq-error") Object groq() { throw new GroqRequestException(HttpStatus.TOO_MANY_REQUESTS,"GROQ_RATE_LIMITED","Groq đã chạm hạn mức yêu cầu hoặc token. Vui lòng chờ rồi phân tích lại.",429,"req_example",true,7); }
        @PostMapping("/validate") Object validate(@Valid @RequestBody Input input) { return Map.of("ok", true); }
        @GetMapping("/authored") Authored authored() { return new Authored("Tuyển dụng", "Đăng nhập"); }
        @GetMapping("/unexpected") Object unexpected() { throw new IllegalStateException("private server detail"); }
    }
}
