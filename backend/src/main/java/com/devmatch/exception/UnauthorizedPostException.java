package com.devmatch.exception;

public class UnauthorizedPostException extends RuntimeException {

    public UnauthorizedPostException(String message) {
        super(message);
    }
}
