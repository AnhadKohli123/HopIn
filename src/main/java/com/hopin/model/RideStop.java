package com.hopin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
public class RideStop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne
    public Ride ride;

    public String name;
    public Double lat;
    public Double lng;
    public int seq;
}
