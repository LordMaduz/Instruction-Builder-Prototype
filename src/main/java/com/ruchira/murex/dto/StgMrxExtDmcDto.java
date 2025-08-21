package com.ruchira.murex.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing a DMC (Data Management Configuration) Record
 * This class encapsulates the data structure for DMC records generated
 * during the Murex booking transformation process.
 */
@Data
public class StgMrxExtDmcDto {

    // Core identification fields
    private String externalDealId;
    private String typology;
    private String navType;

    // Currency and amount fields
    private String currency1;
    private String currency2;
    private BigDecimal buyTransAmt;
    private BigDecimal sellTransAmt;
    private BigDecimal hedgeAmountAllocation;
    private BigDecimal exchangeRate;
    private BigDecimal historicalExchangeRate;

    // Business fields
    private String portfolio;
    private String counterParty;
    private String comment0;
    private String murexComment;

    // Dates
    private LocalDate tradeDate;
    private LocalDate valueDate;
    private LocalDate maturityDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private String traceId;
    private String instructionConfigRuleId;
    private String murexBookingCode;
}
