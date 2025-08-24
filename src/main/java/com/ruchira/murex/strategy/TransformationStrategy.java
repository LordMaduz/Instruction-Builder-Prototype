package com.ruchira.murex.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruchira.murex.config.TransformationFieldConfig;
import com.ruchira.murex.mapper.DynamicMapper;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.mapper.MurexTradeRecordMapper;
import com.ruchira.murex.model.MurexBookingConfig;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.TransformedMurexTrade;
import com.ruchira.murex.model.TransformationContext;
import com.ruchira.murex.parser.JsonParser;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.ruchira.murex.util.CloneUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static com.ruchira.murex.constant.Constants.*;
import static java.math.BigDecimal.ZERO;

/**
 * Strategy interface for different transformation types
 * Follows Strategy pattern to enable extensible transformation logic
 */
@RequiredArgsConstructor
public abstract class TransformationStrategy {

    protected final MurexTradeRecordMapper murexTradeRecordMapper;
    protected final DynamicMapper dynamicMapper;
    protected final DynamicFieldParser fieldMapper;
    protected final JsonParser jsonParser;
    protected final TransformationFieldConfig transformationFieldConfig;
    protected final StgMrxExtProcessingService stgMrxExtProcessingService;
    protected final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Check if this strategy can handle the given typology
     *
     * @param typology The typology to check
     * @return true if this strategy supports the typology
     */
    public abstract boolean supports(String typology);

    /**
     * Applies a transformation strategy to generate booking  and related trade details.
     *
     * <p>The transformation logic is driven by the provided {@link TransformationContext},
     * which encapsulates all necessary inputs such as the grouped record, filtered Murex
     * configurations, input currency, and rule identifiers. The output includes both
     * staging booking and transformed trade entities.</p>
     *
     * @param transformationContext Context object containing all parameters required
     *                              to execute the transformation logic
     * @return A pair consisting of:
     * <ul>
     *     <li>List of {@link StgMrxExtDmcDto} booking DTOs generated from before applying the transformation</li>
     *     <li>List of {@link MurexTrade} trade details produced alongside the DTOs</li>
     * </ul>
     */
    public abstract Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(TransformationContext transformationContext);

    /**
     * Get the transformation type name
     *
     * @return The name of this transformation strategy
     */
    public abstract String getTransformationType();

    public void applyIndividualTransformation(TransformedMurexTrade booking, JsonNode transformation,
                                              TransformationContext transformationContext) {

        boolean flipCurrency = transformation.has(FLIP_CURRENCY_TRANSFORMATION_KEY) &&
                transformation.get(FLIP_CURRENCY_TRANSFORMATION_KEY).asBoolean();
        boolean outboundCurrencyChange = transformation.has(OUTBOUND_CURRENCY_CHANGE_TRANSFORMATION_KEY) &&
                transformation.get(OUTBOUND_CURRENCY_CHANGE_TRANSFORMATION_KEY).asBoolean();


        BigDecimal exchangeRate = determineExchangeRate(transformation, booking, flipCurrency);

        if (flipCurrency) {
            applyFlipCurrencyLogic(booking, exchangeRate, transformationContext.getCurrenciesInFamily());
        } else {
            applyNormalTransformationLogic(booking, exchangeRate, transformationContext.getCurrenciesInFamily());
        }

        applyOutBoundCurrencyChange(booking, outboundCurrencyChange, transformationContext);

    }

    public void applyOutBoundCurrencyChange(
            TransformedMurexTrade booking, boolean outboundCurrencyChange,
            TransformationContext transformationContext
    ) {

        if (outboundCurrencyChange) {
            String currency1 = booking.getCurr1();
            String currency2 = booking.getCurr2();

            List<String> currenciesInFamily = transformationContext.getCurrenciesInFamily();
            String flipCurrencyVariant = transformationContext.getFlipCurrencyVariant();

            if (currenciesInFamily.contains(currency1)) {
                booking.setCurr1(flipCurrencyVariant);
            } else if (currenciesInFamily.contains(currency2)) {
                booking.setCurr2(flipCurrencyVariant);
            }
        }
    }

