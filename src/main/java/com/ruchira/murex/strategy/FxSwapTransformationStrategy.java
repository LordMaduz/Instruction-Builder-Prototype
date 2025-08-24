package com.ruchira.murex.strategy;

import com.ruchira.murex.config.TransformationFieldConfig;
import com.ruchira.murex.mapper.DynamicMapper;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.mapper.MurexTradeRecordMapper;
import com.ruchira.murex.model.*;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import com.ruchira.murex.model.trade.MurexTradeLegAdditionalFields;
import com.ruchira.murex.model.trade.MurexTradeLegComponent;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruchira.murex.parser.JsonParser;
import com.ruchira.murex.util.TraceIdGenerator;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.ruchira.murex.constant.Constants.*;

/**
 * Transformation strategy for FX Swap typology
 * Handles complex business logic for FX Swap transformations with near/far leg processing
 * <p>
 * FX Swap Processing Rules:
 * - Each GroupedRecord contains exactly 2 records (near and far legs)
 * - nearRecord: currency1 == inputCurrency && currency2 == 'USD'
 * - farRecord: currency2 == inputCurrency && currency1 == 'USD'
 * - Supports 2 transformation JSON structures:
 * 1. Both nearLeg and farLeg in referenceSubTradeBuySell array
 * 2. Single leg (nearLeg OR farLeg) in referenceSubTradeBuySell array
 */
@Component
public class FxSwapTransformationStrategy extends TransformationStrategy {

    public FxSwapTransformationStrategy(
            final MurexTradeRecordMapper murexTradeRecordMapper,
            final DynamicMapper dynamicMapper,
            final DynamicFieldParser fieldMapper,
            final JsonParser transformationParser,
            final TransformationFieldConfig transformationFieldConfig,
            final StgMrxExtProcessingService stgMrxExtProcessingService
    ) {
        super(murexTradeRecordMapper, dynamicMapper, fieldMapper, transformationParser, transformationFieldConfig, stgMrxExtProcessingService);
    }

    @Override
    public boolean supports(String typology) {
        return FX_SWAP_TYPOLOGY.equals(typology);
    }

    @Override
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(TransformationContext transformationContext) {

        final GroupedRecord groupedRecord = transformationContext.getGroupedRecord();
        validateRecordCount(groupedRecord);

        List<MurexTrade> murexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        // Create base booking from both records
        List<TransformedMurexTrade> baseBookings = createBaseBookings(groupedRecord.getRecords());

        // Identify near and far records based on currency configuration
        LegIdentificationResult legResult = identifyNearAndFarRecords(baseBookings, transformationContext);

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {
            try {

                // Process transformations based on configuration structure
                List<TransformedMurexTrade> transformedTradeLeg = processSwapTransformations(
                        legResult, config, transformationContext, stgMrxExtDmcs);

                MurexTrade murexTrade = buildMurexTrade(transformedTradeLeg);

                murexTrades.add(murexTrade);

            } catch (Exception e) {
                throw new TransformationException(
                        String.format("Failed to transform FX Swap for config %s: %s", config.getId(), e.getMessage()),
                        getTransformationType(),
                        e
                );
            }
        }

        return Pair.of(stgMrxExtDmcs, murexTrades);
    }

    private MurexTrade buildMurexTrade(List<TransformedMurexTrade> trades) {

        MurexTrade murexTrade = murexTradeRecordMapper.toMurexTrade(trades.getFirst());
        trades.forEach(tradeLeg -> {

            final MurexTradeLeg murexTradeLeg = murexTradeRecordMapper.toMurexTradeLeg(tradeLeg);
            final MurexTradeLegComponent murexTradeLegComponent = murexTradeRecordMapper.toMurexTradeLegComponent(tradeLeg);
            final MurexTradeLegAdditionalFields murexTradeLegAdditionalFields = murexTradeRecordMapper.toMurexTradeLegAdditionalFields(tradeLeg);

            murexTradeLeg.setComponents(List.of(murexTradeLegComponent));
            murexTradeLeg.setAdditionalFields(murexTradeLegAdditionalFields);

            if (NEAR_LEG_TYPE.equals(tradeLeg.getLegIdentificationType())) {
                murexTrade.setNearLeg(murexTradeLeg);
            } else {
                murexTrade.setFarLeg(murexTradeLeg);
            }
        });
        return murexTrade;
    }

