package com.ruchira.murex.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

/**
 * Configuration service for transformation parameters
 * Centralizes business rule configuration to avoid hardcoding
 */
@Configuration
@ConfigurationProperties(prefix = "app.tps.fields")
@Data
public class TransformationFieldConfig {

    private List<String> ignoreFields;
    private Set<String> includeFields;

    /**
     * Get rounding mode for BigDecimal operations
     */
    public RoundingMode getRoundingMode() {
        return RoundingMode.HALF_UP; // Default, could be configurable
    }
}
