package com.ruchira.murex.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruchira.murex.constant.Constants;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.model.MurexBookingConfig;
import com.ruchira.murex.model.MurexTrade;
import com.ruchira.murex.model.MurexTradeLeg;
import com.ruchira.murex.model.TransformationContext;
import com.ruchira.murex.parser.JsonTransformationParser;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.ruchira.murex.util.CopyUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.ruchira.murex.constant.Constants.*;

/**
 * Strategy interface for different transformation types
 * Follows Strategy pattern to enable extensible transformation logic
 */
@RequiredArgsConstructor
public abstract class TransformationStrategy {

    protected final DynamicFieldParser fieldMapper;
    protected final JsonTransformationParser jsonTransformationParser;
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
     * Applies a transformation strategy to generate booking DTOs and related trade details.
     *
     * <p>The transformation logic is driven by the provided {@link TransformationContext},
     * which encapsulates all necessary inputs such as the grouped record, filtered Murex
     * configurations, input currency, and rule identifiers. The output includes both
     * staging booking DTOs and transformed trade entities.</p>
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

    public void applyIndividualTransformation(MurexTradeLeg booking, JsonNode transformation,
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
            MurexTradeLeg booking, boolean outboundCurrencyChange,
            TransformationContext transformationContext
    ) {

        if (outboundCurrencyChange) {
            String currency1 = booking.getCurrency1();
            String currency2 = booking.getCurrency2();

            List<String> currenciesInFamily = transformationContext.getCurrenciesInFamily();
            String flipCurrencyVariant = transformationContext.getFlipCurrencyVariant();

            if (currenciesInFamily.contains(currency1)) {
                booking.setCurrency1(flipCurrencyVariant);
            } else if (currenciesInFamily.contains(currency2)) {
                booking.setCurrency2(flipCurrencyVariant);
            }
        }
    }

    public BigDecimal determineExchangeRate(JsonNode transformation, MurexTradeLeg booking, boolean flipCurrency) {
        String exchangeRatesConfig = null;

        if (transformation.has(EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY)) {
            exchangeRatesConfig = transformation.get(EXCHANGE_RATE_TYPE_TRANSFORMATION_KEY).asText();
        }

        BigDecimal rate;

        if (exchangeRatesConfig != null && exchangeRatesConfig.startsWith("REF_TRADE")) {
            rate = BigDecimal.valueOf(0.9); // Configurable rate
        } else if (exchangeRatesConfig != null && exchangeRatesConfig.startsWith("BLENDED_HISTORICAL")) {
            rate = booking.getHistoricalExchangeRate();
            if (rate == null) rate = BigDecimal.ONE;
        } else {
            rate = booking.getHistoricalExchangeRate();
            if (rate == null) rate = BigDecimal.ONE;
        }

        if (flipCurrency && rate.compareTo(BigDecimal.ZERO) != 0) {
            rate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP);
        }

