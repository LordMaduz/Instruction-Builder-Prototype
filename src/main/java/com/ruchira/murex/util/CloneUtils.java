package com.ruchira.murex.util;

import com.ruchira.murex.exception.BusinessException;
import lombok.experimental.UtilityClass;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Set;

@UtilityClass
public class CloneUtils {

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
            throw new BusinessException("Failed to clone object with selected fields", e);
        }
    }


}
