package com.hopin.exception;

public class BookingCancelledException extends BookingException {
    public BookingCancelledException() {
        super("This booking is no longer active", 409);
    }
}
