package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Ride {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public User driver;

    @ManyToOne
    public Vehicle vehicle;

    public String source;
    public Double srcLat;
    public Double srcLng;

    public String destination;
    public Double dstLat;
    public Double dstLng;

    @OneToMany(mappedBy = "ride", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seq ASC")
    public List<RideStop> stops = new ArrayList<>();

    public LocalDateTime departTime;
    public int seatsTotal;
    public int seatsLeft;
    public double pricePerSeat;
    public boolean negotiable = false;
    public double totalCost;

    @Enumerated(EnumType.STRING)
    public RideStatus status = RideStatus.OPEN;
}
