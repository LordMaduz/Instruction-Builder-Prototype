package com.ruchira.murex.util;

import com.ruchira.murex.exception.BusinessException;
import com.ruchira.murex.model.TransformedMurexTrade;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.ObjectUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static com.ruchira.murex.constant.Constants.*;
import static com.ruchira.murex.constant.Constants.BLENDED_HISTORICAL_EXCHANGE_RATE;

@UtilityClass
public class MurexTradingHelper {

    public String determineDealCurrency(TransformedMurexTrade source) {
        return Optional.ofNullable(source)
                .map(s -> ObjectUtils.isNotEmpty(s.getBsIndicator()) && SELL_INDICATOR.equalsIgnoreCase(s.getBsIndicator())
                        ? s.getCurr2()
                        : s.getCurr1())
                .orElseThrow(() -> new BusinessException("Nullable TransformedMurexTrade received when populating dealCurrency"));
    }

    public BigDecimal determineDealAmount(TransformedMurexTrade source) {
        return Optional.ofNullable(source)
                .map(s -> ObjectUtils.isNotEmpty(s.getBsIndicator()) && SELL_INDICATOR.equalsIgnoreCase(s.getBsIndicator())
                        ? s.getSellTransAmt()
                        : s.getBuyTransAmt())
                .orElseThrow(() -> new BusinessException("Nullable TransformedMurexTrade received when populating dealAmount"));
    }

    public BigDecimal determineClientSpotRate(TransformedMurexTrade source) {
        if (ObjectUtils.isNotEmpty(source.getOutboundProduct()) && SPOT_OUTBOUND_GROUP.equals(source.getOutboundProduct())) {
            if (BLENDED_HISTORICAL_EXCHANGE_RATE.equals(source.getExchangeRateType())) {
                return source.getHistoricalExchangeRate();
            }
            return source.getSpotRate();
        }
        return BigDecimal.ZERO;
    }

    public BigDecimal determineClientRate(TransformedMurexTrade source) {
        if (ObjectUtils.isNotEmpty(source.getExchangeRateType()) && BLENDED_HISTORICAL_EXCHANGE_RATE.equals(source.getExchangeRateType())) {
            return source.getHistoricalExchangeRate();
        }
        return source.getSpotRate();

    }
}
