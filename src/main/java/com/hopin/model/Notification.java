package com.hopin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @JsonIgnore
    @ManyToOne
    public User user;

    @Column(length = 300)
    public String text;

    public String tab;
    public String searchFrom;
    public String searchTo;
    public LocalDate searchDate;
    public boolean seen = false;
    public LocalDateTime createdAt = LocalDateTime.now();
}
