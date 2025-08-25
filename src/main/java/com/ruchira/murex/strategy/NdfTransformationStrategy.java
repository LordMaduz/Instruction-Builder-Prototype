package com.ruchira.murex.strategy;

import com.ruchira.murex.config.TransformationFieldConfig;
import com.ruchira.murex.exception.BusinessException;
import com.ruchira.murex.mapper.DynamicMapper;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.mapper.MurexTradeRecordMapper;
import com.ruchira.murex.model.*;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import com.ruchira.murex.model.trade.MurexTradeLegAdditionalFields;
import com.ruchira.murex.model.trade.MurexTradeLegComponent;
import com.ruchira.murex.parser.JsonParser;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruchira.murex.util.TraceIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

import static com.ruchira.murex.constant.Constants.*;

/**
 * NDF (Non-Deliverable Forward) Transformation Strategy
 * <p>
 * Handles three distinct transformation scenarios for NDF trades:
 * <p>
 * Case 1: Single NDF transformation with 2 sub-trades (embeddedSpotLeg + forwardLeg)
 * - Apply transformations to both TransformedMurexTrade objects
 * <p>
 * Case 2: Single NDF transformation with 1 sub-trade (forwardLeg only)
 * - Identify forwardLeg by latest valueDate, apply transformation only to that record
 * <p>
 * Case 3: Dual transformations (NDF forwardLeg + FX Spot)
 * <p>
 * Transformation Structure Examples:
 * <p>
 * Case 1 - Both Legs:
 * [{
 * "referenceTrade": "NDF",
 * "referenceSubTradeBuySell": [
 * {"embeddedSpotLeg": true, "buy": true, "sell": true},
 * {"forwardLeg": true, "buy": true, "sell": true}
 * ]
 * }]
 * <p>
 * Case 2 - Forward Leg Only:
 * [{
 * "referenceTrade": "NDF",
 * "referenceSubTradeBuySell": [
 * {"forwardLeg": true, "buy": true, "sell": true}
 * ]
 * }]
 * <p>
 * Case 3 - Dual Transformations
 * [{
 * "referenceTrade": "NDF",
 * "referenceSubTradeBuySell": [{"forwardLeg": true, "buy": false, "sell": true}]
 * },
 * {
 * "referenceTrade": "FX Spot",
 * "referenceTradeBuySell": {"buy": true, "sell": false}
 * }]
 */
@Component
@Slf4j
public class NdfTransformationStrategy extends TransformationStrategy {

    public NdfTransformationStrategy(
            final MurexTradeRecordMapper murexTradeRecordMapper,
            final DynamicMapper dynamicMapper,
            final DynamicFieldParser fieldMapper,
            final JsonParser transformationParser,
            final TransformationFieldConfig transformationFieldConfig,
            final StgMrxExtProcessingService stgMrxExtProcessingService) {
        super(murexTradeRecordMapper, dynamicMapper, fieldMapper, transformationParser, transformationFieldConfig, stgMrxExtProcessingService);
    }

    @Override
    public String getTransformationType() {
        return FX_NDF_TYPOLOGY;
    }

    @Override
    public boolean supports(String typology) {
        return FX_NDF_TYPOLOGY.equals(typology);
    }

