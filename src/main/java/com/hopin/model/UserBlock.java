package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class UserBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public User blocker;

    @ManyToOne
    public User blocked;

    public LocalDateTime createdAt = LocalDateTime.now();
}
