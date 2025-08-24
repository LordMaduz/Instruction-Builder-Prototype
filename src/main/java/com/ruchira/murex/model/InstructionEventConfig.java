package com.ruchira.murex.model;

import lombok.Data;

@Data
public class InstructionEventConfig {

    private String ruleId;
    private String description;
    private String businessEvent;
    private String hedgeMethod;
    private String currencyType;
    private String navType;
    private String hedgingInstrument;
    private String mrxBookCodeIds;
    private Byte status;
}