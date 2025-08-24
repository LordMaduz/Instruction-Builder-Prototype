package com.ruchira.murex.mapper;

import com.ruchira.murex.kafka.model.HAWKMurexBookingRecord;
import com.ruchira.murex.kafka.model.HAWKMurexBookingTradeLeg;
import com.ruchira.murex.kafka.model.HawkMurexBookingTradeLegAdditionalFields;
import com.ruchira.murex.kafka.model.HawkMurexBookingTradeLegComponent;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import com.ruchira.murex.model.trade.MurexTradeLegAdditionalFields;
import com.ruchira.murex.model.trade.MurexTradeLegComponent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValueMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface HawkMurexBookingMapper {

    @Mapping(source = "tradeReference", target = "externalReference")
    @Mapping(source = "tradeExecutionDate", target = "transDate")
    @Mapping(source = "tradeExecutionTime", target = "dealTime")
    @Mapping(source = "sourcePortfolio", target = "tradingPortf")
    @Mapping(target = "nearLeg", ignore = true)
    @Mapping(target = "farLeg", ignore = true)
    HAWKMurexBookingRecord toHawkMurexBooking(MurexTrade source);

    @Mapping(source = "clientForwardRate", target = "forwardRate")
    @Mapping(source = "clientSpotRate", target = "spotRate")
    @Mapping(source = "clientRate", target = "exchRate")
    HAWKMurexBookingTradeLeg toHawkMurexTradeLeg(MurexTradeLeg source);

    List<HawkMurexBookingTradeLegComponent> toHawkMurexTradeLegComponents(List<MurexTradeLegComponent> source);

    HawkMurexBookingTradeLegAdditionalFields toHawkMurexTradeLegAdditionalFields(MurexTradeLegAdditionalFields source);
}