    /**
     * Enhanced process method with access to all GroupedRecords for NDF Edge case processing
     * This method enables proper FX Spot lookup for dual transformations
     */
    @Override
    public RecordProcessingResult process(TransformationContext transformationContext) {

        List<MurexTrade> allMurexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> allStgMrxExtDmcs = new ArrayList<>();

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {
            try {
                TransformationResult transformationResult = processNdfTransformation(
                        config,
                        transformationContext);

                MurexTrade murexTrade = buildMurexTrade(transformationResult.getMurexTradeList());

                allMurexTrades.add(murexTrade);
                allStgMrxExtDmcs.addAll(transformationResult.getStgMrxExtDmcs());
            } catch (Exception e) {
                throw new TransformationException(
                        String.format("Failed to transform FX NDF for config %s: %s", config.getId(), e.getMessage()),
                        getTransformationType(),
                        e
                );
            }
        }

        return new RecordProcessingResult(allStgMrxExtDmcs, allMurexTrades);
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

    private List<StgMrxExtDmcDto> generaStgMurexExtDmcRecords(
            final List<TransformedMurexTrade> transformedMurexTrades,
            final String murexBookCode,
            final String instructionEventRuleId,
            final String traceId
    ) {

        return stgMrxExtProcessingService.generateDmcRecords(transformedMurexTrades, murexBookCode, instructionEventRuleId, traceId);
    }

    /**
     * Process NDF transformation for a single MurexBookConfig
     * Determines the transformation case and applies appropriate logic
     */
    private TransformationResult processNdfTransformation(MurexBookingConfig config,
                                                          TransformationContext transformationContext) {

        // Generate unique trace ID for tracking
        final String traceId = TraceIdGenerator.generateTimestampBasedTraceId();

        // Parse transformations array from MurexBookConfig
        List<JsonNode> transformations = jsonParser
                .parseTransformations(config.getTransformations());

        if (transformations.isEmpty()) {
            throw new TransformationException(
                    String.format("No transformations found in MurexBookConfig %s", config.getId()),
                    getTransformationType()
            );
        }

        // Validate we have exactly 2 records in GroupedRecord for NDF
        List<AggregatedDataResponse> records = transformationContext.getGroupedRecord().getRecords();
        if (records.size() != 2) {
            throw new TransformationException(
                    String.format("NDF transformation requires exactly 2 records in GroupedRecord, found: %s", records.size()),
                    getTransformationType()
            );
        }

        // Convert records to TransformedMurexTrade objects
        List<TransformedMurexTrade> murexBookings = createBaseBookings(records, config.getMurexBookCode(), traceId);

        // Determine transformation case and process accordingly
        JsonNode ndfTransformation = findNdfTransformation(transformations);
        if (ndfTransformation == null) {
            throw new TransformationException("No NDF transformation found in transformations array", getTransformationType());
        }

        TransformationCase transformationCase = determineTransformationCase(transformations, ndfTransformation);

        return switch (transformationCase) {
            case BOTH_LEGS ->
                    processBothLegsCase(murexBookings, ndfTransformation, config, transformationContext, traceId);
            case EMBEDDED_SPOT_LEG_ONLY ->
                    processEmbeddedSpotLegOnlyCase(murexBookings, ndfTransformation, config, transformationContext, traceId);
            case DUAL_TRANSFORMATIONS ->
                    processDualTransformationsCase(murexBookings, transformations, config, transformationContext, traceId);
        };
    }

    /**
     * Create base TransformedMurexTrade objects from FetchDataResponse records
     */
    private List<TransformedMurexTrade> createBaseBookings(List<AggregatedDataResponse> records, String murexBookCode, String traceId) {
        List<TransformedMurexTrade> bookings = new ArrayList<>();
        for (AggregatedDataResponse record : records) {
            TransformedMurexTrade transformedMurexTrade = dynamicMapper.mapToMurexTradeLeg(record, Map.of(FIELD_MUREX_BOOK_CODE, murexBookCode, FIELD_TRACE_ID, traceId));
            bookings.add(transformedMurexTrade);
        }
        return bookings;
    }

    /**
     * Find the NDF transformation node from the transformations array
     */
    private JsonNode findNdfTransformation(List<JsonNode> transformations) {
        return transformations.stream()
                .filter(transformation -> transformation.has(REFERENCE_TRADE_FIELD))
                .filter(transformation -> FX_NDF_TYPOLOGY.contains(transformation.get(REFERENCE_TRADE_FIELD).asText()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Determine which transformation case we're dealing with
     */
    private TransformationCase determineTransformationCase(List<JsonNode> transformations,
                                                           JsonNode ndfTransformation) {

        // Case 3: Multiple transformations (NDF + FX Spot)
        if (transformations.size() > 1) {
            return TransformationCase.DUAL_TRANSFORMATIONS;
        }

        // Single transformation - check referenceSubTradeBuySell array
        if (ndfTransformation.has(REFERENCE__SUB_TRADE_BUY_SELL_FIELD)) {
            JsonNode subTrades = ndfTransformation.get(REFERENCE__SUB_TRADE_BUY_SELL_FIELD);

            if (subTrades.isArray()) {
                int subTradeCount = subTrades.size();

                if (subTradeCount == 2) {
                    // Case 1: Both legs (embeddedSpotLeg + forwardLeg)
                    return TransformationCase.BOTH_LEGS;
                } else if (subTradeCount == 1) {
                    // Case 2: Forward leg only
                    return TransformationCase.EMBEDDED_SPOT_LEG_ONLY;
                }
            }
        }

        throw new TransformationException("Unable to determine transformation case from NDF configuration", getTransformationType());
    }

    /**
     * Case 1: Apply transformation to both TransformedMurexTrade objects
     * Both records get the same transformation applied
     */
    private TransformationResult processBothLegsCase(List<TransformedMurexTrade> murexBookings,
                                                     JsonNode ndfTransformation,
                                                     MurexBookingConfig config,
                                                     TransformationContext transformationContext,
                                                     String traceId) {

        log.info("Processing NDF Case: Both Legs transformation");

        // Identify NDF Embedded Spot Leg
        TransformedMurexTrade embeddedSpotLeg = identifyEmbeddedSpotLeg(murexBookings);
        embeddedSpotLeg.setLegIdentificationType(NEAR_LEG_TYPE);

        // Identify NDF Forward  Leg
        TransformedMurexTrade forwardLeg = murexBookings.stream()
                .filter(booking -> !booking.equals(embeddedSpotLeg))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unable to identify embedded spot leg"));
        forwardLeg.setLegIdentificationType(FAR_LEG_TYPE);

        List<TransformedMurexTrade> transformedMurexTradesForDMC = Stream.of(embeddedSpotLeg, forwardLeg)
                .map(dynamicMapper::clone)
                .toList();

        List<TransformedMurexTrade> transformedMurexTrades = Stream.of(embeddedSpotLeg, forwardLeg)
                .map(leg -> applyNDFTransformation(leg, ndfTransformation, config, transformationContext))
                .toList();

        List<StgMrxExtDmcDto> stgMrxExtDmcDtos = generaStgMurexExtDmcRecords(transformedMurexTradesForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);

        return new TransformationResult(transformedMurexTrades, stgMrxExtDmcDtos);
    }

    private TransformedMurexTrade applyNDFTransformation(
            TransformedMurexTrade booking,
            JsonNode ndfTransformation,
            MurexBookingConfig config,
            TransformationContext transformationContext
    ) {
        try {
            TransformedMurexTrade transformedBooking = applyNdfTransformation(
                    booking, ndfTransformation, config, transformationContext);
            return applyTPSFieldTransformations(transformedBooking, config);
        } catch (Exception e) {
            String message = String.format("Error applying transformation to booking %s : %s", booking.getContract(), e.getMessage());
            log.error(message);
            throw e;
        }
    }


    /**
     * Case 2: Identify forwardLeg by latest valueDate and apply transformation only to that record
     */
    private TransformationResult processEmbeddedSpotLegOnlyCase(List<TransformedMurexTrade> murexBookings,
                                                                JsonNode ndfTransformation,
                                                                MurexBookingConfig config,
                                                                TransformationContext transformationContext,
                                                                String traceId) {

        log.info("Processing NDF Case: Forward Leg Only transformation");

        // Identify forward leg by latest/furthest valueDate
        TransformedMurexTrade embeddedSpotLeg = identifyEmbeddedSpotLeg(murexBookings);
        embeddedSpotLeg.setLegIdentificationType(NEAR_LEG_TYPE);

        List<TransformedMurexTrade> transformedMurexTradesForDMC = List.of(dynamicMapper.clone(embeddedSpotLeg));

        // Apply transformation only to embedded spot leg
        TransformedMurexTrade transformedForwardLeg = applyNdfTransformation(
                embeddedSpotLeg, ndfTransformation, config, transformationContext);
        TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedForwardLeg, config);


        List<StgMrxExtDmcDto> stgMrxExtDmcDtos = generaStgMurexExtDmcRecords(transformedMurexTradesForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);

        return new TransformationResult(List.of(tpsFieldFilteredTrade), stgMrxExtDmcDtos);
    }

    /**
     * Case 3: Dual transformations (NDF + FX Spot) - Complete Phase 3 implementation
     * <p>
     * Handles transformation arrays with both NDF and FX Spot elements:
     * 1. Parse and differentiate NDF and FX Spot transformation nodes
     * 2. Identify NDF forward leg and embedded spot leg DTOs
     * 3. Locate matching FX Spot DTO from external GroupedRecords
     * 4. Apply inter-DTO field overrides based on buy/sell flags
     * 5. Apply standard transformations to NDF embedded spot leg
     * <p>
     * Example transformation structure:
     * [{
     * "referenceTrade": "NDF",
     * "referenceSubTradeBuySell": [{"forwardLeg": true, "buy": false, "sell": true}]
     * },
     * {
     * "referenceTrade": "FX Spot",
     * "referenceTradeBuySell": {"buy": true, "sell": false}
     * }]
     */
    private TransformationResult processDualTransformationsCase(List<TransformedMurexTrade> murexBookings,
                                                                List<JsonNode> transformations,
                                                                MurexBookingConfig config,
                                                                TransformationContext transformationContext,
                                                                String traceId) {

        log.info("Processing NDF Case: Dual Transformations");

        // Step 1: Parse and differentiate transformation nodes
        DualTransformationNodes nodes = parseDualTransformations(transformations);

        // Step 2: Identify NDF leg DTOs
        TransformedMurexTrade embeddedSpotLeg = identifyEmbeddedSpotLeg(murexBookings);
        embeddedSpotLeg.setLegIdentificationType(NEAR_LEG_TYPE);

        List<TransformedMurexTrade> tradeLegInputForDMC = List.of(dynamicMapper.clone(embeddedSpotLeg));

        TransformedMurexTrade forwardLeg = murexBookings.stream()
                .filter(booking -> !booking.equals(embeddedSpotLeg))
                .findFirst()
                .orElseThrow(() -> new BusinessException("Unable to identify embedded spot leg"));

        // Step 3: Locate matching FX Spot DTO using all available GroupedRecords
        TransformedMurexTrade fxSpotDTO = locateFxSpotDTO(embeddedSpotLeg, config, transformationContext.getAllGroupedRecords());

        // Step 4: Apply inter-DTO field overrides
        TransformedMurexTrade modifiedEmbeddedSpotLeg = applyInterDtoFieldOverrides(
                embeddedSpotLeg, forwardLeg, fxSpotDTO, nodes);

        // Step 5: Apply standard transformations to modified embedded spot leg
        TransformedMurexTrade transformedEmbeddedSpotLeg = applyNdfTransformation(
                modifiedEmbeddedSpotLeg, nodes.ndfTransformation, config, transformationContext);

        TransformedMurexTrade tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedEmbeddedSpotLeg, config);

        List<StgMrxExtDmcDto> stgMrxExtDmcDtos = generaStgMurexExtDmcRecords(tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);

        return new TransformationResult(List.of(tpsFieldFilteredTrade), stgMrxExtDmcDtos);
    }

    /**
     * Parse dual transformations array and extract NDF and FX Spot transformation nodes
     */
    private DualTransformationNodes parseDualTransformations(List<JsonNode> transformations) {
        JsonNode ndfTransformation = null;
        JsonNode fxSpotTransformation = null;

        for (JsonNode transformation : transformations) {
            if (transformation.has(REFERENCE_TRADE_FIELD)) {
                String referenceTrade = transformation.get(REFERENCE_TRADE_FIELD).asText();

                if (FX_NDF_TYPOLOGY.contains(referenceTrade)) {
                    ndfTransformation = transformation;
                } else if (FX_SPOT_TYPOLOGY.equals(referenceTrade)) {
                    fxSpotTransformation = transformation;
                }
            }
        }

        if (ndfTransformation == null) {
            throw new TransformationException("No NDF transformation found in dual transformations array", getTransformationType());
        }

        if (fxSpotTransformation == null) {
            throw new TransformationException("No FX Spot transformation found in dual transformations array", getTransformationType());
        }

        return new DualTransformationNodes(ndfTransformation, fxSpotTransformation);
    }

    /**
     * Locate matching FX Spot DTO based on business rules:
     * - Same navType (comment0 equivalent)
     * - Different externalDealId
     * - Typology must be "FX Spot"
     *
     * @param embeddedSpotLeg   The NDF embedded spot leg to find a match for
     * @param config            The current MurexBookConfig
     * @param allGroupedRecords All available GroupedRecords to search through
     * @return Matching FX Spot MurexBookingDTO or mock DTO if not found
     */
    private TransformedMurexTrade locateFxSpotDTO(TransformedMurexTrade embeddedSpotLeg,
                                                  MurexBookingConfig config,
                                                  List<GroupedRecord> allGroupedRecords) {

        log.info("Locating FX Spot DTO for embedded spot leg: {}", embeddedSpotLeg.getContract());

        GroupedRecord groupedRecord = allGroupedRecords.stream().filter(record ->
                        record.getTypology().equals(FX_SPOT_TYPOLOGY) &&
                                record.getNavType().equals(embeddedSpotLeg.getNavType()) &&
                                record.getComment0().equals(embeddedSpotLeg.getComment0()) &&
                                !record.getContract().equals(embeddedSpotLeg.getContract())
                ).findAny()
                .orElseThrow(() -> new BusinessException(
                        String.format("No Valid FX Spot Grouped Record Found for NDF Dual Transformation" +
                                        " Case with Nav Type: %s, Comment0: %s & Contract != %s", embeddedSpotLeg.getNavType(),
                                embeddedSpotLeg.getComment0(), embeddedSpotLeg.getContract())
                ));


        // Found matching FX Spot record - convert to DTO
        return dynamicMapper.mapToMurexTradeLeg(groupedRecord.getRecords().getFirst());
    }


    /**
     * Apply inter-DTO field overrides based on buy/sell flags in transformations
     * <p>
     * Business Rules:
     * 1. FX Spot transformation buy/sell flags override embedded spot leg currencies from FX Spot DTO
     * 2. NDF transformation buy/sell flags override embedded spot leg currencies from NDF forward DTO
     */
    private TransformedMurexTrade applyInterDtoFieldOverrides(TransformedMurexTrade embeddedSpotLeg,
                                                              TransformedMurexTrade forwardLeg,
                                                              TransformedMurexTrade fxSpotDTO,
                                                              DualTransformationNodes nodes) {

        log.info("Applying inter-DTO field overrides");

        // Create a copy of embedded spot leg for modification
        TransformedMurexTrade modifiedEmbeddedSpotLeg = dynamicMapper.clone(embeddedSpotLeg);

        // Step 1: Apply FX Spot transformation overrides
        applyFxSpotOverrides(modifiedEmbeddedSpotLeg, fxSpotDTO, nodes.fxSpotTransformation);

        // Step 2: Apply NDF transformation overrides (may override FX Spot changes)
        applyNdfOverrides(modifiedEmbeddedSpotLeg, forwardLeg, nodes.ndfTransformation);

        return modifiedEmbeddedSpotLeg;
    }

    /**
     * Apply FX Spot transformation overrides to embedded spot leg
     * <p>
     * Rules:
     * - If "buy": true in referenceTradeBuySell, override currency1 using FX Spot DTO currency1
     * - If "sell": true, override currency2 using FX Spot DTO currency2
     */
    private void applyFxSpotOverrides(TransformedMurexTrade embeddedSpotLeg,
                                      TransformedMurexTrade fxSpotDTO,
                                      JsonNode fxSpotTransformation) {

        if (!fxSpotTransformation.has(REFERENCE_TRADE_BUY_SELL_FIELD)) {
            return;
        }

        JsonNode buySellNode = fxSpotTransformation.get(REFERENCE_TRADE_BUY_SELL_FIELD);

        // Apply buy override (currency1 from FX Spot)
        if (buySellNode.has(TRADE_BUY_FIELD) && buySellNode.get(TRADE_BUY_FIELD).asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurr1();
            embeddedSpotLeg.setCurr1(fxSpotDTO.getCurr1());

            log.info("FX Spot buy override: currency1 changed from; {} to {}", originalCurrency, fxSpotDTO.getCurr1());
        }

        // Apply sell override (currency2 from FX Spot)
        if (buySellNode.has(TRADE_SELL_FIELD) && buySellNode.get(TRADE_SELL_FIELD).asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurr2();
            embeddedSpotLeg.setCurr2(fxSpotDTO.getCurr2());

            log.info("FX Spot sell override: currency2 changed from: {} to {}", originalCurrency, fxSpotDTO.getCurr2());
        }
    }

    /**
     * Apply NDF transformation overrides to embedded spot leg
     * <p>
     * Rules:
     * - If "buy": true in referenceSubTradeBuySell, override currency1 using NDF forward DTO currency1
     * - If "sell": true, override currency2 using NDF forward DTO currency2
     */
    private void applyNdfOverrides(TransformedMurexTrade embeddedSpotLeg,
                                   TransformedMurexTrade forwardLeg,
                                   JsonNode ndfTransformation) {

        if (!ndfTransformation.has(REFERENCE__SUB_TRADE_BUY_SELL_FIELD)) {
            return;
        }

        JsonNode subTradeBuySellArray = ndfTransformation.get(REFERENCE__SUB_TRADE_BUY_SELL_FIELD);

        if (!subTradeBuySellArray.isArray() || subTradeBuySellArray.isEmpty()) {
            return;
        }

        // Use the first (and typically only) sub-trade for dual transformations
        JsonNode subTradeBuySell = subTradeBuySellArray.get(0);

        // Apply buy override (currency1 from NDF forward)
        if (subTradeBuySell.has(TRADE_BUY_FIELD) && subTradeBuySell.get(TRADE_BUY_FIELD).asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurr1();
            embeddedSpotLeg.setCurr1(forwardLeg.getCurr1());

            log.info("NDF buy override: currency1 changed from: {} to {}", originalCurrency, forwardLeg.getCurr1());
        }

        // Apply sell override (currency2 from NDF forward)
        if (subTradeBuySell.has(TRADE_SELL_FIELD) && subTradeBuySell.get(TRADE_SELL_FIELD).asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurr2();
            embeddedSpotLeg.setCurr2(forwardLeg.getCurr2());

            log.info("NDF sell override: currency2 changed from: {} to {} ", originalCurrency, forwardLeg.getCurr2());
        }
    }

    /**
     * Data holder class for dual transformation nodes
     */
    private static class DualTransformationNodes {
        final JsonNode ndfTransformation;
        final JsonNode fxSpotTransformation;

        public DualTransformationNodes(JsonNode ndfTransformation, JsonNode fxSpotTransformation) {
            this.ndfTransformation = ndfTransformation;
            this.fxSpotTransformation = fxSpotTransformation;
        }
    }

    /**
     * Identify the forward leg by finding the record with the latest/furthest valueDate
     */
    private TransformedMurexTrade identifyEmbeddedSpotLeg(List<TransformedMurexTrade> murexBookings) {

        TransformedMurexTrade embeddedSpotLeg = murexBookings.stream().min(Comparator.comparing(TransformedMurexTrade::getValueDte)).orElse(null);

        if (embeddedSpotLeg == null) {
            log.error("Unable to determine forward leg by valueDate");
            throw new BusinessException("Unable to determine forward leg by valueDate");
        }

        return embeddedSpotLeg;
    }

    /**
     * Apply NDF transformation logic to a MurexBookingDTO
     * Follows FX Spot transformation standards with NDF-specific adaptations
     */
    private TransformedMurexTrade applyNdfTransformation(TransformedMurexTrade booking,
                                                         JsonNode ndfTransformation,
                                                         MurexBookingConfig config,
                                                         TransformationContext transformationContext) {

        // Create a copy of the leg record for transformation
        TransformedMurexTrade transformedRecord = dynamicMapper.clone(booking);

        try {
            // Apply individual transformation using FX Spot logic (reused)
            applyIndividualTransformation(transformedRecord, ndfTransformation, transformationContext);

            // Apply output customizations
            applyOutputCustomizations(transformedRecord, config);

        } catch (Exception e) {
            throw new TransformationException(
                    String.format("Failed to apply leg transformation: %S", e.getMessage()),
                    getTransformationType(),
                    e
            );
        }

        return transformedRecord;
    }

    /**
     * Enumeration for different transformation cases
     */
    private enum TransformationCase {
        BOTH_LEGS,           // Case 1: Apply to both legs
        EMBEDDED_SPOT_LEG_ONLY,    // Case 2: Apply to forward leg only
        DUAL_TRANSFORMATIONS // Case 3: Multiple transformations (Phase 3)
    }
}