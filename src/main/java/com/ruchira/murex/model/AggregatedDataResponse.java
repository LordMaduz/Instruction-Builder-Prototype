package com.ruchira.murex.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AggregatedDataResponse {
    
    private String externalDealId;
    private String comment0;
    private String counterParty;
    private String typology;
    private String murexComment;
    private String portfolio;
    private LocalDate valueDate;
    private LocalDate maturityDate;
    private BigDecimal stgExchangeRate;
    private LocalDate transDate;
    private String currency1;
    private String currency2;
    private BigDecimal buyTransAmt;
    private BigDecimal sellTransAmt;
    private BigDecimal historicalExchangeRate;
    private LocalDate businessDate;
    private String currency;
    private BigDecimal hedgeAmountAllocation;
    private String entityName;
    private String navType;
    private String entityType;
    private String entityId;
}