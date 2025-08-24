package com.ruchira.murex.model.trade;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class MurexTradeLeg {

    private String dealCcy;
    private BigDecimal dealAmount;
    private String bsIndicator;
    private BigDecimal clientForwardRate;
    private BigDecimal clientSpotRate;
    private BigDecimal initPrice;
    private BigDecimal clientRate;
    private String fwswPoints;
    private BigDecimal salesMarginAmount;
    private String salesMarginCcy;
    private LocalDate valueDate;
    private LocalDate fixDate;
    private List<MurexTradeLegComponent> components;
    private MurexTradeLegAdditionalFields additionalFields;
}
