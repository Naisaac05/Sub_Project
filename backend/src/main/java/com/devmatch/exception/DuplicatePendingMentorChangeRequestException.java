package com.devmatch.exception;

public class DuplicatePendingMentorChangeRequestException extends RuntimeException {
    public DuplicatePendingMentorChangeRequestException(String message) {
        super(message);
    }
}
