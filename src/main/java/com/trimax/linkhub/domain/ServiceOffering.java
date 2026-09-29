package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "service_offerings") @Getter @Setter
public class ServiceOffering {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 2000) private String description;
    @Column(nullable = false, precision = 15, scale = 2) private BigDecimal price;
    @Column(nullable = false, length = 3) private String currency = "VND";
    @Column(nullable = false) private int durationMinutes = 60;
}
