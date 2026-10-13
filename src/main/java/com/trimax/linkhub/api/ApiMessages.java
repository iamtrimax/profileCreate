package com.trimax.linkhub.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** The API and browser share the same declared messages and parameter templates. */
@Component
public class ApiMessages {
    private final ObjectMapper json;
    private final Map<String, String> english = new LinkedHashMap<>();
    private final List<MessagePattern> patterns = new ArrayList<>();
    private record MessagePattern(String source, String target, Pattern regex, List<Integer> indexes) {}

    public ApiMessages(ObjectMapper json) throws IOException {
        this.json = json;
        try (var input = new ClassPathResource("static/assets/i18n-en.json").getInputStream()) {
            var values = json.readValue(input, Map.class);
            values.forEach((key, value) -> english.put(normalize(key.toString()), value.toString()));
        }
        english.forEach((source, target) -> {
            var placeholders = Pattern.compile("\\{(\\d+)\\}").matcher(source);
            var regex = new StringBuilder("^");
            var indexes = new ArrayList<Integer>();
            int cursor = 0;
            while (placeholders.find()) {
                regex.append(Pattern.quote(source.substring(cursor, placeholders.start()))).append("(.*?)");
                indexes.add(Integer.parseInt(placeholders.group(1)));
                cursor = placeholders.end();
            }
            if (!indexes.isEmpty()) {
                regex.append(Pattern.quote(source.substring(cursor))).append('$');
                patterns.add(new MessagePattern(source, target, Pattern.compile(regex.toString()), List.copyOf(indexes)));
            }
        });
        patterns.sort(Comparator.comparingInt((MessagePattern p) -> p.source().replaceAll("\\{\\d+\\}", "").length()).reversed());
    }

    public String language(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.ACCEPT_LANGUAGE) == null || request.getHeader(HttpHeaders.ACCEPT_LANGUAGE).isBlank()) return "vi";
        var locales = request.getLocales();
        while (locales.hasMoreElements()) {
            String language = locales.nextElement().getLanguage();
            if (language.equals("en") || language.equals("vi")) return language;
        }
        return "vi";
    }

    public String text(String source, String language) {
        if (source == null || !"en".equals(language)) return source;
        return translate(source, 0);
    }

    private String translate(String source, int depth) {
        String normalized = normalize(source);
        if (english.containsKey(normalized)) return english.get(normalized);
        for (var pattern : patterns) {
            var match = pattern.regex().matcher(normalized);
            if (!match.matches()) continue;
            var values = new HashMap<Integer, String>();
            for (int i = 0; i < pattern.indexes().size(); i++) values.put(pattern.indexes().get(i), match.group(i + 1));
            if (depth < 3 && (pattern.source().startsWith("Bổ sung vào") || pattern.source().startsWith("{0} (tối đa"))) {
                var details = values.get(0);
                var parts = Pattern.compile("[,;] ?").matcher(details);
                var translated = new StringBuilder();
                int cursor = 0;
                while (parts.find()) {
                    translated.append(translate(details.substring(cursor, parts.start()), depth + 1)).append(parts.group());
                    cursor = parts.end();
                }
                translated.append(translate(details.substring(cursor), depth + 1));
                values.put(0, translated.toString());
            }
            var placeholders = Pattern.compile("\\{(\\d+)\\}").matcher(pattern.target());
            var result = new StringBuilder();
            while (placeholders.find()) placeholders.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(values.get(Integer.parseInt(placeholders.group(1)))));
            placeholders.appendTail(result);
            return result.toString();
        }
        return source;
    }

    public void writeSecurityError(HttpServletRequest request, HttpServletResponse response, int status, String message, String code) throws IOException {
        String language = language(request);
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, language);
        response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
        response.getWriter().write(json.writeValueAsString(Map.of("message", text(message, language), "code", code)));
    }

    private static String normalize(String text) { return text.strip().replaceAll("\\s+", " "); }
}
