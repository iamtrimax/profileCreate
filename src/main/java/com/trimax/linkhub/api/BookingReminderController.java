package com.trimax.linkhub.api;

import com.trimax.linkhub.service.BookingReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import java.util.UUID;

@RestController @RequiredArgsConstructor
public class BookingReminderController {
    private final BookingReminderService reminders;
    @GetMapping("/api/me/bookings/upcoming")
    public BookingReminderService.Upcoming upcoming(Authentication auth){return reminders.upcoming(UUID.fromString(auth.getName()));}
}
