package com.trimax.linkhub.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity @Table(name = "social_links") @Getter @Setter
public class SocialLink {
    @Id private UUID id = UUID.randomUUID();
    @Column(nullable = false) private UUID ownerId;
    @Column(nullable = false, length = 100) private String title;
    @Column(nullable = false, length = 2000) private String url;
    @Column(nullable = false) private int sortOrder;
}
