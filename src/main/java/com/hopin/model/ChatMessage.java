package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Ride ride;

    @ManyToOne
    public User rider;

    @ManyToOne
    public User sender;

    @Column(length = 1000)
    public String text;

    public LocalDateTime createdAt = LocalDateTime.now();
    public boolean seen = false;
}
