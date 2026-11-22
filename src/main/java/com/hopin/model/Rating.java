package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Rating {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Ride ride;

    @ManyToOne
    public User fromUser;

    @ManyToOne
    public User toUser;

    public int score;

    @Column(length = 300)
    public String comment;

    public LocalDateTime createdAt = LocalDateTime.now();
}
