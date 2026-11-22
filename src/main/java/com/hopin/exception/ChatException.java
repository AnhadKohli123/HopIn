package com.hopin.exception;

public class ChatException extends ApplicationException {
    public ChatException(String message, int status) {
        super(message, status);
    }
}
