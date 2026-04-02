package com.devmatch.exception;

public class DuplicateMatchingException extends RuntimeException {
    public DuplicateMatchingException(String message) {
        super(message);
    }
}
