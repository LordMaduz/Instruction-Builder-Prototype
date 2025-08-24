package com.ruchira.murex.model.trade;

import lombok.Data;

@Data
public class MurexTradeLegAdditionalFields {
    private String executionVenue;
    private String sourceSystem;
    private String broker;
    private String makerOrTaker;
    private String traderId;
    private String origContractRef;
    private String desk;
    private String counterPartyCode;
    private String tradeLegType;
    private String comment0;
    private String comment1;
    private String comment2;
}

