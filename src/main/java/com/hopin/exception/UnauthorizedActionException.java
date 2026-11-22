package com.hopin.exception;

public class UnauthorizedActionException extends UserException {
    public UnauthorizedActionException(String message) {
        super(message, 403);
    }
}
