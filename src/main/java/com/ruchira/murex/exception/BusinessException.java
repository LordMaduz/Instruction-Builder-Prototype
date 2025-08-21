package com.ruchira.murex.exception;

import lombok.Getter;

/**
 * Base exception for all business logic related errors
 * Provides structured error information for business rule violations
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private final String errorCode;
    private final String context;
    
    public BusinessException(String message) {
        super(message);
        this.errorCode = "BUSINESS_ERROR";
        this.context = null;
    }
    
    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = null;
    }
    
    public BusinessException(String errorCode, String message, String context) {
        super(message);
        this.errorCode = errorCode;
        this.context = context;
    }
    
    public BusinessException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = null;
    }
    
    public BusinessException(String errorCode, String message, String context, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = context;
    }

}