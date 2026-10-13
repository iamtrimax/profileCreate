package com.trimax.linkhub.api;

import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.*;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.*;

/** Localize response envelopes, never authored DTO fields such as chat messages. */
@RestControllerAdvice
public class ApiResponseLanguage implements ResponseBodyAdvice<Object> {
    private final ApiMessages messages;
    public ApiResponseLanguage(ApiMessages messages) { this.messages = messages; }

    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) { return true; }

    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType type,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Map<?, ?> envelope) || !(envelope.get("message") instanceof String message)
                || !(request instanceof ServletServerHttpRequest servlet)) return body;
        String language = messages.language(servlet.getServletRequest());
        response.getHeaders().setContentLanguage(Locale.forLanguageTag(language));
        response.getHeaders().add(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        var translated = new LinkedHashMap<Object, Object>(envelope);
        translated.put("message", messages.text(message, language));
        if (envelope.get("errors") instanceof List<?> errors) {
            translated.put("errors", errors.stream().map(error -> {
                if (!(error instanceof Map<?, ?> detail) || !(detail.get("message") instanceof String text)) return error;
                var fields = new LinkedHashMap<Object, Object>(detail);
                fields.put("message", messages.text(text, language));
                return fields;
            }).toList());
        }
        return translated;
    }
}
