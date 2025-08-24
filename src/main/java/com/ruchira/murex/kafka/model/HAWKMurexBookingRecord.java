package com.ruchira.murex.kafka.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HAWKMurexBookingRecord {
    private String externalReference;
    private LocalDate transDate;
    private LocalDateTime dealTime;
    private String dealType;
    private String murexShortLabel;
    private String tradingPortf;
    private String regionalPortfolio;
    private String destinationPortfolio;
    private String internal = "Y";
    private String intermediaryPortfolio;
    private String brokerLabel;
    private String splitCross;
    private String splitSpotSwap;
    private String familyGrpType;
    private HAWKMurexBookingTradeLeg nearLeg;
    private HAWKMurexBookingTradeLeg farLeg;
}
