package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class VerificationCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public User user;

    public String channel;
    public String target;
    public String code;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime expiresAt;
    public int attempts = 0;
    public boolean used = false;
}
