package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "contact_requests") @Getter @Setter
public class ContactRequest {
    public enum Status { NEW, READ, ARCHIVED }
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 254) private String email;
    @Column(nullable = false, length = 4000) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status = Status.NEW;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}
