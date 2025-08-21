package com.ruchira.murex.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Map;
import java.util.Set;

@UtilityClass
public class CopyUtils {

    private final ObjectMapper mapper = new ObjectMapper();

    public <S, D> D clone(S source, Class<D> clazz) {
        try {
            D target = clazz.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(target, source);
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Error cloning DTO", e);
        }
    }

    public <S, D> D clone(S source, Class<D> clazz, Map<String, Object> overrides) {
        try {
            D target = clazz.getDeclaredConstructor().newInstance();
            BeanUtils.copyProperties(target, source);
            if (ObjectUtils.isNotEmpty(overrides)) {
                for (Map.Entry<String, Object> entry : overrides.entrySet()) {
                    BeanUtils.setProperty(target, entry.getKey(), entry.getValue());
                }
            }
            return target;
        } catch (Exception e) {
            throw new RuntimeException("Error cloning DTO", e);
        }
    }

    /**
     * Clones an object including only the provided set of fields.
     * Uses Apache Commons PropertyUtils (getter/setter access).
     *
     * @param source         The source object
     * @param targetClass    The class of the target object
     * @param includedFields Set of field names to include in the clone
     * @param <T>            Type of the object
     * @return Cloned object with only selected fields populated
     */
    public static <T> T cloneWithFields(Object source, Class<T> targetClass, Set<String> includedFields) {
        if (source == null) return null;

        try {
            T target = targetClass.getDeclaredConstructor().newInstance();

            BeanWrapperImpl sourceWrapper = new BeanWrapperImpl(source);
            BeanWrapperImpl targetWrapper = new BeanWrapperImpl(target);

            for (String field : includedFields) {
                if (sourceWrapper.isReadableProperty(field) && targetWrapper.isWritableProperty(field)) {
                    Object value = sourceWrapper.getPropertyValue(field);
                    targetWrapper.setPropertyValue(field, value);
                }
            }

            return target;

        } catch (Exception e) {
            throw new RuntimeException("Failed to clone object with selected fields", e);
        }
    }
}
