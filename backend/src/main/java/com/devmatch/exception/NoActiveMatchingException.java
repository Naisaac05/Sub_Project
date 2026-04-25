package com.devmatch.exception;

public class NoActiveMatchingException extends RuntimeException {
    public NoActiveMatchingException(String message) {
        super(message);
    }
}
