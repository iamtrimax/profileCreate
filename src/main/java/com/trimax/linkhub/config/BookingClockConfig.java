package com.trimax.linkhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class BookingClockConfig {
    @Bean Clock bookingClock() {return Clock.systemUTC();}
}
