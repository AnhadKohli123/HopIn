package com.hopin.exception;

public class SafetyException extends ApplicationException {
    public SafetyException(String message, int status) {
        super(message, status);
    }
}
