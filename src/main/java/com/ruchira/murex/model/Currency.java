package com.ruchira.murex.model;

import lombok.Data;

@Data
public class Currency {
    private String originalCurrency;
    private String functionalCurrency;
    private String currencyCategory;
}