    public BigDecimal determineExchangeRate(JsonNode transformation, TransformedMurexTrade booking, boolean flipCurrency) {
        String exchangeRatesConfig = null;

        if (transformation.has(EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY)) {
            exchangeRatesConfig = transformation.get(EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY).asText();
        }

        BigDecimal rate;

        if (exchangeRatesConfig != null && exchangeRatesConfig.startsWith(REF_TRADE_EXCHANGE_RATE)) {
            booking.setExchangeRateType(REF_TRADE_EXCHANGE_RATE);
            rate = booking.getSpotRate();
            if (rate == null) rate = ZERO;
        } else if (exchangeRatesConfig != null && exchangeRatesConfig.startsWith(BLENDED_HISTORICAL_EXCHANGE_RATE)) {
            booking.setExchangeRateType(BLENDED_HISTORICAL_EXCHANGE_RATE);
            rate = booking.getHistoricalExchangeRate();
            if (rate == null) rate = ZERO;
        } else {
            booking.setExchangeRateType(BLENDED_HISTORICAL_EXCHANGE_RATE);
            rate = booking.getHistoricalExchangeRate();
            if (rate == null) rate = ZERO;
        }

        if (flipCurrency && rate.compareTo(ZERO) != 0) {
            rate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP);
        }

