package com.imali.banking.exception;

public class UnauthorizedAccountAccessException extends RuntimeException {

    public UnauthorizedAccountAccessException(String message) {
        super(message);
    }
}
