package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "daily_visits", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "visit_date"})) @Getter @Setter
public class DailyVisit {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false) private LocalDate visitDate;
    @Column(nullable = false) private long views;
}
