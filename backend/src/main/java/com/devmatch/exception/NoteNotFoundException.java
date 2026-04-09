package com.devmatch.exception;

public class NoteNotFoundException extends RuntimeException {
    public NoteNotFoundException(String message) { super(message); }
}
