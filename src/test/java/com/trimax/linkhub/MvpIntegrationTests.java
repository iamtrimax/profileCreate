package com.trimax.linkhub;

import com.trimax.linkhub.service.RateLimiter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class MvpIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean RateLimiter limiter;
    @BeforeEach void allowRequests() { when(limiter.allow(anyString(), anyInt(), anyInt())).thenReturn(true); }

    @Test void completeMvpFlowAndOwnershipIsolation() throws Exception {
        String username = randomUsername();
        MockHttpSession owner = registerLogin(username);
        MockHttpSession other = registerLogin(username + "b");
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/" + username)).andExpect(status().isNotFound());
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        mvc.perform(get("/" + username)).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/profile").session(owner).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Trí\",\"bio\":\"Designer\",\"published\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"Trí\",\"bio\":\"Designer\",\"published\":true}"))
                .andExpect(status().isOk());
        String link = body(mvc.perform(post("/api/me/links").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Portfolio\",\"url\":\"https://example.com\",\"sortOrder\":1}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String offering = body(mvc.perform(post("/api/me/services").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Thiết kế logo\",\"description\":\"Gói cơ bản\",\"price\":500000}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/public/" + username)).andExpect(status().isOk()).andExpect(jsonPath("$.links[0].title").value("Portfolio"))
                .andExpect(jsonPath("$.services[0].currency").value("VND")).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/" + username)).andExpect(status().isOk());
        byte[] qr = mvc.perform(get("/api/public/" + username + "/qr")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn().getResponse().getContentAsByteArray();
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(qr));
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));
        assertEquals("http://localhost:8080/" + username, new com.google.zxing.MultiFormatReader().decode(bitmap).getText());
        String contact = body(mvc.perform(post("/api/public/" + username + "/contacts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Khách\",\"email\":\"guest@example.com\",\"message\":\"Tôi muốn đặt dịch vụ\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/public/" + username + "/visits").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/analytics?days=7").session(owner)).andExpect(status().isOk()).andExpect(jsonPath("$.totalViews").value(3))
                .andExpect(jsonPath("$.totalContacts").value(1)).andExpect(jsonPath("$.newContacts").value(1)).andExpect(jsonPath("$.daily.length()").value(7));
        mvc.perform(get("/api/me/contacts").session(other)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(delete("/api/me/links/" + link).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/links/" + link).session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hijack\",\"url\":\"https://example.com\",\"sortOrder\":0}")).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/services/" + offering).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/contacts/" + contact).session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}")).andExpect(status().isNotFound());
        mvc.perform(patch("/api/me/contacts/" + contact).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READ\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/me/analytics").session(owner)).andExpect(jsonPath("$.newContacts").value(0));
        mvc.perform(delete("/api/me/links/" + link).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/me/services/" + offering).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/logout").session(owner).with(csrf())).andExpect(status().isNoContent());
        assertTrue(owner.isInvalid());
    }

    @Test void validationConflictsAndRateLimits() throws Exception {
        String username = randomUsername();
        MockHttpSession session = registerLogin(username);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registration(username.toUpperCase(Locale.ROOT))))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registration("dashboard"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + username + "@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/links").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"XSS\",\"url\":\"javascript:alert(1)\",\"sortOrder\":0}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/services").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Invalid\",\"description\":\"\",\"price\":-1}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/analytics?days=0").session(session)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/contacts?page=-1").session(session)).andExpect(status().isBadRequest());
        when(limiter.allow(anyString(), anyInt(), anyInt())).thenReturn(false);
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"test@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test void realCsrfTokenAndSessionRotation() throws Exception {
        String username = randomUsername();
        var initial = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) initial.getRequest().getSession(false);
        JsonNode token = body(initial.getResponse().getContentAsString());
        String previousId = session.getId();
        mvc.perform(post("/api/auth/register").session(session).header(token.get("headerName").asText(), token.get("token").asText()).contentType(MediaType.APPLICATION_JSON).content(registration(username)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").session(session).header(token.get("headerName").asText(), token.get("token").asText()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + username + "@example.com\",\"password\":\"password1234\"}")).andExpect(status().isOk());
        assertNotEquals(previousId, session.getId());
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
        mvc.perform(post("/api/auth/logout").session(session).header(token.get("headerName").asText(), token.get("token").asText())).andExpect(status().isForbidden());
    }
    @Test void templateSelectionPersistsAndIsVisibleOnlyOnPublishedProfiles() throws Exception {
        String username = randomUsername();
        MockHttpSession owner = registerLogin(username);
        MockHttpSession other = registerLogin(username + "b");
        mvc.perform(get("/api/me").session(owner)).andExpect(jsonPath("$.template").value("CLASSIC"));
        for (String template : List.of("MINIMAL", "STUDIO", "MIDNIGHT", "CLASSIC", "BUSINESS", "PORTFOLIO", "CREATOR")) {
            String input = json.writeValueAsString(Map.of("displayName", "Template test", "bio", "Profile description", "published", true, "template", template));
            mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.template").value(template));
            mvc.perform(get("/api/me").session(owner)).andExpect(jsonPath("$.template").value(template));
            mvc.perform(get("/api/public/" + username)).andExpect(status().isOk()).andExpect(jsonPath("$.template").value(template));
            mvc.perform(get("/api/me").session(other)).andExpect(jsonPath("$.template").value("CLASSIC"));
        }
        String dark = "{\"displayName\":\"Draft\",\"bio\":\"\",\"published\":false,\"template\":\"MIDNIGHT\"}";
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dark)).andExpect(status().isOk());
        mvc.perform(get("/api/public/" + username)).andExpect(status().isNotFound());
        // An older client omitting template must preserve the saved selection.
        String legacy = "{\"displayName\":\"Published\",\"bio\":\"\",\"published\":true}";
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(legacy))
                .andExpect(status().isOk()).andExpect(jsonPath("$.template").value("MIDNIGHT"));
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(dark.replace("MIDNIGHT", "UNKNOWN")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/public/" + username)).andExpect(status().isOk()).andExpect(jsonPath("$.template").value("MIDNIGHT"));
        mvc.perform(get("/assets/templates.css")).andExpect(status().isOk());
        mvc.perform(get("/assets/templates.js")).andExpect(status().isOk());
    }

    @Test void redisTimeoutReturns503WithoutBypassingRateLimitOrWritingData() throws Exception {
        String username = randomUsername();
        MockHttpSession owner = registerLogin(username);
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Redis outage test\",\"bio\":\"\",\"published\":true}"))
                .andExpect(status().isOk());
        when(limiter.allow(anyString(), anyInt(), anyInt())).thenThrow(new org.springframework.dao.QueryTimeoutException("Redis command timed out"));
        mvc.perform(post("/api/public/" + username + "/visits").with(csrf()))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/public/" + username + "/contacts").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Guest\",\"email\":\"guest@example.com\",\"message\":\"Hello\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + username + "@example.com\",\"password\":\"password1234\"}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/me/analytics").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalViews").value(0)).andExpect(jsonPath("$.totalContacts").value(0));
        mvc.perform(get("/api/public/" + username)).andExpect(status().isOk());
        doReturn(true).when(limiter).allow(anyString(), anyInt(), anyInt());
        mvc.perform(post("/api/public/" + username + "/visits").with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/me/analytics").session(owner)).andExpect(jsonPath("$.totalViews").value(1));
    }

    @Test void landingSectionsSupportEditingVisibilityOrderingAndOwnership() throws Exception {
        String username = randomUsername();
        MockHttpSession owner = registerLogin(username);
        MockHttpSession other = registerLogin(username + "b");
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Landing\",\"bio\":\"My work\",\"published\":true,\"template\":\"BUSINESS\"}"))
                .andExpect(status().isOk());
        List<String> ids = new ArrayList<>();
        int order = 5;
        for (String type : List.of("ABOUT", "FEATURE", "PROCESS", "PROJECT", "FAQ")) {
            String input = json.writeValueAsString(Map.of("type", type, "title", type + " title", "body", "Content " + type,
                    "url", "https://example.com/project", "sortOrder", order--, "enabled", !type.equals("FEATURE")));
            String result = mvc.perform(post("/api/me/sections").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            ids.add(body(result).get("id").asText());
        }
        mvc.perform(get("/api/me/sections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/sections").session(owner)).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/me/sections").session(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/public/" + username)).andExpect(jsonPath("$.template").value("BUSINESS"))
                .andExpect(jsonPath("$.sections.length()").value(4)).andExpect(jsonPath("$.sections[0].type").value("FAQ"))
                .andExpect(jsonPath("$.sections[0].ownerId").doesNotExist());
        String updated = json.writeValueAsString(Map.of("type", "PROJECT", "title", "Updated project", "body", "New details",
                "url", "", "sortOrder", 0, "enabled", true));
        mvc.perform(put("/api/me/sections/" + ids.get(0)).session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/sections/" + ids.get(0)).session(other).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(put("/api/me/sections/" + ids.get(0)).session(owner).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/me/sections/" + ids.get(0)).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(updated))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/" + username)).andExpect(jsonPath("$.sections[0].title").value("Updated project"));
        String invalid = json.writeValueAsString(Map.of("type", "PROJECT", "title", "Invalid", "body", "Body",
                "url", "javascript:alert(1)", "sortOrder", 0, "enabled", true));
        mvc.perform(post("/api/me/sections").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/sections").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(updated.replace("PROJECT", "UNKNOWN"))).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/sections/" + ids.get(0)).session(owner).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/" + username)).andExpect(jsonPath("$.sections.length()").value(3));
        mvc.perform(get("/assets/landing.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/landing.css")).andExpect(status().isOk());
        mvc.perform(get("/assets/theme.js")).andExpect(status().isOk());
        mvc.perform(get("/assets/dark-mode.css")).andExpect(status().isOk());
    }

    @Test void bookingApiValidationPrivacyAndOwnership() throws Exception {
        String username=randomUsername();
        MockHttpSession owner=registerLogin(username),other=registerLogin(username+"b");
        mvc.perform(get("/api/me/bookings")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/me/profile").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"displayName\":\"Booking\",\"bio\":\"\",\"published\":true}")).andExpect(status().isOk());
        String offering=body(mvc.perform(post("/api/me/services").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"Consultation\",\"description\":\"\",\"price\":0,\"durationMinutes\":60}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        var date=java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(2);
        String schedule=json.writeValueAsString(Map.of("timezone","Asia/Ho_Chi_Minh","rules",List.of(Map.of("dayOfWeek",date.getDayOfWeek().getValue(),"startMinute",540,"endMinute",660)),"exceptions",List.of()));
        mvc.perform(put("/api/me/availability").session(owner).contentType(MediaType.APPLICATION_JSON).content(schedule)).andExpect(status().isForbidden());
        mvc.perform(put("/api/me/availability").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(schedule)).andExpect(status().isOk());
        mvc.perform(put("/api/me/availability").session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(schedule.replace("Asia/Ho_Chi_Minh","Invalid/Zone"))).andExpect(status().isBadRequest());
        String slotUrl="/api/public/"+username+"/slots?serviceId="+offering+"&date="+date;
        JsonNode slots=body(mvc.perform(get(slotUrl)).andExpect(status().isOk()).andExpect(jsonPath("$.slots.length()").value(5)).andReturn().getResponse().getContentAsString());
        String input=json.writeValueAsString(Map.of("serviceId",offering,"startsAt",slots.get("slots").get(0).get("startsAt").asText(),"name","Guest","email","guest@example.com","message","Discuss a project"));
        String bookingUrl="/api/public/"+username+"/bookings";
        mvc.perform(post(bookingUrl).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isForbidden());
        JsonNode receipt=body(mvc.perform(post(bookingUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.email").doesNotExist()).andReturn().getResponse().getContentAsString());
        mvc.perform(post(bookingUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isConflict());
        mvc.perform(get(slotUrl)).andExpect(jsonPath("$.slots.length()").value(1)).andExpect(jsonPath("$.slots[0].email").doesNotExist());
        mvc.perform(get("/api/me/bookings").session(other)).andExpect(jsonPath("$.length()").value(0));
        String change="/api/me/bookings/"+receipt.get("id").asText();
        mvc.perform(patch(change).session(other).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}")).andExpect(status().isNotFound());
        mvc.perform(patch(change).session(owner).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}")).andExpect(status().isNoContent());
        mvc.perform(get(slotUrl)).andExpect(jsonPath("$.slots.length()").value(5));
        mvc.perform(post(bookingUrl).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(input)).andExpect(status().isCreated());
        mvc.perform(get("/assets/booking.js")).andExpect(status().isOk());
    }

    private MockHttpSession registerLogin(String username) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(registration(username))).andExpect(status().isCreated());
        var result = mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + username + "@example.com\",\"password\":\"password1234\"}")).andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private String randomUsername() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12); }
    private String registration(String username) throws Exception { return json.writeValueAsString(Map.of("username", username, "email", username + "@example.com", "password", "password1234", "displayName", "Trí")); }
    private JsonNode body(String body) throws Exception { return json.readTree(body); }
}
