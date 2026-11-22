package com.hopin.exception;

public class NoSeatsAvailableException extends RideException {
    public NoSeatsAvailableException(int left) {
        super("Not enough seats available, only " + left + " left", 409);
    }
}
