package com.ruchira.murex.service;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Configuration service for transformation parameters
 * Centralizes business rule configuration to avoid hardcoding
 */
@Service

public class TransformationConfigService {

    @Value("${transformation.ref-trade.default-rate:0.9}")
    private double defaultRefTradeRate;

    @Getter
    @Value("${transformation.decimal.scale:6}")
    private int decimalScale;

    /**
     * Get the reference trade rate (configurable)
     */
    public BigDecimal getRefTradeRate() {
        return BigDecimal.valueOf(defaultRefTradeRate);
    }

    /**
     * Get rounding mode for BigDecimal operations
     */
    public RoundingMode getRoundingMode() {
        return RoundingMode.HALF_UP; // Default, could be configurable
    }
}