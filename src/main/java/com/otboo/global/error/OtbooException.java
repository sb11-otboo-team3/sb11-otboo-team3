package com.otboo.global.error;

import org.springframework.http.HttpStatus;

public abstract class OtbooException extends RuntimeException {

    private final HttpStatus status;

    protected OtbooException(
            HttpStatus status,
            String message
    ) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}