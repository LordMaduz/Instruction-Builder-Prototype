package com.ruchira.murex.kafka.model;

import lombok.Data;

@Data
public class HawkMurexBookingTradeLegAdditionalFields {
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
