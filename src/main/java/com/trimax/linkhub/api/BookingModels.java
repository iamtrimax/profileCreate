package com.trimax.linkhub.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class BookingModels {
    private BookingModels() {}
    public enum Status { PENDING, CONFIRMED, CANCELLED, COMPLETED }
    public record Rule(@Min(1) @Max(7) int dayOfWeek, @Min(0) @Max(1439) int startMinute, @Min(1) @Max(1440) int endMinute) {}
    public record ExceptionDay(@NotNull LocalDate date, boolean closed, @Min(0) @Max(1439) Integer startMinute, @Min(1) @Max(1440) Integer endMinute) {}
    public record Schedule(@NotBlank @Size(max=64) String timezone, @NotNull @Size(max=84) List<@NotNull @Valid Rule> rules,
                           @NotNull @Size(max=180) List<@NotNull @Valid ExceptionDay> exceptions) {}
    public record Create(@NotNull UUID serviceId, @NotNull Instant startsAt,
                         @NotBlank @Size(max=100) String name, @NotBlank @Email @Size(max=254) String email,
                         @NotNull @Size(max=2000) String message) {}
    public record Change(@NotNull Status status) {}
    public record Slot(Instant startsAt, Instant endsAt) {}
    public record Slots(String timezone, List<Slot> slots) {}
    public record Receipt(UUID id, Status status, Instant startsAt, Instant endsAt, String timezone) {}
    public record Booking(UUID id, String serviceTitle, String name, String email, String message,
                          Instant startsAt, Instant endsAt, String timezone, Status status) {}
}
