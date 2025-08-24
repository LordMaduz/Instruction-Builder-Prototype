package com.ruchira.murex.dto;

import lombok.Data;


@Data
public class InstructionRequestDto {
    private String businessDate;
    private String instructionEvent;
    private String hedgeMethod;
    private String currency;
    private String hedgeInstrumentType;
    private String externalTradeIds;
}