        return rate;
    }

    private void applyNormalTransformationLogic(TransformedMurexTrade booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = booking.getHedgeAmtAllocation();
        if (hedgeAmount == null) hedgeAmount = ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = booking.getCurr1();
        String currency2 = booking.getCurr2();

        if (currenciesInFamily.contains(currency1)) {
            booking.setBuyTransAmt(hedgeAmount);
            booking.setSellTransAmt(hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency2)) {
            booking.setSellTransAmt(hedgeAmount);
            booking.setBuyTransAmt(hedgeAmount.multiply(exchangeRate));
        }
    }

    private void applyFlipCurrencyLogic(TransformedMurexTrade booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = booking.getHedgeAmtAllocation();
        if (hedgeAmount == null) hedgeAmount = ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = booking.getCurr1();
        String currency2 = booking.getCurr2();

        // Inverted logic for flipCurrency = true
        if (currenciesInFamily.contains(currency2)) {
            booking.setBuyTransAmt(hedgeAmount);
            booking.setSellTransAmt(hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency1)) {
            booking.setSellTransAmt(hedgeAmount);
            booking.setBuyTransAmt(hedgeAmount.multiply(exchangeRate));
        }
    }

    public void applyOutputCustomizations(TransformedMurexTrade booking, MurexBookingConfig config) {
        try {
            String tpsOutboundJson = config.getTpsOutbound();
            if (tpsOutboundJson == null || tpsOutboundJson.trim().isEmpty()) {
                return;
            }

            JsonNode tpsOutboundNode = objectMapper.readTree(tpsOutboundJson);

            // Special handling for comment fields
            applySpecialCommentHandling(booking, tpsOutboundNode, config);

            // Apply general field customizations using dynamic mapping
            fieldMapper.applyFieldMappings(booking, tpsOutboundNode, transformationFieldConfig.getIgnoreFields());

        } catch (Exception e) {
            throw new TransformationException("Error applying output customizations", getTransformationType(), e);
        }
    }

    private void applySpecialCommentHandling(TransformedMurexTrade booking, JsonNode tpsOutboundNode, MurexBookingConfig config) {
        // Special handling for comment0 field with n-way concatenation
        if (tpsOutboundNode.has(COMMENT_0_KEYWORD)) {
            JsonNode comment0Array = tpsOutboundNode.get(COMMENT_0_KEYWORD);

            if (comment0Array.isArray()) {
                List<String> values = new ArrayList<>();

                for (JsonNode comment0Config : comment0Array) {
                    if (comment0Config.has(FIELD_NAME_KEYWORD)) {
                        // Handle multiple fieldName entries within the same config object
                        JsonNode fieldNameNode = comment0Config.get(FIELD_NAME_KEYWORD);
                        // Single fieldName
                        String fieldName = fieldNameNode.asText();
                        Object value = getFieldValueFromTable(booking, comment0Config, fieldName);
                        if (value != null && !value.toString().trim().isEmpty()) {
                            values.add(value.toString());
                        }
                    }
                }

                // N-way concatenation with " | " separator
                if (!values.isEmpty()) {
                    fieldMapper.setFieldValue(booking, COMMENT_0_KEYWORD, String.join(" | ", values));
                }
            }
        }

        // Special handling for comment1 field
        if (tpsOutboundNode.has(COMMENT_1_KEYWORD)) {
            JsonNode comment1Array = tpsOutboundNode.get(COMMENT_1_KEYWORD);

            if (comment1Array.isArray()) {
                List<String> values = new ArrayList<>();

                for (JsonNode comment1Config : comment1Array) {
                    if (comment1Config.has(FIELD_NAME_KEYWORD)) {
                        String fieldName = comment1Config.get(FIELD_NAME_KEYWORD).asText();
                        Object value = getFieldValueFromTable(booking, comment1Config, fieldName);
                        if (value != null && !value.toString().trim().isEmpty()) {
                            values.add(value.toString());
                        }
                    }
                }

                // For comment1, use the first value or join if multiple
                if (!values.isEmpty()) {
                    fieldMapper.setFieldValue(booking, COMMENT_1_KEYWORD, values.size() == 1 ? values.getFirst() : String.join(" | ", values));
                }
            }
        }
    }

    /**
     * Get field value considering the table context from configuration
     *
     * @param booking   The TransformedMurexTrade object
     * @param config    The configuration node containing table and fieldName
     * @param fieldName The field name to retrieve
     * @return The field value or special handling based on table context
     */
    private Object getFieldValueFromTable(TransformedMurexTrade booking, JsonNode config, String fieldName) {
        String table = config.has(TABLE_KEYWORD) ? config.get(TABLE_KEYWORD).asText() : null;

        // Handle different table contexts currently support fields from
        // Entity Table and MurexBookCode from MurexBookCode table
        if (MUREX_BOOK_CODE_TABLE_KEYWORD.equals(table) && FIELD_MUREX_BOOK_CODE.equals(fieldName)) {
            // For murexBookCode table, return the murexBookCode value
            return fieldMapper.getFieldValue(booking, FIELD_MUREX_BOOK_CODE);
        }

        // Default: get field value directly from booking
        return fieldMapper.getFieldValue(booking, fieldName);
    }

    public TransformedMurexTrade applyTPSFieldTransformations(TransformedMurexTrade booking, MurexBookingConfig config) {

        try {
            String transformations = config.getTransformations();
            if (ObjectUtils.isEmpty(transformations)) {
                return booking;
            }
            JsonNode transformationNode = jsonParser.getFirstTransformation(transformations);

            Set<String> tpdFieldSet = new HashSet<>();

            JsonNode tpsFields = transformationNode.get(TPS_FIELDS_KEYWORD);
            tpdFieldSet.addAll(jsonArrayToStringSet(tpsFields));
            tpdFieldSet.addAll(transformationFieldConfig.getIncludeFields());

            return CloneUtils.cloneWithFields(booking, TransformedMurexTrade.class, tpdFieldSet);
        } catch (Exception e) {
            throw new TransformationException("Error applying output customizations", getTransformationType(), e);
        }
    }

    public Set<String> jsonArrayToStringSet(JsonNode node) {
        Object obj = fieldMapper.extractValueFromJsonNode(node);
        if (!(obj instanceof List<?> list)) return Set.of();
        return list.stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
    }
}