    private void validateRecordCount(GroupedRecord groupedRecord) {
        if (groupedRecord.getRecords().size() != 2) {
            throw new TransformationException(
                    String.format("FX Swap must have exactly 2 records, found: %s", groupedRecord.getRecords().size()),
                    getTransformationType()
            );
        }
    }

    private void generaStgMurexExtDmcRecords(
            final List<StgMrxExtDmcDto> stgMrxExtDmcs,
            final List<TransformedMurexTrade> transformedMurexTrades,
            final String murexBookCode,
            final String instructionEventRuleId,
            final String traceId
    ) {

        stgMrxExtDmcs.addAll(stgMrxExtProcessingService.generateDmcRecords(transformedMurexTrades, murexBookCode, instructionEventRuleId, traceId));
    }

    @Override
    public String getTransformationType() {
        return FX_SWAP_TYPOLOGY;
    }

    /**
     * Create base TransformedMurexTrade objects from FetchDataResponse records
     */
    private List<TransformedMurexTrade> createBaseBookings(List<AggregatedDataResponse> records) {
        List<TransformedMurexTrade> bookings = new ArrayList<>();
        for (AggregatedDataResponse record : records) {
            TransformedMurexTrade transformedMurexTrade = dynamicMapper.mapToMurexTradeLeg(record);
            bookings.add(transformedMurexTrade);
        }
        return bookings;
    }

    /**
     * Identify near and far records based on currency matching rules
     *
     * @param bookings              List of 2 TransformedMurexTrade objects
     * @param transformationContext context params needed for processing
     * @return LegIdentificationResult containing near and far records
     */
    private LegIdentificationResult identifyNearAndFarRecords(List<TransformedMurexTrade> bookings, TransformationContext transformationContext) {
        TransformedMurexTrade nearRecord = null;
        TransformedMurexTrade farRecord = null;

        for (TransformedMurexTrade booking : bookings) {
            String currency1 = booking.getCurr1();
            String currency2 = booking.getCurr2();


            if (transformationContext.getCurrenciesInFamily().contains(currency1) && FUNCTIONAL_CURRENCY_USD.equals(currency2)) {
                booking.setLegIdentificationType(NEAR_LEG_TYPE);
                nearRecord = booking;
            } else if (transformationContext.getCurrenciesInFamily().contains(currency2) && FUNCTIONAL_CURRENCY_USD.equals(currency1)) {
                booking.setLegIdentificationType(FAR_LEG_TYPE);
                farRecord = booking;
            }
        }

        if (nearRecord == null || farRecord == null) {
            throw new TransformationException(
                    String.format("Could not identify near/far records. for given input currency family: %s and, Records: %s",
                            transformationContext.getCurrenciesInFamily(), bookings.size()),
                    getTransformationType()
            );
        }

        return new LegIdentificationResult(nearRecord, farRecord);
    }

