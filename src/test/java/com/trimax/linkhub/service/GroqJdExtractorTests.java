package com.trimax.linkhub.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroqJdExtractorTests {
    private final JsonMapper json=JsonMapper.builder().build();
    private static final String JD="LinkHub cần Java Developer tại Hà Nội, làm việc Hybrid. Cần 2 năm kinh nghiệm Java và Spring Boot. Ưu tiên Docker. Lương 20–30 triệu VND/tháng.";
    private Map<String,Object> fact(Object value,String evidence){Map<String,Object> fact=new HashMap<>();fact.put("value",value);fact.put("evidence",evidence);return fact;}
    private ObjectNode draft() {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("role",fact("Java Developer","Java Developer"));fields.put("company",fact("LinkHub","LinkHub"));
        fields.put("location",fact("Hà Nội","Hà Nội"));fields.put("workMode",fact("HYBRID","Hybrid"));
        fields.put("currency",fact("VND","VND/tháng"));fields.put("minExperience",fact(2,"2 năm kinh nghiệm"));
        fields.put("salaryMin",fact(20000000,"20–30 triệu VND/tháng"));fields.put("salaryMax",fact(30000000,"20–30 triệu VND/tháng"));
        fields.put("salaryNegotiable",fact(null,null));
        fields.put("requiredSkills",List.of(Map.of("name","Java","evidence","Java và Spring Boot"),Map.of("name","Spring Boot","evidence","Java và Spring Boot")));
        fields.put("preferredSkills",List.of(Map.of("name","Docker","evidence","Ưu tiên Docker")));
        return (ObjectNode)json.valueToTree(fields);
    }
    @Test void validatesProseExtractionWithoutRequiringFieldLabels() {
        var parsed=GroqJdExtractor.validated(draft(),JD);
        assertTrue(parsed.missingFields().isEmpty());assertEquals("groq",parsed.source());assertEquals(JD,parsed.data().description());
        assertEquals(List.of("java","spring boot"),parsed.data().requiredSkills());assertEquals(List.of("docker"),parsed.data().preferredSkills());
        assertEquals(20000000,parsed.data().salaryMin());assertEquals(2,parsed.data().minExperience());
    }
    @Test void rejectsInventedEvidenceAndSkillNames() {
        ObjectNode invented=draft();invented.set("company",json.valueToTree(fact("Other company","Other company")));
        assertThrows(ResponseStatusException.class,()->GroqJdExtractor.validated(invented,JD));
        ObjectNode skill=draft();skill.set("requiredSkills",json.valueToTree(List.of(Map.of("name","Kubernetes","evidence","Java và Spring Boot"))));
        assertThrows(ResponseStatusException.class,()->GroqJdExtractor.validated(skill,JD));
    }
    @Test void missingFieldsStayMissingAndForeignSalaryDoesNotBecomeVnd() {
        ObjectNode missing=draft();missing.set("company",json.valueToTree(fact(null,null)));missing.set("minExperience",json.valueToTree(fact(null,null)));
        var parsed=GroqJdExtractor.validated(missing,JD);assertTrue(parsed.missingFields().contains("công ty"));assertFalse(parsed.warnings().isEmpty());
        ObjectNode usd=draft();usd.set("currency",json.valueToTree(fact("USD","USD")));
        assertFalse(GroqJdExtractor.validated(usd,JD+" USD").missingFields().isEmpty());
    }
    @Test void rejectsInvalidTypesEnumsAndOutOfRangeYears() {
        ObjectNode invalid=draft();invalid.set("minExperience",json.valueToTree(fact(70,"2 năm kinh nghiệm")));
        assertThrows(ResponseStatusException.class,()->GroqJdExtractor.validated(invalid,JD));
        invalid.set("minExperience",json.valueToTree(fact("2","2 năm kinh nghiệm")));
        assertThrows(ResponseStatusException.class,()->GroqJdExtractor.validated(invalid,JD));
        ObjectNode mode=draft();mode.set("workMode",json.valueToTree(fact("ANY","Hybrid")));
        assertThrows(ResponseStatusException.class,()->GroqJdExtractor.validated(mode,JD));
    }
    @Test void explicitNegotiableSalaryUsesUnrestrictedRange() {
        ObjectNode result=draft();result.set("salaryNegotiable",json.valueToTree(fact(true,"Lương thỏa thuận")));
        var parsed=GroqJdExtractor.validated(result,JD+" Lương thỏa thuận");assertEquals(0,parsed.data().salaryMin());assertEquals(999999999999L,parsed.data().salaryMax());
    }
    @SuppressWarnings("unchecked")
    @Test void sendsGroqStrictSchemaAndDoesNotExposeProviderSecrets() throws Exception {
        HttpClient http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("content",json.writeValueAsString(draft())))))));
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new GroqJdExtractor(json,http,true,"test-key","test-model");assertTrue(client.extract(JD).missingFields().isEmpty());
        var captor=ArgumentCaptor.forClass(HttpRequest.class);verify(http).send(captor.capture(),any(HttpResponse.BodyHandler.class));
        HttpRequest request=captor.getValue();assertEquals("https://api.groq.com/openai/v1/chat/completions",request.uri().toString());assertEquals("Bearer test-key",request.headers().firstValue("Authorization").orElseThrow());assertTrue(request.timeout().isPresent());
        var body=json.readTree(body(request));assertFalse(body.has("store"));assertTrue(body.path("response_format").path("json_schema").path("strict").asBoolean());assertEquals(3500,body.path("max_completion_tokens").asInt());assertEquals("low",body.path("reasoning_effort").asText());assertEquals(json.valueToTree(GroqJdExtractor.schema()),body.path("response_format").path("json_schema").path("schema"));
        assertEquals(JD,body.path("messages").get(1).path("content").asText());assertFalse(body.toString().contains("test-key"));
        when(response.statusCode()).thenReturn(401);when(response.body()).thenReturn("private provider detail test-key");
        var error=assertThrows(ResponseStatusException.class,()->client.extract(JD));assertFalse(error.getReason().contains("test-key"));
    }
    @SuppressWarnings("unchecked")
    @Test void refusesIncompleteRefusalAndMalformedResponses() throws Exception {
        HttpClient http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);when(response.statusCode()).thenReturn(200);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        var client=new GroqJdExtractor(json,http,true,"test-key","test-model");
        for(String body:List.of(
            json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","length","message",Map.of("content",json.writeValueAsString(draft())))))),
            json.writeValueAsString(Map.of("choices",List.of(Map.of("finish_reason","stop","message",Map.of("refusal","refused","content",json.writeValueAsString(draft())))))),
            "{\"choices\":[]}", "{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":null}}]}", "not json")){
            when(response.body()).thenReturn(body);assertThrows(ResponseStatusException.class,()->client.extract(JD));
        }
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenThrow(new HttpTimeoutException("timeout"));
        assertThrows(ResponseStatusException.class,()->client.extract(JD));
    }
    @Test void missingApiKeyNeverCallsHttp() {
        HttpClient http=mock(HttpClient.class);var client=new GroqJdExtractor(json,http,true,"","test-model");
        assertThrows(ResponseStatusException.class,()->client.extract(JD));verifyNoInteractions(http);
    }
    @SuppressWarnings("unchecked")
    @Test void reportsActionableProviderErrorsWithoutLeakingProviderMessages() throws Exception {
        HttpClient http=mock(HttpClient.class);HttpResponse<String> response=mock(HttpResponse.class);
        when(http.send(any(HttpRequest.class),any(HttpResponse.BodyHandler.class))).thenReturn(response);
        when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(Map.of("x-request-id",List.of("req_example"),"retry-after",List.of("45")),(k,v)->true));
        var client=new GroqJdExtractor(json,http,true,"secret-key","test-model");
        record Case(int status,String providerCode,String expected,boolean retryable) {}
        for(var item:List.of(new Case(401,"invalid_api_key","GROQ_AUTH_FAILED",false),
            new Case(403,"permission_denied","GROQ_ACCESS_DENIED",false),
            new Case(429,"rate_limit_exceeded","GROQ_RATE_LIMITED",true),
            new Case(404,"model_not_found","GROQ_MODEL_UNAVAILABLE",false),
            new Case(400,"invalid_json_schema","GROQ_INVALID_REQUEST",false),
            new Case(503,"server_is_overloaded","GROQ_UPSTREAM_ERROR",true))) {
            when(response.statusCode()).thenReturn(item.status());
            when(response.body()).thenReturn(json.writeValueAsString(Map.of("error",Map.of("code",item.providerCode(),"message","secret-key "+JD))));
            var error=assertThrows(GroqRequestException.class,()->client.extract(JD));
            assertEquals(item.expected(),error.code());assertEquals(item.retryable(),error.retryable());
            assertEquals(item.status(),error.providerStatus());assertEquals("req_example",error.providerRequestId());
            assertFalse(error.getReason().contains("secret-key"));assertFalse(error.getReason().contains(JD));
            if(item.retryable())assertEquals(45,error.retryAfterSeconds());else assertNull(error.retryAfterSeconds());
        }
        when(response.statusCode()).thenReturn(429);when(response.body()).thenReturn("{\"error\":{\"type\":\"insufficient_quota\"}}");
        assertEquals("GROQ_RATE_LIMITED",assertThrows(GroqRequestException.class,()->client.extract(JD)).code());
        when(response.statusCode()).thenReturn(502);when(response.body()).thenReturn("<html>secret-key</html>");
        assertEquals("GROQ_UPSTREAM_ERROR",assertThrows(GroqRequestException.class,()->client.extract(JD)).code());
    }
    private String body(HttpRequest request) throws Exception {
        var complete=new CompletableFuture<String>();var text=new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription){subscription.request(Long.MAX_VALUE);}
            public void onNext(ByteBuffer bytes){text.append(StandardCharsets.UTF_8.decode(bytes));}
            public void onError(Throwable error){complete.completeExceptionally(error);}
            public void onComplete(){complete.complete(text.toString());}
        });return complete.get(2,TimeUnit.SECONDS);
    }
}
