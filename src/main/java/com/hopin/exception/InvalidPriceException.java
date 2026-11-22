package com.hopin.exception;

public class InvalidPriceException extends BookingException {
    public InvalidPriceException(String message) {
        super(message, 400);
    }
}
