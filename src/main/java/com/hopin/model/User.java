package com.hopin.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;

    @JsonIgnore
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

    @JsonIgnore
    public String phone;

    public Boolean emailVerified = false;
    public Boolean phoneVerified = false;

    @JsonIgnore
    public String idType;

    @JsonIgnore
    public String idLast4;

    @JsonIgnore
    public String idStatus = "NONE";

    public Boolean hasPhoto = false;
    public Integer photoVersion = 0;

    @JsonProperty("idVerified")
    public boolean idVerified() {
        return "APPROVED".equals(idStatus);
    }

    @JsonProperty("verified")
    public boolean verified() {
        return Boolean.TRUE.equals(emailVerified) && Boolean.TRUE.equals(phoneVerified) && idVerified();
    }
}
