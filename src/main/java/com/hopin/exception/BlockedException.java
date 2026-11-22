package com.hopin.exception;

public class BlockedException extends UserException {
    public BlockedException() {
        super("You can't interact with this user", 403);
    }
}
