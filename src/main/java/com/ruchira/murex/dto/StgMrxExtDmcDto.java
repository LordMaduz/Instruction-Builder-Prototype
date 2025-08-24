package com.ruchira.murex.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * DTO representing a DMC (Data Management Configuration) Record
 * This class encapsulates the data structure for DMC records generated
 * during the Murex booking transformation process.
 */
@Data
public class StgMrxExtDmcDto {

    private String txnId;
    private String mxProdCd;
    private String dealUdfPcCode;
    private String ctpyRelnship;
    private String ctpy;
    private LocalDate nextRolloverDate;
    private LocalDate valueDte;
    private LocalDate maturityDte;
    private LocalDate transDte; //
    private LocalDate pymtDte;
    private BigDecimal actualIntSpread;
    private BigDecimal buyTransAmt;
    private BigDecimal sellTransAmt;
    private BigDecimal mtmTransAmt;
    private String curr1;
    private String curr2;
    private String cptyLocCtry;
    private String instrumentCode;
    private String mrxEntityId;
    private String currBizUnit;
    private LocalDate nextRepriceDte;
    private String dealStatus;
    private LocalDate mktOpLastDte;
    private String issuerCode;
    private BigDecimal exchRate;
    private String exchTradedInd;
    private String swapLegInd;
    private String spotForward;
    private String deliverable;
    private String issuer;
    private String salesPersonId;
    private String tradingPortf;
    private String mtmCurr;
    private String origCurr;
    private BigDecimal liveAmt;
    private String cancelReissueInd;
    private BigDecimal delta;
    private BigDecimal marketValue;
    private String bsIndicator;
    private BigDecimal dirtyPrice;
    private BigDecimal discountdMktVal;
    private String discountdMktValCcy;
    private BigDecimal pvOnCg;
    private String extRef;
    private String nondiscMv;
    private BigDecimal pvEffect;
    private LocalDate lastCalcDte;
    private String nondiscMvD;
    private String liveQty;
    private BigDecimal pastCashCap;
    private BigDecimal upl;
    private BigDecimal realizePlFut;
    private BigDecimal fwswPoints;
    private BigDecimal nomL1Orig;
    private BigDecimal nomL2Orig;
    private BigDecimal discNpvL1;
    private BigDecimal discNpvL2;
    private BigDecimal unrealPlL1;
    private BigDecimal unrealPlL2;
    private BigDecimal initPrice;
    private BigDecimal pl;
    private BigDecimal mktPrice;
    private String mvCurr;
    private LocalDate fixDate;
    private String ntdsgPortf;
    private String comment0;
    private BigDecimal spotRate;
    private String comment1;
    private String comment2;
    private BigDecimal unreCapGain;
    private BigDecimal liveAmt2;
    private BigDecimal initialQty;
    private LocalDateTime dealTime;
    private BigDecimal marketSpotRate1;
    private BigDecimal marketSpotRate2;
    private String contract;
    private String typologyMx3;
    private String dealNo;
    private String origContractRef;
    private String legalBu;
    private BigDecimal salesMarginUsd;
    private String salesMarginCurrUsd;
    private String sourceDataLocCd;
    private String productCode;
    private LocalDate dlBusinessdate;

    // --- hn fields ---
    private BigDecimal historicalExchangeRate;

    // Observability Fields
    private String traceId;
    private String instructionRuleId;
    private String murexBookCode;

    // Audit Fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
