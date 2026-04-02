package com.devmatch.exception;

public class UnauthorizedMatchingException extends RuntimeException {
    public UnauthorizedMatchingException(String message) {
        super(message);
    }
}
