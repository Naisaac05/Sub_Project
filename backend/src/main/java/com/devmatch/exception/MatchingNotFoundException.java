package com.devmatch.exception;

public class MatchingNotFoundException extends RuntimeException {
    public MatchingNotFoundException(String message) {
        super(message);
    }
}
