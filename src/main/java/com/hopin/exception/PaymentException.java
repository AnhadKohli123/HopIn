package com.hopin.exception;

public class PaymentException extends ApplicationException {
    public PaymentException(String message, int status) {
        super(message, status);
    }
}
