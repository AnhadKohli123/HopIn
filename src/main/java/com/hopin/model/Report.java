package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public User reporter;

    @ManyToOne
    public User reported;

    public String reason;

    @Column(length = 500)
    public String details;

    public Long rideId;
    public Double lat;
    public Double lng;
    public boolean sos = false;
    public String status = "OPEN";
    public LocalDateTime createdAt = LocalDateTime.now();
}
