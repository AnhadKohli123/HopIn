package com.hopin.exception;

public class BookingException extends ApplicationException {
    public BookingException(String message, int status) {
        super(message, status);
    }
}