        return rate;
    }

    private void applyNormalTransformationLogic(MurexTradeLeg booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = booking.getHedgeAmountAllocation();
        if (hedgeAmount == null) hedgeAmount = BigDecimal.ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = booking.getCurrency1();
        String currency2 = booking.getCurrency2();

        if (currenciesInFamily.contains(currency1)) {
            booking.setBuyTransAmt(hedgeAmount);
            booking.setSellTransAmt(hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency2)) {
            booking.setSellTransAmt(hedgeAmount);
            booking.setBuyTransAmt(hedgeAmount.multiply(exchangeRate));
        }
    }

    private void applyFlipCurrencyLogic(MurexTradeLeg booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = booking.getHedgeAmountAllocation();
        if (hedgeAmount == null) hedgeAmount = BigDecimal.ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = booking.getCurrency1();
        String currency2 = booking.getCurrency2();

        // Inverted logic for flipCurrency = true
        if (currenciesInFamily.contains(currency2)) {
            booking.setBuyTransAmt(hedgeAmount);
            booking.setSellTransAmt(hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency1)) {
            booking.setSellTransAmt(hedgeAmount);
            booking.setBuyTransAmt(hedgeAmount.multiply(exchangeRate));
        }
    }

    public void applyOutputCustomizations(MurexTradeLeg booking, MurexBookingConfig config) {
        try {
            String tpsOutboundJson = config.getTpsOutbound();
            if (tpsOutboundJson == null || tpsOutboundJson.trim().isEmpty()) {
                return;
            }

            JsonNode tpsOutboundNode = objectMapper.readTree(tpsOutboundJson);

            // Special handling for comment fields
            applySpecialCommentHandling(booking, tpsOutboundNode, config);

            // Apply general field customizations using dynamic mapping
            fieldMapper.applyFieldMappings(booking, tpsOutboundNode, Constants.TPS_FIELDS_TO_IGNORE);

        } catch (Exception e) {
            throw new TransformationException("Error applying output customizations", getTransformationType(), e);
        }
    }

    private void applySpecialCommentHandling(MurexTradeLeg booking, JsonNode tpsOutboundNode, MurexBookingConfig config) {
        // Special handling for comment0 field with n-way concatenation
        if (tpsOutboundNode.has("comment0")) {
            JsonNode comment0Array = tpsOutboundNode.get("comment0");

            if (comment0Array.isArray()) {
                List<String> values = new ArrayList<>();

                for (JsonNode comment0Config : comment0Array) {
                    if (comment0Config.has("fieldName")) {
                        // Handle multiple fieldName entries within the same config object
                        JsonNode fieldNameNode = comment0Config.get("fieldName");
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
                    fieldMapper.setFieldValue(booking, "comment0", String.join(" | ", values));
                }
            }
        }

        // Special handling for comment1 field
        if (tpsOutboundNode.has("comment1")) {
            JsonNode comment1Array = tpsOutboundNode.get("comment1");

            if (comment1Array.isArray()) {
                List<String> values = new ArrayList<>();

                for (JsonNode comment1Config : comment1Array) {
                    if (comment1Config.has("fieldName")) {
                        String fieldName = comment1Config.get("fieldName").asText();
                        Object value = getFieldValueFromTable(booking, comment1Config, fieldName);
                        if (value != null && !value.toString().trim().isEmpty()) {
                            values.add(value.toString());
                        }
                    }
                }

                // For comment1, use the first value or join if multiple
                if (!values.isEmpty()) {
                    fieldMapper.setFieldValue(booking, "comment1", values.size() == 1 ? values.getFirst() : String.join(" | ", values));
                }
            }
        }
    }

    /**
     * Get field value considering the table context from configuration
     *
     * @param booking   The MurexBookingDTO object
     * @param config    The configuration node containing table and fieldName
     * @param fieldName The field name to retrieve
     * @return The field value or special handling based on table context
     */
    private Object getFieldValueFromTable(MurexTradeLeg booking, JsonNode config, String fieldName) {
        String table = config.has("table") ? config.get("table").asText() : null;

        // Handle different table contexts
        if ("entityTable".equals(table)) {
            // For entityTable, get value directly from booking object
            return fieldMapper.getFieldValue(booking, fieldName);
        } else if ("murexBookCode".equals(table)) {
            // For murexBookCode table, return the murexBookCode value
            if ("murexBookCode".equals(fieldName)) {
                return fieldMapper.getFieldValue(booking, "murexBookCode");
            }
        }

        // Default: get field value directly from booking
        return fieldMapper.getFieldValue(booking, fieldName);
    }

    public MurexTradeLeg applyTPSFieldTransformations(MurexTradeLeg booking, MurexBookingConfig config) {

        try {
            String transformations = config.getTransformations();
            if (ObjectUtils.isEmpty(transformations)) {
                return booking;
            }
            JsonNode transformationNode = jsonTransformationParser.getFirstTransformation(transformations);

            Set<String> tpdFieldSet = new HashSet<>();

            JsonNode tpsFields = transformationNode.get("tpsFields");
            tpdFieldSet.addAll(jsonArrayToStringSet(tpsFields));
            tpdFieldSet.addAll(DEFAULT_TPS_FIELDS_TO_INCLUDE);

            return CopyUtils.cloneWithFields(booking, MurexTradeLeg.class, tpdFieldSet);
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