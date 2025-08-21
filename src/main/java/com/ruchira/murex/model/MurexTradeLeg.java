package com.ruchira.murex.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class MurexTradeLeg {
    
    // Core identification fields
    private String externalDealId;
    private String typology;
    private String navType;
    private String murexBookCode;
    
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
    private String comment1;
    private String murexComment;

    //additional
    private String outboundProduct;
    private String familyGroupType;
    private String traderId;
    private String sourceSystem;

    
    // Dates
    private LocalDate tradeDate;
    private LocalDate valueDate;
    private LocalDate maturityDate;

}