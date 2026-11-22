package com.hopin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @Column(unique = true)
    public String email;

    @JsonIgnore
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    public Role role;

    public double rating = 0;
    public int ratingCount = 0;
    public boolean blocked = false;

    public Boolean noSmoking = false;
    public Boolean noPets = false;
    public Boolean ac = false;
    public Boolean music = false;
    public Boolean luggage = false;

    @Column(length = 300)
    public String note;
}
