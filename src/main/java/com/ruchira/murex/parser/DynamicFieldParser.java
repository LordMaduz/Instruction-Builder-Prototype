package com.ruchira.murex.parser;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Generic utility for dynamic field access and manipulation using reflection and Jackson
 * Eliminates hardcoded field mappings and supports nested properties
 * <p>
 * Key Features:
 * - Reflection-based property access with caching for performance
 * - Support for nested field paths (e.g., "address.street.name")
 * - Type-safe conversions with proper error handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicFieldParser {

    /**
     * Get field value using reflection with support for nested properties
     *
     * @param obj       Target object
     * @param fieldPath Field path (e.g., "name" or "address.street")
     * @return Field value or null if not found
     */
    public Object getFieldValue(Object obj, String fieldPath) {
        if (Objects.isNull(obj) || ObjectUtils.isEmpty(fieldPath)) {
            return null;
        }

        try {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(obj);
            if (!wrapper.isReadableProperty(fieldPath)) {
                return null; // or throw exception if strict mode
            }
            return wrapper.getPropertyValue(fieldPath);

        } catch (Exception e) {
            throw new DynamicMappingException(String.format("Failed to get field value: %s", fieldPath), e);
        }
    }

    /**
     * Set field value using reflection with type conversion
     *
     * @param obj       Target object
     * @param fieldPath Field path
     * @param value     Value to set (will be converted to appropriate type)
     */
    public void setFieldValue(Object obj, String fieldPath, Object value) {
        if (Objects.isNull(obj) || ObjectUtils.isEmpty(fieldPath)) {
            return;
        }

        try {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(obj);
            if (!wrapper.isWritableProperty(fieldPath)) {
                throw new DynamicMappingException(String.format("Field not found: %s", fieldPath));
            }
            wrapper.setPropertyValue(fieldPath, value);

        } catch (Exception e) {
            throw new DynamicMappingException(String.format("Failed to set field value: %s", fieldPath), e);
        }

    }

    /**
     * Bulk apply field mappings from JSON configuration
     *
     * @param targetObj     Target object to modify
     * @param mappingConfig JSON node containing field mappings
     * @param ignoreFields  List of field names to skip during mapping
     */
    public void applyFieldMappings(Object targetObj, JsonNode mappingConfig, List<String> ignoreFields) {
        if (targetObj == null || mappingConfig == null || !mappingConfig.isObject()) {
            return;
        }

        mappingConfig.properties().forEach(entry -> {
            String fieldName = entry.getKey();
            JsonNode valueNode = entry.getValue();

            if (CollectionUtils.isNotEmpty(ignoreFields) && ignoreFields.contains(fieldName)) {
                return;
            }

            try {
                Object value = extractValueFromJsonNode(valueNode);
                setFieldValue(targetObj, fieldName, value);
            } catch (Exception e) {
                throw new DynamicMappingException(String.format("Failed to apply mapping for field: %s", fieldName), e);
            }
        });
    }

    public Object extractValueFromJsonNode(JsonNode valueNode) {
        if (valueNode == null || valueNode.isNull()) {
            return null;
        }

        if (valueNode.isTextual()) {
            return valueNode.asText();
        } else if (valueNode.isNumber()) {
            if (valueNode.isInt()) {
                return valueNode.asInt();
            } else if (valueNode.isLong()) {
                return valueNode.asLong();
            } else {
                return valueNode.asDouble();
            }
        } else if (valueNode.isBoolean()) {
            return valueNode.asBoolean();
        } else if (valueNode.isArray()) {
            List<Object> result = new ArrayList<>();
            valueNode.forEach(element -> result.add(extractValueFromJsonNode(element)));
            return result;
        }

        return valueNode.asText();
    }

    /**
     * Custom exception for dynamic mapping operations
     */
    public static class DynamicMappingException extends RuntimeException {
        public DynamicMappingException(String message) {
            super(message);
        }

        public DynamicMappingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}