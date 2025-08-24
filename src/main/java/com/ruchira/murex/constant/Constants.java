package com.ruchira.murex.constant;


public final class Constants {

    //Transformation Fields
    public static final String FLIP_CURRENCY_TRANSFORMATION_KEY = "flipCurrency";
    public static final String OUTBOUND_CURRENCY_CHANGE_TRANSFORMATION_KEY = "outboundCurrChange";
    public static final String EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY = "exchangeRates";
    public static final String COMMENT_0_KEYWORD = "comment0";
    public static final String COMMENT_1_KEYWORD = "comment1";
    public static final String TABLE_KEYWORD = "table";
    public static final String FIELD_NAME_KEYWORD = "fieldName";
    public static final String FIELD_TRACE_ID = "traceId";
    public static final String FIELD_MUREX_BOOK_CODE = "murexBookCode";
    public static final String MUREX_BOOK_CODE_TABLE_KEYWORD = "murexBookCodeTable";
    public static final String TPS_FIELDS_KEYWORD = "tpsFields";
    public static final String REFERENCE_TRADE_FIELD = "referenceTrade";
    public static final String REFERENCE_SUB_TRADE_NEAR_LEG_FIELD = "nearLeg";
    public static final String REFERENCE_SUB_TRADE_FAR_LEG_FIELD = "farLeg";
    public static final String REFERENCE_TRADE_BUY_SELL_FIELD = "referenceTradeBuySell";
    public static final String REFERENCE__SUB_TRADE_BUY_SELL_FIELD = "referenceSubTradeBuySell";
    public static final String TRADE_BUY_FIELD = "buy";
    public static final String TRADE_SELL_FIELD = "sell";

    // supported Typologies
    public static final String FX_SPOT_TYPOLOGY = "FX Spot";
    public static final String FX_SWAP_TYPOLOGY = "FX Swap";
    public static final String FX_NDF_TYPOLOGY = "FX NDF";
    public static final String FUNCTIONAL_CURRENCY_USD = "USD";

    // Transformation metaData fields
    public static final String TRADING_PORTFOLIO_SG_BANK_SFX = "SG BANK SFX";
    public static final String SELL_INDICATOR = "S";
    public static final String SPOT_OUTBOUND_GROUP = "SPOT";

    public static final String NEAR_LEG_TYPE = "NEAR_LEG";
    public static final String FAR_LEG_TYPE = "FAR_LEG";

    public static final String REF_TRADE_EXCHANGE_RATE = "REF_TRADE";
    public static final String BLENDED_HISTORICAL_EXCHANGE_RATE = "BLEND_HISTFX";

    // FTL File names
    public static final String FETCH_BUSINESS_EVENT_RULE_FTL_FILE = "fetchInstructionEventConfig.ftl";
    public static final String FETCH_MUREX_BOOK_CODES_FTL_FILE = "fetchMurexBookCodes.ftl";
    public static final String FETCH_CURRENCY_CONFIG_FTL_FILE = "fetchCurrencyConfig.ftl";
    public static final String INSERT_DATA_TO_STG_MTX_EXT_DMC_FTL_FILE = "stgMrxExtDmcInsertData.ftl";
    public static final String INSERT_DATA_TO_MUREX_BOOKING_FTL_FILE = "murexBookingInsert.ftl";
    public static final String INSERT_DATA_TO_MUREX_BOOK_TRADE_LEG_FTL_FILE = "murexBookingTradeLegInsert.ftl";
    public static final String INSERT_DATA_TO_MUREX_BOOK_TRADE_LEG_COMPONENTS_FTL_FILE = "murexBookingTradeLegComponentInsert.ftl";
}
