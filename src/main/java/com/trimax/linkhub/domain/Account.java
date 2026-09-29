package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "accounts") @Getter @Setter
public class Account {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(nullable = false, length = 100, updatable = false) private String passwordHash;
    @Column(nullable = false, unique = true, length = 30) private String username;
    @Column(nullable = false, length = 100) private String displayName;
    @Column(nullable = false, length = 2000) private String bio = "";
    @Column(nullable = false) private boolean published;
    @Column(nullable = false, updatable = false) private boolean emailVerified;
    @Column(nullable = false, updatable = false) private long authVersion;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private ProfileTemplate profileTemplate = ProfileTemplate.CLASSIC;
    @Column(nullable = false) private Instant createdAt = Instant.now();
}
