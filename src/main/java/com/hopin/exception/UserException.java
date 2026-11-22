package com.hopin.exception;

public class UserException extends ApplicationException {
    public UserException(String message, int status) {
        super(message, status);
    }
}
