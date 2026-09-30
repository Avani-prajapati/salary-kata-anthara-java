package com.acme.salary.common.error;

public class InvalidReferenceException extends RuntimeException {

    private final String field;

    public InvalidReferenceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
