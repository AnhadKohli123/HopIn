package com.hopin.exception;

public class VerificationException extends UserException {
    public VerificationException(String message) {
        super(message, 400);
    }
}
