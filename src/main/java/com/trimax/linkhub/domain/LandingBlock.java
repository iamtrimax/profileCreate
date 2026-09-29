package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "landing_blocks") @Getter @Setter
public class LandingBlock {
    public enum Type { ABOUT, FEATURE, PROCESS, PROJECT, FAQ }
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID ownerId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type type;
    @Column(nullable = false, length = 120) private String title;
    @Column(nullable = false, length = 3000) private String body;
    @Column(nullable = false, length = 2000) private String url = "";
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean enabled = true;
}
