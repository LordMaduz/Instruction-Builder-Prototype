package com.ruchira.murex.constant;

import java.util.List;
import java.util.Set;

public final class Constants {
    public final static List<String> TPS_FIELDS_TO_IGNORE = List.of("comment0", "comment1");
    public final static Set<String> DEFAULT_TPS_FIELDS_TO_INCLUDE = Set.of("outboundProduct", "familyGroupType",
            "portfolio", "counterParty", "traderId", "sourceSystem", "comment0", "comment1");

    //Transformation Fields
    public static final String FLIP_CURRENCY_TRANSFORMATION_KEY = "flipCurrency";
    public static final String OUTBOUND_CURRENCY_CHANGE_TRANSFORMATION_KEY = "outboundCurrChange";
    public static final String EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY = "exchangeRates";
}
