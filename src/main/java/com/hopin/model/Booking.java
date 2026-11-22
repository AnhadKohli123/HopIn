package com.hopin.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class Booking {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne
    public Ride ride;

    @ManyToOne
    public User rider;

    public int seats;

    public Integer pickupIdx;
    public Integer dropIdx;
    public String pickupName;
    public String dropName;

    public Double listPrice;
    public Double offeredPrice;
    public Double counterPrice;
    public Double agreedPrice;

    @Enumerated(EnumType.STRING)
    public BookingStatus status = BookingStatus.PENDING;

    public boolean paid = false;
    public double amountPaid = 0;
    public LocalDateTime createdAt = LocalDateTime.now();
}
