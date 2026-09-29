package com.trimax.linkhub;

import com.trimax.linkhub.api.*;
import com.trimax.linkhub.api.BookingModels.*;
import com.trimax.linkhub.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BookingRetryTests {
    @Test void retriesTransactionFailureAndBoundsRetries() {
        BookingService service=mock(BookingService.class);RateLimiter limiter=mock(RateLimiter.class);
        when(limiter.allow(anyString(),anyInt(),anyInt())).thenReturn(true);
        var controller=new BookingController(service,limiter);
        var input=new Create(UUID.randomUUID(),Instant.now(),"Guest","guest@example.com","");
        var receipt=new Receipt(UUID.randomUUID(),Status.PENDING,Instant.now(),Instant.now().plusSeconds(3600),"UTC");
        when(service.create("test",input)).thenThrow(new CannotAcquireLockException("deadlock")).thenReturn(receipt);
        assertEquals(receipt,controller.create("test",input,new MockHttpServletRequest()));
        verify(service,times(2)).create("test",input);
        reset(service);
        when(service.create("test",input)).thenThrow(new CannotAcquireLockException("deadlock"));
        assertEquals(409,assertThrows(ResponseStatusException.class,()->controller.create("test",input,new MockHttpServletRequest())).getStatusCode().value());
        verify(service,times(3)).create("test",input);
    }
}
