package com.ruchira.murex.model.trade;


import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class MurexTrade {

    private String tradeReference;
    private LocalDate tradeExecutionDate;
    private LocalDateTime tradeExecutionTime;
    private String dealType;
    private String murexShortLabel;
    private String sourcePortfolio;
    private String regionalPortfolio;
    private String destinationPortfolio;
    private String internal = "Y";
    private String intermediaryPortfolio;
    private String brokerLabel;
    private String splitCross;
    private String splitSpotSwap;
    private String familyGrpType;
    private MurexTradeLeg nearLeg;
    private MurexTradeLeg farLeg;

}
