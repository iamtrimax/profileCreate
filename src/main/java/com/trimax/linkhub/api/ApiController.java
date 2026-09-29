package com.trimax.linkhub.api;

import com.trimax.linkhub.service.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    private final LinkHubService service;
    private final RateLimiter rateLimiter;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final AccountSecurityService accountSecurity;
    private final AnalyticsService events;
    @Value("${linkhub.base-url}")
    private String baseUrl;

    @GetMapping("/auth/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Me register(@Valid @RequestBody Requests.Register input, HttpServletRequest request) {
        throttle(request, "register", 5, 3600);
        return service.register(input);
    }

    @PostMapping("/auth/login")
    public Views.Me login(@Valid @RequestBody Requests.Login input, HttpServletRequest request,
            HttpServletResponse response) {
        throttle(request, "login", 20, 300);
        long versionBefore = service.findByEmail(input.email()).map(a -> a.getAuthVersion()).orElse(-1L);
        Authentication auth = authenticationManager.authenticate(UsernamePasswordAuthenticationToken
                .unauthenticated(LinkHubService.normalizeEmail(input.email()), input.password()));
        if (versionBefore != accountSecurity.version(owner(auth)))
            throw new BadCredentialsException("Thông tin đăng nhập đã thay đổi. Vui lòng thử lại.");
        if (request.getSession(false) != null)
            request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        request.getSession().setAttribute("AUTH_VERSION", versionBefore);
        new HttpSessionCsrfTokenRepository().saveToken(null, request, response);
        return service.me(owner(auth));
    }

    @GetMapping("/me")
    public Views.Me me(Authentication auth) {
        return service.me(owner(auth));
    }

    @PutMapping("/me/profile")
    public Views.Me profile(Authentication auth, @Valid @RequestBody Requests.Profile input) {
        return service.updateProfile(owner(auth), input);
    }

    @GetMapping("/me/links")
    public List<Views.Link> links(Authentication auth) {
        return service.links(owner(auth));
    }

    @PostMapping("/me/links")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Link addLink(Authentication auth, @Valid @RequestBody Requests.Link input) {
        return service.saveLink(owner(auth), null, input);
    }

    @PutMapping("/me/links/{id}")
    public Views.Link updateLink(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Requests.Link input) {
        return service.saveLink(owner(auth), id, input);
    }

    @DeleteMapping("/me/links/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteLink(Authentication auth, @PathVariable UUID id) {
        service.deleteLink(owner(auth), id);
    }

    @GetMapping("/me/services")
    public List<Views.Offering> services(Authentication auth) {
        return service.services(owner(auth));
    }

    @PostMapping("/me/services")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Offering addService(Authentication auth, @Valid @RequestBody Requests.Offering input) {
        return service.saveService(owner(auth), null, input);
    }

    @PutMapping("/me/services/{id}")
    public Views.Offering updateService(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody Requests.Offering input) {
        return service.saveService(owner(auth), id, input);
    }

    @DeleteMapping("/me/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteService(Authentication auth, @PathVariable UUID id) {
        service.deleteService(owner(auth), id);
    }

    @GetMapping("/me/sections")
    public List<Views.Block> sections(Authentication auth) {
        return service.blocks(owner(auth));
    }

    @PostMapping("/me/sections")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.Block addSection(Authentication auth, @Valid @RequestBody Requests.Block input) {
        return service.saveBlock(owner(auth), null, input);
    }

    @PutMapping("/me/sections/{id}")
    public Views.Block updateSection(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody Requests.Block input) {
        return service.saveBlock(owner(auth), id, input);
    }

    @DeleteMapping("/me/sections/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(Authentication auth, @PathVariable UUID id) {
        service.deleteBlock(owner(auth), id);
    }

    @GetMapping("/me/contacts")
    public Views.Page<Views.Contact> contacts(Authentication auth,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.contacts(owner(auth), page, size);
    }

    @PatchMapping("/me/contacts/{id}")
    public Views.Contact status(Authentication auth, @PathVariable UUID id,
            @Valid @RequestBody Requests.ContactStatus input) {
        return service.contactStatus(owner(auth), id, input.status());
    }

    @GetMapping("/me/analytics")
    public Views.Analytics analytics(Authentication auth,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return service.analytics(owner(auth), days);
    }

    @GetMapping("/public/{username}")
    public Views.Profile publicProfile(@PathVariable String username) {
        return service.publicProfile(username);
    }

    @PostMapping("/public/{username}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, UUID> contact(@PathVariable String username, @Valid @RequestBody Requests.Contact input,
            HttpServletRequest request) {
        throttle(request, "contact", 5, 600);
        return Map.of("id", service.contact(username, input));
    }

    public record EventInput(@Size(max = 2048) String referrer) {
    }

    @PostMapping("/public/{username}/visits")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void visit(@PathVariable String username, @Valid @RequestBody(required = false) EventInput input,
            HttpServletRequest request, HttpServletResponse response) {
        throttle(request, "visit", 60, 60);
        events.record(username, null, input == null ? null : input.referrer(), request, response);
    }

    @PostMapping("/public/{username}/clicks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void click(@PathVariable String username, @PathVariable UUID id, HttpServletRequest request,
            HttpServletResponse response) {
        throttle(request, "click", 60, 60);
        events.record(username, id, null, request, response);
    }

    @GetMapping("/me/analytics/engagement")
    public AnalyticsService.Engagement engagement(Authentication auth,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int days) {
        return events.engagement(owner(auth), days);
    }

    @GetMapping(value = "/public/{username}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@PathVariable String username) throws Exception {
        Views.Profile profile = service.publicProfile(username);
        String url = baseUrl.replaceAll("/+$", "") + "/" + profile.username();
        var matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 320, 320);
        var bytes = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", bytes);
        return bytes.toByteArray();
    }

    private UUID owner(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    private void throttle(HttpServletRequest request, String action, int limit, int seconds) {
        // Do not trust caller-supplied forwarding headers; use the direct peer address.
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(request.getRemoteAddr().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        if (!rateLimiter.allow(action + ":" + hash, limit, seconds))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Bạn thao tác quá nhanh. Vui lòng thử lại sau.");
    }
}