    /**
     * Process FX Swap transformations based on configuration structure
     *
     * @param legResult             Identified near and far records
     * @param config                MurexBookConfig with transformations
     * @param transformationContext transformations context with all metaData
     * @return List of transformed TransformationContext objects
     */
    private List<TransformedMurexTrade> processSwapTransformations(final LegIdentificationResult legResult,
                                                                   final MurexBookingConfig config,
                                                                   final TransformationContext transformationContext,
                                                                   final List<StgMrxExtDmcDto> stgMrxExtDmcs) {

        List<TransformedMurexTrade> results = new ArrayList<>();

        try {

            // Generate unique trace ID for tracking
            final String traceId = TraceIdGenerator.generateTimestampBasedTraceId();

            // Use new parser for backward compatibility and consistency
            JsonNode transformationNode = jsonParser.getFirstTransformation(config.getTransformations());

            // Validate FX Swap has exactly one transformation
            int transformationCount = jsonParser.getTransformationCount(config.getTransformations());
            if (transformationCount != 1) {
                throw new TransformationException(
                        String.format("FX Swap transformations must have exactly 1 element, found: %s", transformationCount),
                        getTransformationType()
                );
            }

            TransformationConfigStructure configStructure = analyzeConfigStructure(transformationNode);

            // Set murex book code for both legs
            legResult.nearRecord.setMurexBookCode(config.getMurexBookCode());
            legResult.farRecord.setMurexBookCode(config.getMurexBookCode());

            // Set traceId for both legs
            legResult.nearRecord.setTraceId(traceId);
            legResult.farRecord.setTraceId(traceId);

            // Process based on configuration structure
            switch (configStructure.type) {
                case BOTH_LEGS:
                    // Case 1: Both nearLeg and farLeg present
                    results.addAll(processBothLegs(legResult, configStructure, config, transformationContext, stgMrxExtDmcs, traceId));
                    break;

                case SINGLE_LEG:
                    // Case 2: Only one leg present (nearLeg OR farLeg)
                    results.addAll(processSingleLeg(legResult, configStructure, config, transformationContext, stgMrxExtDmcs, traceId));
                    break;

                default:
                    throw new TransformationException(
                            "Unsupported transformation configuration structure",
                            getTransformationType()
                    );
            }

        } catch (Exception e) {
            throw new TransformationException(
                    String.format("Error processing FX Swap transformations: %s", e.getMessage()),
                    getTransformationType(),
                    e
            );
        }

        return results;
    }

    /**
     * Analyze transformation configuration structure to determine processing type
     * Note: FX Swap only uses referenceSubTradeBuySell, not referenceTradeBuySell
     */
    private TransformationConfigStructure analyzeConfigStructure(JsonNode transformationsNode) {
        TransformationConfigStructure structure = new TransformationConfigStructure();

        if (transformationsNode.has(REFERENCE__SUB_TRADE_BUY_SELL_FIELD)) {
            JsonNode subTradeArray = transformationsNode.get(REFERENCE__SUB_TRADE_BUY_SELL_FIELD);

            if (subTradeArray.isArray() && subTradeArray.size() == 2) {
                // Check if both nearLeg and farLeg are present
                boolean hasNearLeg = false;
                boolean hasFarLeg = false;

                for (JsonNode leg : subTradeArray) {
                    if (leg.has(REFERENCE_SUB_TRADE_NEAR_LEG_FIELD)) hasNearLeg = true;
                    if (leg.has(REFERENCE_SUB_TRADE_FAR_LEG_FIELD)) hasFarLeg = true;
                }

                if (hasNearLeg && hasFarLeg) {
                    structure.type = TransformationConfigType.BOTH_LEGS;
                    structure.referenceSubTradeBuySell = subTradeArray;
                } else {
                    structure.type = TransformationConfigType.SINGLE_LEG;
                    structure.referenceSubTradeBuySell = subTradeArray;
                }
            } else if (subTradeArray.isArray() && subTradeArray.size() == 1) {
                structure.type = TransformationConfigType.SINGLE_LEG;
                structure.referenceSubTradeBuySell = subTradeArray;
            }
        }

        return structure;
    }

