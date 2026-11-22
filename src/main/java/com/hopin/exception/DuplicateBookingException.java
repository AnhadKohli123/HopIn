package com.hopin.exception;

public class DuplicateBookingException extends BookingException {
    public DuplicateBookingException() {
        super("You already have an active booking on this ride", 409);
    }
}
