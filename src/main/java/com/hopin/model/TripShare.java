package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TripShare {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, length = 64)
    public String token;

    @ManyToOne
    public Ride ride;

    @ManyToOne
    public User sharer;

    public boolean active = true;
    public Double lat;
    public Double lng;
    public Double accuracy;
    public LocalDateTime updatedAt;
    public LocalDateTime createdAt = LocalDateTime.now();
}
