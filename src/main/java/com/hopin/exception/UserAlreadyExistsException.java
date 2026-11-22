package com.hopin.exception;

public class UserAlreadyExistsException extends UserException {
    public UserAlreadyExistsException(String email) {
        super("An account with " + email + " already exists", 409);
    }
}
