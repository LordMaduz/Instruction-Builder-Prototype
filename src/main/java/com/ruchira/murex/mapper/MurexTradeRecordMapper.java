package com.ruchira.murex.mapper;

import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import com.ruchira.murex.model.trade.MurexTradeLegAdditionalFields;
import com.ruchira.murex.model.trade.MurexTradeLegComponent;
import com.ruchira.murex.util.MurexTradingHelper;
import com.ruchira.murex.model.*;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = MurexTradingHelper.class)
public interface MurexTradeRecordMapper {

    @Mapping(source = "traceId", target = "tradeReference")
    @Mapping(source = "transDte", target = "tradeExecutionDate")
    @Mapping(source = "dealTime", target = "tradeExecutionTime")
    @Mapping(source = "outboundProduct", target = "dealType")
    @Mapping(source = "tradingPortf", target = "sourcePortfolio")
    @Mapping(source = "ctpy", target = "destinationPortfolio")
    @Mapping(source = "familyGrpType", target = "familyGrpType")
    MurexTrade toMurexTrade(TransformedMurexTrade source);

    @Mapping(target = "dealCcy", expression = "java(MurexTradingHelper.determineDealCurrency(source))")
    @Mapping(target = "dealAmount", expression = "java(MurexTradingHelper.determineDealAmount(source))")
    @Mapping(source = "bsIndicator", target = "bsIndicator")
    @Mapping(source = "initPrice", target = "initPrice")
    @Mapping(target = "clientSpotRate", expression = "java(MurexTradingHelper.determineClientSpotRate(source))")
    @Mapping(target = "clientRate", expression = "java(MurexTradingHelper.determineClientRate(source))")
    @Mapping(source = "valueDte", target = "valueDate")
    @Mapping(source = "fixDate", target = "fixDate")
    MurexTradeLeg toMurexTradeLeg(TransformedMurexTrade source);


    @Mapping(source = "instrumentCode", target = "currencyPair")
    @Mapping(source = "marketSpotRate1", target = "marketSpotRate")
    MurexTradeLegComponent toMurexTradeLegComponent(TransformedMurexTrade source);


    @Mapping(source = "sourceSystem", target = "sourceSystem")
    @Mapping(source = "traderId", target = "traderId")
    @Mapping(source = "txnId", target = "origContractRef")
    @Mapping(source = "ctpy", target = "counterPartyCode")
    MurexTradeLegAdditionalFields toMurexTradeLegAdditionalFields(TransformedMurexTrade source);

}
