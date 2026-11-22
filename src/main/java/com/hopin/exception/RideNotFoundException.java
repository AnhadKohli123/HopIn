package com.hopin.exception;

public class RideNotFoundException extends RideException {
    public RideNotFoundException(Long id) {
        super("Ride " + id + " not found", 404);
    }
}
