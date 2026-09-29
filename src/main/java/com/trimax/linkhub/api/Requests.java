package com.trimax.linkhub.api;

import jakarta.validation.constraints.*;
import com.trimax.linkhub.domain.ContactRequest;
import com.trimax.linkhub.domain.ProfileTemplate;
import com.trimax.linkhub.domain.LandingBlock;
import java.math.BigDecimal;

public final class Requests {
    private Requests() {}
    public record Register(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=10, max=64) String password,
            @NotBlank @Pattern(regexp="[a-zA-Z0-9][a-zA-Z0-9_]{2,29}") String username,
            @NotBlank @Size(max=100) String displayName) {
        @Override public String toString() { return "Register[credentials redacted]"; }
    }
    public record Login(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=64) String password) {
        @Override public String toString() { return "Login[credentials redacted]"; }
    }
    public record Profile(@NotBlank @Size(max=100) String displayName,
            @NotNull @Size(max=2000) String bio, boolean published, ProfileTemplate template) {}
    public record Link(@NotBlank @Size(max=100) String title,
            @NotBlank @Size(max=2000) String url, @Min(0) @Max(10000) int sortOrder) {}
    public record Offering(@NotBlank @Size(max=100) String title,
            @NotNull @Size(max=2000) String description,
            @NotNull @DecimalMin("0") @Digits(integer=13, fraction=2) BigDecimal price,
            @Min(15) @Max(480) Integer durationMinutes) {}
    public record Contact(@NotBlank @Size(max=100) String name,
            @NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=4000) String message) {}
    public record ContactStatus(@NotNull ContactRequest.Status status) {}
    public record Block(@NotNull LandingBlock.Type type,
            @NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=3000) String body,
            @NotNull @Size(max=2000) String url,
            @Min(0) @Max(10000) int sortOrder, boolean enabled) {}
}
