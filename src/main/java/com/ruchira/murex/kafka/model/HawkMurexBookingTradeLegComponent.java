package com.ruchira.murex.kafka.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class HawkMurexBookingTradeLegComponent {
    private String currencyPair;
    private BigDecimal marketSpotRate;
    private BigDecimal marketForwardRate;
    private LocalDate spotValueDate;
}
