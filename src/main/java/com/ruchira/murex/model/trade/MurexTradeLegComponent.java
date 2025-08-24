package com.ruchira.murex.model.trade;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MurexTradeLegComponent {

    private String currencyPair;
    private BigDecimal marketSpotRate;
    private BigDecimal marketForwardRate;
    private LocalDate spotValueDate;
}
