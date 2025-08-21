package com.ruchira.murex.exception;

import com.ruchira.murex.parser.DynamicFieldParser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized exception handler for REST API
 * Provides consistent error response format and proper HTTP status codes
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("error", "Business Logic Error");
        errorResponse.put("errorCode", ex.getErrorCode());
        errorResponse.put("message", ex.getMessage());
        
        if (ex.getContext() != null) {
            errorResponse.put("context", ex.getContext());
        }
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(TransformationException.class)
    public ResponseEntity<Map<String, Object>> handleTransformationException(TransformationException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        errorResponse.put("error", "Transformation Error");
        errorResponse.put("errorCode", ex.getErrorCode());
        errorResponse.put("message", ex.getMessage());
        
        if (ex.getTransformationType() != null) {
            errorResponse.put("transformationType", ex.getTransformationType());
        }
        
        if (ex.getFieldName() != null) {
            errorResponse.put("fieldName", ex.getFieldName());
        }
        
        return ResponseEntity.unprocessableEntity().body(errorResponse);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(ValidationException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.BAD_REQUEST.value());
        errorResponse.put("error", "Validation Error");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("groupKey", ex.getGroupKey());
        errorResponse.put("typology", ex.getTypology());
        errorResponse.put("recordCount", ex.getRecordCount());
        
        return ResponseEntity.badRequest().body(errorResponse);
    }

    @ExceptionHandler(DynamicFieldParser.DynamicMappingException.class)
    public ResponseEntity<Map<String, Object>> handleDynamicMappingException(DynamicFieldParser.DynamicMappingException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put("error", "Dynamic Field Mapping Error");
        errorResponse.put("message", ex.getMessage());
        
        return ResponseEntity.internalServerError().body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        errorResponse.put("error", "Internal Server Error");
        errorResponse.put("message", "An unexpected error occurred");

        
        return ResponseEntity.internalServerError().body(errorResponse);
    }
}