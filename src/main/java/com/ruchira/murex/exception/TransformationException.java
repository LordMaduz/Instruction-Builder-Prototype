package com.ruchira.murex.exception;

import lombok.Getter;

/**
 * Exception for transformation logic errors
 * Specialized business exception for data transformation operations
 */
@Getter
public class TransformationException extends BusinessException {
    
    private final String transformationType;
    private final String fieldName;
    
    public TransformationException(String message) {
        super("TRANSFORMATION_ERROR", message);
        this.transformationType = null;
        this.fieldName = null;
    }
    
    public TransformationException(String message, String transformationType) {
        super("TRANSFORMATION_ERROR", message);
        this.transformationType = transformationType;
        this.fieldName = null;
    }
    
    public TransformationException(String message, String transformationType, String fieldName) {
        super("TRANSFORMATION_ERROR", message);
        this.transformationType = transformationType;
        this.fieldName = fieldName;
    }
    
    public TransformationException(String message, String transformationType, Throwable cause) {
        super("TRANSFORMATION_ERROR", message, cause);
        this.transformationType = transformationType;
        this.fieldName = null;
    }

}