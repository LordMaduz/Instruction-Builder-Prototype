package com.ruchira.murex.parser;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic utility for dynamic field access and manipulation using reflection and Jackson
 * Eliminates hardcoded field mappings and supports nested properties
 *
 * Key Features:
 * - Reflection-based property access with caching for performance
 * - Jackson ObjectMapper integration for complex JSON transformations
 * - Support for nested field paths (e.g., "address.street.name")
 * - Type-safe conversions with proper error handling
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicFieldParser {

    private final ObjectMapper objectMapper;

    private final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();

    /**
     * Get field value using reflection with support for nested properties
     *
     * @param obj Target object
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
            throw new DynamicMappingException("Failed to get field value: " + fieldPath, e);
        }
    }

    /**
     * Set field value using reflection with type conversion
     *
     * @param obj Target object
     * @param fieldPath Field path
     * @param value Value to set (will be converted to appropriate type)
     */
    public void setFieldValue(Object obj, String fieldPath, Object value) {
        if (Objects.isNull(obj) || ObjectUtils.isEmpty(fieldPath)) {
            return;
        }

        try {
            BeanWrapperImpl wrapper = new BeanWrapperImpl(obj);
            if (!wrapper.isWritableProperty(fieldPath)) {
                throw new DynamicMappingException("Field not found: " + fieldPath);
            }
            wrapper.setPropertyValue(fieldPath, value);

        } catch (Exception e) {
            throw new DynamicMappingException("Failed to set field value: " + fieldPath, e);
        }

    }

    /**
     * Bulk apply field mappings from JSON configuration
     *
     * @param targetObj Target object to modify
     * @param mappingConfig JSON node containing field mappings
     * @param ignoreFields  List of field names to skip during mapping
     *
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
                throw new DynamicMappingException("Failed to apply mapping for field: " + fieldName, e);
            }
        });
    }

    /**
     * Create a field mapping between source and target objects
     *
     * @param source Source object
     * @param target Target object
     * @param fieldMappings Map of target field -> source field
     */
    public void mapFields(Object source, Object target, Map<String, String> fieldMappings) {
        if (source == null || target == null || fieldMappings == null) {
            return;
        }

        fieldMappings.forEach((targetField, sourceField) -> {
            try {
                Object value = getFieldValue(source, sourceField);
                setFieldValue(target, targetField, value);
            } catch (Exception e) {
                throw new DynamicMappingException("Failed to map field: " + sourceField + " -> " + targetField, e);
            }
        });
    }

    /**
     * Convert object to Map representation for dynamic access
     *
     * @param obj Source object
     * @return Map representation
     */
    public Map<String, Object> objectToMap(Object obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }

        try {
            return objectMapper.convertValue(obj, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new DynamicMappingException("Failed to convert object to map", e);
        }
    }

    /**
     * Convert Map to specific object type
     *
     * @param map Source map
     * @param targetClass Target class
     * @return Converted object
     */
    public <T> T mapToObject(Map<String, Object> map, Class<T> targetClass) {
        if (map == null || targetClass == null) {
            return null;
        }

        try {
            return objectMapper.convertValue(map, targetClass);
        } catch (Exception e) {
            throw new DynamicMappingException("Failed to convert map to object", e);
        }
    }

    // Private helper methods

    private Object getNestedFieldValue(Object obj, String fieldPath) {
        String[] parts = fieldPath.split("\\.");
        Object current = obj;

        for (String part : parts) {
            if (current == null) {
                return null;
            }
            current = getFieldValue(current, part);
        }

        return current;
    }

    private void setNestedFieldValue(Object obj, String fieldPath, Object value) {
        String[] parts = fieldPath.split("\\.");
        Object current = obj;

        // Navigate to the parent of the final field
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = getFieldValue(current, parts[i]);
            if (next == null) {
                throw new DynamicMappingException("Cannot set nested field - null intermediate object at: " + parts[i]);
            }
            current = next;
        }

        // Set the final field
        setFieldValue(current, parts[parts.length - 1], value);
    }

    private Method findGetterMethod(Class<?> clazz, String fieldName) {
        String getterName = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        String booleanGetterName = "is" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        try {
            return clazz.getMethod(getterName);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getMethod(booleanGetterName);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }

    private Method findSetterMethod(Class<?> clazz, String fieldName) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        // Try to find setter by looking at available methods
        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }

        return null;
    }

    private Field findField(Class<?> clazz, String fieldName) {
        Map<String, Field> fields = fieldCache.computeIfAbsent(clazz, this::buildFieldMap);
        return fields.get(fieldName);
    }

    private Map<String, Field> buildFieldMap(Class<?> clazz) {
        Map<String, Field> fieldMap = new HashMap<>();

        // Include fields from superclasses
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            Field[] fields = current.getDeclaredFields();
            for (Field field : fields) {
                if (!fieldMap.containsKey(field.getName())) {
                    fieldMap.put(field.getName(), field);
                }
            }
            current = current.getSuperclass();
        }

        return fieldMap;
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }

        try {
            return objectMapper.convertValue(value, targetType);
        } catch (Exception e) {
            throw new DynamicMappingException("Failed to convert value to type: " + targetType.getName(), e);
        }
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
            valueNode.forEach(element-> result.add(extractValueFromJsonNode(element)));
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