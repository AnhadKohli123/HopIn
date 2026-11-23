package com.hopin.model;

import jakarta.persistence.*;

@Entity
public class UserPhoto {
    @Id
    public Long userId;

    @Column(length = 1000000)
    public String data;
}
