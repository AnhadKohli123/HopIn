package com.hopin.exception;

public class InvalidLoginException extends UserException {
    public InvalidLoginException() {
        super("Invalid email or password", 401);
    }
}
