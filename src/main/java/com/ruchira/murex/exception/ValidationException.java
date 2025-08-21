package com.ruchira.murex.exception;

import lombok.Getter;

/**
 * Custom exception for validation errors
 */
@Getter
public class ValidationException extends RuntimeException {
    
    private final String groupKey;
    private final String typology;
    private final int recordCount;

    public ValidationException(String message, String groupKey, String typology, int recordCount) {
        super(message);
        this.groupKey = groupKey;
        this.typology = typology;
        this.recordCount = recordCount;
    }

    @Override
    public String toString() {
        return String.format("ValidationException{message='%s', groupKey='%s', typology='%s', recordCount=%d}", 
                           getMessage(), groupKey, typology, recordCount);
    }
}