package com.devmatch.exception;

public class AlreadyAppliedException extends RuntimeException {
    public AlreadyAppliedException(String message) {
        super(message);
    }
}
