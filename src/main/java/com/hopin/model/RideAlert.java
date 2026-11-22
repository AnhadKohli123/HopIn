package com.hopin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class RideAlert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne
    public User user;

    public String source;
    public String destination;
    public LocalDate date;
    public int seats = 1;
    public Double fromLat;
    public Double fromLng;
    public Double toLat;
    public Double toLng;
    public Double radiusKm;
    public LocalDateTime createdAt = LocalDateTime.now();
}