    /**
     * Process both legs transformation (Case 1)
     */
    private List<TransformedMurexTrade> processBothLegs(LegIdentificationResult legResult,
                                                        TransformationConfigStructure configStructure,
                                                        MurexBookingConfig config,
                                                        TransformationContext transformationContext,
                                                        List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                        String traceId) {

        List<TransformedMurexTrade> tradeLegInputForDMC = new ArrayList<>();
        List<TransformedMurexTrade> results = new ArrayList<>();

        JsonNode transformationNode = getTransformationsNode(config);

        for (JsonNode legConfig : configStructure.referenceSubTradeBuySell) {
            if (legConfig.has(REFERENCE_SUB_TRADE_NEAR_LEG_FIELD)) {
                tradeLegInputForDMC.add(dynamicMapper.clone(legResult.nearRecord));

                // Apply transformation to near record
                TransformedMurexTrade transformedNear = applyLegTransformation(
                        legResult.nearRecord, transformationNode, config, transformationContext);
                TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedNear, config);
                results.add(tpsFieldFilteredTrade);
            }

            if (legConfig.has(REFERENCE_SUB_TRADE_FAR_LEG_FIELD)) {
                tradeLegInputForDMC.add(dynamicMapper.clone(legResult.farRecord));

                // Apply transformation to far record
                TransformedMurexTrade transformedFar = applyLegTransformation(
                        legResult.farRecord, transformationNode, config, transformationContext);
                TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedFar, config);
                results.add(tpsFieldFilteredTrade);
            }
        }

        generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);
        return results;
    }

    private JsonNode getTransformationsNode(MurexBookingConfig config) {
        try {
            String transformations = config.getTransformations();
            if (ObjectUtils.isEmpty(transformations)) {
                return null;
            }
            return jsonParser.getFirstTransformation(transformations);
        } catch (Exception e) {
            throw new TransformationException("Error applying output customizations", getTransformationType(), e);
        }
    }

    /**
     * Process single leg transformation (Case 2)
     */
    private List<TransformedMurexTrade> processSingleLeg(final LegIdentificationResult legResult,
                                                         final TransformationConfigStructure configStructure,
                                                         final MurexBookingConfig config,
                                                         final TransformationContext transformationContext,
                                                         final List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                         final String traceId) {

        List<TransformedMurexTrade> results = new ArrayList<>();

        List<TransformedMurexTrade> tradeLegInputForDMC = new ArrayList<>();

        JsonNode transformationNode = getTransformationsNode(config);

        for (JsonNode legConfig : configStructure.referenceSubTradeBuySell) {
            if (legConfig.has(REFERENCE_SUB_TRADE_NEAR_LEG_FIELD)) {

                tradeLegInputForDMC.add(dynamicMapper.clone(legResult.nearRecord));

                TransformedMurexTrade transformedNear = applyLegTransformation(
                        legResult.nearRecord, transformationNode, config, transformationContext);
                TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedNear, config);

                results.add(tpsFieldFilteredTrade);

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);
            } else if (legConfig.has(REFERENCE_SUB_TRADE_FAR_LEG_FIELD)) {

                tradeLegInputForDMC.add(dynamicMapper.clone(legResult.farRecord));

                TransformedMurexTrade transformedFar = applyLegTransformation(
                        legResult.farRecord, transformationNode, config, transformationContext);
                TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedFar, config);

                results.add(tpsFieldFilteredTrade);

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);
            }
        }

        return results;
    }


    /**
     * Apply transformation to a single leg record
     * Reuses FX Spot transformation logic where applicable
     */
    private TransformedMurexTrade applyLegTransformation(TransformedMurexTrade legRecord,
                                                         JsonNode legTransformation,
                                                         MurexBookingConfig config,
                                                         TransformationContext transformationContext) {

        // Create a copy of the leg record for transformation
        TransformedMurexTrade transformedRecord = dynamicMapper.clone(legRecord);

        try {
            // Apply individual transformation using FX Spot logic (reused)
            applyIndividualTransformation(transformedRecord, legTransformation, transformationContext);

            // Apply output customizations
            applyOutputCustomizations(transformedRecord, config);

        } catch (Exception e) {
            throw new TransformationException(
                    String.format("Failed to apply leg transformation: %s", e.getMessage()),
                    getTransformationType(),
                    e
            );
        }

        return transformedRecord;
    }

// Inner classes for structured data

    /**
     * Result of leg identification process
     */
    private static class LegIdentificationResult {
        final TransformedMurexTrade nearRecord;
        final TransformedMurexTrade farRecord;

        LegIdentificationResult(TransformedMurexTrade nearRecord, TransformedMurexTrade farRecord) {
            this.nearRecord = nearRecord;
            this.farRecord = farRecord;
        }
    }

    /**
     * Structure analysis result for transformation configuration
     */
    private static class TransformationConfigStructure {
        TransformationConfigType type;
        JsonNode referenceSubTradeBuySell;
    }

    /**
     * Types of transformation configuration structures
     */
    private enum TransformationConfigType {
        BOTH_LEGS,    // Both nearLeg and farLeg present
        SINGLE_LEG    // Only one leg present (nearLeg OR farLeg)
    }
}