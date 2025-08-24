package com.ruchira.murex.exception;

import lombok.Getter;

/**
 * Base exception for all business logic related errors
 * Provides structured error information for business rule violations
 */
@Getter
public class BusinessException extends RuntimeException {


    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}