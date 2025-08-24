package com.ruchira.murex.mapper;

import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.model.AggregatedDataResponse;
import com.ruchira.murex.model.TransformedMurexTrade;
import org.apache.commons.lang3.ObjectUtils;
import org.mapstruct.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.util.Map;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface DynamicMapper {

    StgMrxExtDmcDto mapToDmcDto(TransformedMurexTrade dataResponse);

    TransformedMurexTrade mapToMurexTradeLeg(AggregatedDataResponse dataResponse, @Context Map<String, Object> overrides);

    TransformedMurexTrade mapToMurexTradeLeg(AggregatedDataResponse dataResponse);

    TransformedMurexTrade clone(TransformedMurexTrade transformedMurexTrade);

    @AfterMapping
    default <S, T> void overrideValues(S source, @MappingTarget T target, @Context Map<String, Object> overrides) {
        Logger log = LoggerFactory.getLogger(DynamicMapper.class);

        if (ObjectUtils.isNotEmpty(overrides)) {
            BeanWrapper wrapper = new BeanWrapperImpl(target);
            overrides.forEach((key, value) -> {
                if (wrapper.isWritableProperty(key)) {
                    wrapper.setPropertyValue(key, value);
                    log.debug("Overridden property '{}' with value '{}' on {}", key, value, target.getClass().getSimpleName());
                } else {
                    log.debug("Skipping override: property '{}' not found on {}", key, target.getClass().getSimpleName());
                }
            });
        }
    }

}
