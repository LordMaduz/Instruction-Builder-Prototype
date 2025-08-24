package com.ruchira.murex.kafka.model;


import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class HAWKMurexBookingTradeLeg {
    private String dealCcy;
    private BigDecimal dealAmount;
    private String bsIndicator;
    private BigDecimal forwardRate;
    private BigDecimal spotRate;
    private BigDecimal initPrice;
    private BigDecimal exchRate;
    private String fwswPoints;
    private BigDecimal salesMarginAmount;
    private String salesMarginCcy;
    private LocalDate valueDate;
    private LocalDate fixDate;
    private List<HawkMurexBookingTradeLegComponent> components;
    private HawkMurexBookingTradeLegAdditionalFields additionalFields;
}
