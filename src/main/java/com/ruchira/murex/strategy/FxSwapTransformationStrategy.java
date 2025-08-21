package com.ruchira.murex.strategy;

import com.ruchira.murex.constant.Constants;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.model.*;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.util.CopyUtils;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruchira.murex.parser.JsonTransformationParser;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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


    private static final String FX_SWAP_TYPOLOGY = "FX Swap";

    public FxSwapTransformationStrategy(
            final DynamicFieldParser fieldMapper,
            final JsonTransformationParser transformationParser,
            final StgMrxExtProcessingService stgMrxExtProcessingService
    ) {
        super(fieldMapper, transformationParser, stgMrxExtProcessingService);
    }

    @Override
    public boolean supports(String typology) {
        return FX_SWAP_TYPOLOGY.equals(typology);
    }

    @Override
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(TransformationContext transformationContext) {

        final GroupedRecord groupedRecord = transformationContext.getGroupedRecord();
        if (groupedRecord.getRecords().size() != 2) {
            throw new TransformationException(
                    "FX Swap must have exactly 2 records, found: " + groupedRecord.getRecords().size(),
                    getTransformationType()
            );
        }

        List<MurexTrade> murexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        // Create base booking DTOs from both records
        List<MurexTradeLeg> baseBookings = createBaseBookings(groupedRecord.getRecords());

        // Identify near and far records based on currency configuration
        LegIdentificationResult legResult = identifyNearAndFarRecords(baseBookings, transformationContext.getInputCurrency());

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {
            try {


                // Process transformations based on configuration structure
                List<MurexTradeLeg> transformedTradeLeg = processSwapTransformations(
                        legResult, config, transformationContext, stgMrxExtDmcs);

                MurexTrade murexTrade = MurexTrade.builder()
                        .murexTradeLegs(transformedTradeLeg)
                        .build();

                murexTrades.add(murexTrade);

            } catch (Exception e) {
                throw new TransformationException(
                        "Failed to transform FX Swap for config " + config.getId() + ": " + e.getMessage(),
                        getTransformationType(),
                        e
                );
            }
        }

        return Pair.of(stgMrxExtDmcs, murexTrades);
    }

    private void generaStgMurexExtDmcRecords(
            final List<StgMrxExtDmcDto> stgMrxExtDmcs,
            final List<MurexTradeLeg> murexTradeLegs,
            final String murexBookCode,
            final String instructionEventRuleId
    ) {

        stgMrxExtDmcs.addAll(stgMrxExtProcessingService.generateDmcRecords(murexTradeLegs, murexBookCode, instructionEventRuleId));
    }

    @Override
    public String getTransformationType() {
        return "FX_SWAP";
    }

    /**
     * Create base MurexBookingDTO objects from FetchDataResponse records
     */
    private List<MurexTradeLeg> createBaseBookings(List<AggregatedDataResponse> records) {
        List<MurexTradeLeg> bookings = new ArrayList<>();
        for (AggregatedDataResponse record : records) {
            bookings.add(CopyUtils.clone(record, MurexTradeLeg.class));
        }
        return bookings;
    }

    /**
     * Identify near and far records based on currency matching rules
     *
     * @param bookings      List of 2 MurexBookingDTO objects
     * @param inputCurrency Input currency for identification
     * @return LegIdentificationResult containing near and far records
     */
    private LegIdentificationResult identifyNearAndFarRecords(List<MurexTradeLeg> bookings, String inputCurrency) {
        MurexTradeLeg nearRecord = null;
        MurexTradeLeg farRecord = null;

        for (MurexTradeLeg booking : bookings) {
            String currency1 = (String) fieldMapper.getFieldValue(booking, "currency1");
            String currency2 = (String) fieldMapper.getFieldValue(booking, "currency2");

            // nearRecord: currency1 == inputCurrency && currency2 == 'USD'
            if (inputCurrency.equals(currency1) && "USD".equals(currency2)) {
                nearRecord = booking;
            }
            // farRecord: currency2 == inputCurrency && currency1 == 'USD'
            else if (inputCurrency.equals(currency2) && "USD".equals(currency1)) {
                farRecord = booking;
            }
        }

        if (nearRecord == null || farRecord == null) {
            throw new TransformationException(
                    "Could not identify near/far records. InputCurrency: " + inputCurrency +
                            ", Records: " + bookings.size(),
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
     * @return List of transformed MurexBookingDTO objects
     */
    private List<MurexTradeLeg> processSwapTransformations(final LegIdentificationResult legResult,
                                                           final MurexBookingConfig config,
                                                           final TransformationContext transformationContext,
                                                           final List<StgMrxExtDmcDto> stgMrxExtDmcs) {

        List<MurexTradeLeg> results = new ArrayList<>();

        try {
            // Use new parser for backward compatibility and consistency
            JsonNode transformationNode = jsonTransformationParser.getFirstTransformation(config.getTransformations());

            // Validate FX Swap has exactly one transformation
            int transformationCount = jsonTransformationParser.getTransformationCount(config.getTransformations());
            if (transformationCount != 1) {
                throw new TransformationException(
                        "FX Swap transformations must have exactly 1 element, found: " + transformationCount,
                        getTransformationType()
                );
            }

            TransformationConfigStructure configStructure = analyzeConfigStructure(transformationNode);

            // Set murex book code for both legs
            fieldMapper.setFieldValue(legResult.nearRecord, "murexBookCode", config.getMurexBookCode());
            fieldMapper.setFieldValue(legResult.farRecord, "murexBookCode", config.getMurexBookCode());

            // Process based on configuration structure
            switch (configStructure.type) {
                case BOTH_LEGS:
                    // Case 1: Both nearLeg and farLeg present
                    results.addAll(processBothLegs(legResult, configStructure, config, transformationContext, stgMrxExtDmcs));
                    break;

                case SINGLE_LEG:
                    // Case 2: Only one leg present (nearLeg OR farLeg)
                    results.addAll(processSingleLeg(legResult, configStructure, config, transformationContext, stgMrxExtDmcs));
                    break;

                default:
                    throw new TransformationException(
                            "Unsupported transformation configuration structure",
                            getTransformationType()
                    );
            }

        } catch (Exception e) {
            throw new TransformationException(
                    "Error processing FX Swap transformations: " + e.getMessage(),
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

        if (transformationsNode.has("referenceSubTradeBuySell")) {
            JsonNode subTradeArray = transformationsNode.get("referenceSubTradeBuySell");

            if (subTradeArray.isArray() && subTradeArray.size() == 2) {
                // Check if both nearLeg and farLeg are present
                boolean hasNearLeg = false;
                boolean hasFarLeg = false;

                for (JsonNode leg : subTradeArray) {
                    if (leg.has("nearLeg")) hasNearLeg = true;
                    if (leg.has("farLeg")) hasFarLeg = true;
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
    private List<MurexTradeLeg> processBothLegs(LegIdentificationResult legResult,
                                                TransformationConfigStructure configStructure,
                                                MurexBookingConfig config,
                                                TransformationContext transformationContext,
                                                List<StgMrxExtDmcDto> stgMrxExtDmcs) {

        List<MurexTradeLeg> tradeLegInputForDMC = new ArrayList<>();
        List<MurexTradeLeg> results = new ArrayList<>();

        JsonNode transformationNode = getTransformationsNode(config);

        for (JsonNode legConfig : configStructure.referenceSubTradeBuySell) {
            if (legConfig.has("nearLeg")) {
                tradeLegInputForDMC.add(CopyUtils.clone(legResult.nearRecord, MurexTradeLeg.class));

                // Apply transformation to near record
                MurexTradeLeg transformedNear = applyLegTransformation(
                        legResult.nearRecord, transformationNode, config, transformationContext);
                MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedNear, config);
                results.add(tpsFieldFilteredTrade);
            }

            if (legConfig.has("farLeg")) {
                tradeLegInputForDMC.add(CopyUtils.clone(legResult.farRecord, MurexTradeLeg.class));

                // Apply transformation to far record
                MurexTradeLeg transformedFar = applyLegTransformation(
                        legResult.farRecord, transformationNode, config, transformationContext);
                MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedFar, config);
                results.add(tpsFieldFilteredTrade);
            }
        }

        generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());
        return results;
    }

    private JsonNode getTransformationsNode(MurexBookingConfig config) {
        try {
            String transformations = config.getTransformations();
            if (ObjectUtils.isEmpty(transformations)) {
                return null;
            }
            return jsonTransformationParser.getFirstTransformation(transformations);
        } catch (Exception e) {
            throw new TransformationException("Error applying output customizations", getTransformationType(), e);
        }
    }

    /**
     * Process single leg transformation (Case 2)
     */
    private List<MurexTradeLeg> processSingleLeg(final LegIdentificationResult legResult,
                                                 final TransformationConfigStructure configStructure,
                                                 final MurexBookingConfig config,
                                                 final TransformationContext transformationContext,
                                                 final List<StgMrxExtDmcDto> stgMrxExtDmcs) {

        List<MurexTradeLeg> results = new ArrayList<>();

        List<MurexTradeLeg> tradeLegInputForDMC = new ArrayList<>();

        JsonNode transformationNode = getTransformationsNode(config);

        for (JsonNode legConfig : configStructure.referenceSubTradeBuySell) {
            if (legConfig.has("nearLeg")) {

                tradeLegInputForDMC.add(CopyUtils.clone(legResult.nearRecord, MurexTradeLeg.class));

                MurexTradeLeg transformedNear = applyLegTransformation(
                        legResult.nearRecord, transformationNode, config, transformationContext);
                MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedNear, config);

                results.add(tpsFieldFilteredTrade);

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());
            } else if (legConfig.has("farLeg")) {

                tradeLegInputForDMC.add(CopyUtils.clone(legResult.farRecord, MurexTradeLeg.class));

                MurexTradeLeg transformedFar = applyLegTransformation(
                        legResult.farRecord, transformationNode, config, transformationContext);
                MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedFar, config);

                results.add(tpsFieldFilteredTrade);

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());
            }
        }

        return results;
    }


    /**
     * Apply transformation to a single leg record
     * Reuses FX Spot transformation logic where applicable
     */
    private MurexTradeLeg applyLegTransformation(MurexTradeLeg legRecord,
                                                 JsonNode legTransformation,
                                                 MurexBookingConfig config,
                                                 TransformationContext transformationContext) {

        // Create a copy of the leg record for transformation
        MurexTradeLeg transformedRecord = cloneMurexBookingDTO(legRecord);

        try {
            // Apply individual transformation using FX Spot logic (reused)
            applyIndividualTransformation(transformedRecord, legTransformation, transformationContext);

            // Apply output customizations
            applyOutputCustomizations(transformedRecord, config);

        } catch (Exception e) {
            throw new TransformationException(
                    "Failed to apply leg transformation: " + e.getMessage(),
                    getTransformationType(),
                    e
            );
        }

        return transformedRecord;
    }

    /**
     * Clone MurexBookingDTO for transformation
     */
    private MurexTradeLeg cloneMurexBookingDTO(MurexTradeLeg source) {
        try {
            return CopyUtils.clone(source, MurexTradeLeg.class);
        } catch (Exception e) {
            throw new TransformationException("Failed to clone MurexBookingDTO", getTransformationType(), e);
        }
    }

// Inner classes for structured data

    /**
     * Result of leg identification process
     */
    private static class LegIdentificationResult {
        final MurexTradeLeg nearRecord;
        final MurexTradeLeg farRecord;

        LegIdentificationResult(MurexTradeLeg nearRecord, MurexTradeLeg farRecord) {
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