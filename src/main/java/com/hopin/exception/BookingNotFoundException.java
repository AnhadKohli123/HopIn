package com.hopin.exception;

public class BookingNotFoundException extends BookingException {
    public BookingNotFoundException(Long id) {
        super("Booking " + id + " not found", 404);
    }
}
