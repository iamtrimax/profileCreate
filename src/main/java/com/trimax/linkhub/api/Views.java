package com.trimax.linkhub.api;

import com.trimax.linkhub.domain.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class Views {
    private Views() {}
    public record Me(UUID id, String email, String username, String displayName, String bio, boolean published, ProfileTemplate template, boolean emailVerified) {
        public static Me of(Account a) { return new Me(a.getId(), a.getEmail(), a.getUsername(), a.getDisplayName(), a.getBio(), a.isPublished(), a.getProfileTemplate(),a.isEmailVerified()); }
    }
    public record Link(UUID id, String title, String url, int sortOrder) {
        public static Link of(SocialLink l) { return new Link(l.getId(), l.getTitle(), l.getUrl(), l.getSortOrder()); }
    }
    public record Offering(UUID id, String title, String description, BigDecimal price, String currency, int durationMinutes) {
        public static Offering of(ServiceOffering s) { return new Offering(s.getId(), s.getTitle(), s.getDescription(), s.getPrice(), s.getCurrency(), s.getDurationMinutes()); }
    }
    public record Profile(String username, String displayName, String bio, List<Link> links, List<Offering> services, ProfileTemplate template, List<Block> sections) {}
    public record Block(UUID id, LandingBlock.Type type, String title, String body, String url, int sortOrder, boolean enabled) {
        public static Block of(LandingBlock b) { return new Block(b.getId(), b.getType(), b.getTitle(), b.getBody(), b.getUrl(), b.getSortOrder(), b.isEnabled()); }
    }
    public record Contact(UUID id, String name, String email, String message, ContactRequest.Status status, Instant createdAt) {
        public static Contact of(ContactRequest c) { return new Contact(c.getId(), c.getName(), c.getEmail(), c.getMessage(), c.getStatus(), c.getCreatedAt()); }
    }
    public record Page<T>(List<T> items, int page, int size, long total) {}
    public record Day(LocalDate date, long views) {}
    public record Analytics(long totalViews, long totalContacts, long newContacts, List<Day> daily) {}
}
