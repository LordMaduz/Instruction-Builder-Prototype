package com.ruchira.murex.strategy;

import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.model.*;
import com.ruchira.murex.parser.JsonTransformationParser;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.util.CopyUtils;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * NDF (Non-Deliverable Forward) Transformation Strategy
 * <p>
 * Handles three distinct transformation scenarios for NDF trades:
 * <p>
 * Case 1: Single NDF transformation with 2 sub-trades (embeddedSpotLeg + forwardLeg)
 * - Apply transformations to both MurexBookingDTO objects
 * <p>
 * Case 2: Single NDF transformation with 1 sub-trade (forwardLeg only)
 * - Identify forwardLeg by latest valueDate, apply transformation only to that record
 * <p>
 * Case 3: Dual transformations (NDF forwardLeg + FX Spot) - Future Phase 3 implementation
 * - Currently handles first transformation element only
 * <p>
 * Transformation Structure Examples:
 * <p>
 * Case 1 - Both Legs:
 * [{
 * "referenceTrade": "NDF",
 * "referenceSubTradeBuySell": [
 * {"embeddedSpotLeg": true, "buy": true, "sell": true},
 * {"forwardLeg": true, "buy": true, "sell": true}
 * ],
 * "flipCurrency": false,
 * "exchangeRates": "FORWARD_CURVE"
 * }]
 * <p>
 * Case 2 - Forward Leg Only:
 * [{
 * "referenceTrade": "NDF",
 * "referenceSubTradeBuySell": [
 * {"forwardLeg": true, "buy": true, "sell": true}
 * ],
 * "flipCurrency": true,
 * "exchangeRates": "REF_TRADE"
 * }]
 * <p>
 * Case 3 - Dual Transformations (Phase 3):
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


    private static final String NDF_TYPOLOGY = "NDF";

    public NdfTransformationStrategy(
            DynamicFieldParser fieldMapper,
            JsonTransformationParser transformationParser,
            StgMrxExtProcessingService stgMrxExtProcessingService) {
        super(fieldMapper, transformationParser, stgMrxExtProcessingService);
    }

    @Override
    public String getTransformationType() {
        return NDF_TYPOLOGY;
    }

    @Override
    public boolean supports(String typology) {
        return NDF_TYPOLOGY.equals(typology);
    }

    /**
     * Enhanced process method with access to all GroupedRecords for NDF Edge case processing
     * This method enables proper FX Spot DTO lookup for dual transformations
     */
    @Override
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(TransformationContext transformationContext) {

        List<MurexTrade> murexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {
            try {
                List<MurexTradeLeg> murexTradeLegs = processNdfTransformation(
                        config,
                        stgMrxExtDmcs,
                        transformationContext);

                MurexTrade murexTrade = MurexTrade.builder()
                        .murexTradeLegs(murexTradeLegs)
                        .build();

                murexTrades.add(murexTrade);

            } catch (Exception e) {
                System.err.println("Error processing NDF transformation for config " +
                        config.getId() + ": " + e.getMessage());
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

    /**
     * Process NDF transformation for a single MurexBookConfig
     * Determines the transformation case and applies appropriate logic
     */
    private List<MurexTradeLeg> processNdfTransformation(MurexBookingConfig config,
                                                         List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                         TransformationContext transformationContext) {

        // Parse transformations array from MurexBookConfig
        List<JsonNode> transformations = jsonTransformationParser
                .parseTransformations(config.getTransformations());

        if (transformations.isEmpty()) {
            throw new RuntimeException("No transformations found in MurexBookConfig " + config.getId());
        }

        // Validate we have exactly 2 records in GroupedRecord for NDF
        List<AggregatedDataResponse> records = transformationContext.getGroupedRecord().getRecords();
        if (records.size() != 2) {
            throw new RuntimeException("NDF transformation requires exactly 2 records in GroupedRecord, found: " +
                    records.size());
        }

        // Convert records to MurexBookingDTO objects
        List<MurexTradeLeg> murexBookings = createBaseBookings(records, config.getMurexBookCode());

        // Determine transformation case and process accordingly
        JsonNode ndfTransformation = findNdfTransformation(transformations);
        if (ndfTransformation == null) {
            throw new RuntimeException("No NDF transformation found in transformations array");
        }

        TransformationCase transformationCase = determineTransformationCase(transformations, ndfTransformation);

        return switch (transformationCase) {
            case BOTH_LEGS ->
                    processBothLegsCase(murexBookings, ndfTransformation, config, stgMrxExtDmcs, transformationContext);
            case EMBEDDED_SPOT_LEG_ONLY ->
                    processEmbeddedSpotLegOnlyCase(murexBookings, ndfTransformation, config, stgMrxExtDmcs, transformationContext);
            case DUAL_TRANSFORMATIONS ->
                    processDualTransformationsCase(murexBookings, transformations, config, stgMrxExtDmcs, transformationContext);
        };
    }

    /**
     * Create base MurexBookingDTO objects from FetchDataResponse records
     */
    private List<MurexTradeLeg> createBaseBookings(List<AggregatedDataResponse> records, String murexBookCode) {
        List<MurexTradeLeg> bookings = new ArrayList<>();
        for (AggregatedDataResponse record : records) {
            bookings.add(CopyUtils.clone(record, MurexTradeLeg.class, Map.of("murexBookCode", murexBookCode)));
        }
        return bookings;
    }

    /**
     * Find the NDF transformation node from the transformations array
     */
    private JsonNode findNdfTransformation(List<JsonNode> transformations) {
        return transformations.stream()
                .filter(transformation -> transformation.has("referenceTrade"))
                .filter(transformation -> NDF_TYPOLOGY.equals(transformation.get("referenceTrade").asText()))
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
        if (ndfTransformation.has("referenceSubTradeBuySell")) {
            JsonNode subTrades = ndfTransformation.get("referenceSubTradeBuySell");

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

        throw new RuntimeException("Unable to determine transformation case from NDF configuration");
    }

    /**
     * Case 1: Apply transformation to both MurexBookingDTO objects
     * Both records get the same transformation applied
     */
    private List<MurexTradeLeg> processBothLegsCase(List<MurexTradeLeg> murexBookings,
                                                    JsonNode ndfTransformation,
                                                    MurexBookingConfig config,
                                                    List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                    TransformationContext transformationContext) {

        log.info("Processing NDF Case: Both Legs transformation");

        List<MurexTradeLeg> murexTradeLegs = new ArrayList<>();
        List<MurexTradeLeg> results = new ArrayList<>();

        for (MurexTradeLeg booking : murexBookings) {
            try {
                murexTradeLegs.add(CopyUtils.clone(booking, MurexTradeLeg.class));

                // Apply NDF transformation to each booking
                MurexTradeLeg transformedBooking = applyNdfTransformation(
                        booking, ndfTransformation, config, transformationContext);
                MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedBooking, config);

                results.add(tpsFieldFilteredTrade);

            } catch (Exception e) {
                System.err.println("Error applying transformation to booking " +
                        booking.getExternalDealId() + ": " + e.getMessage());
                throw e;
            }
        }

        generaStgMurexExtDmcRecords(stgMrxExtDmcs, murexTradeLegs, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());

        return results;
    }

    /**
     * Case 2: Identify forwardLeg by latest valueDate and apply transformation only to that record
     */
    private List<MurexTradeLeg> processEmbeddedSpotLegOnlyCase(List<MurexTradeLeg> murexBookings,
                                                               JsonNode ndfTransformation,
                                                               MurexBookingConfig config,
                                                               List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                               TransformationContext transformationContext) {

        log.info("Processing NDF Case: Forward Leg Only transformation");

        // Identify forward leg by latest/furthest valueDate
        MurexTradeLeg embeddedSpotLeg = identifyEmbeddedSpotLeg(murexBookings);


        if (embeddedSpotLeg == null) {
            throw new RuntimeException("Unable to identify embedded spot leg from MurexBookingDTO objects");
        }
        List<MurexTradeLeg> murexTradeLegs = List.of(CopyUtils.clone(embeddedSpotLeg, MurexTradeLeg.class));
        List<MurexTradeLeg> results = new ArrayList<>();

        // Apply transformation only to embedded spot leg
        MurexTradeLeg transformedForwardLeg = applyNdfTransformation(
                embeddedSpotLeg, ndfTransformation, config, transformationContext);
        MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedForwardLeg, config);

        results.add(tpsFieldFilteredTrade);

        generaStgMurexExtDmcRecords(stgMrxExtDmcs, murexTradeLegs, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());

        return results;
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
    private List<MurexTradeLeg> processDualTransformationsCase(List<MurexTradeLeg> murexBookings,
                                                               List<JsonNode> transformations,
                                                               MurexBookingConfig config,
                                                               List<StgMrxExtDmcDto> stgMrxExtDmcs,
                                                               TransformationContext transformationContext) {

        log.info("Processing NDF Case: Dual Transformations");

        // Step 1: Parse and differentiate transformation nodes
        DualTransformationNodes nodes = parseDualTransformations(transformations);

        // Step 2: Identify NDF leg DTOs
        MurexTradeLeg embeddedSpotLeg = identifyEmbeddedSpotLeg(murexBookings);

        List<MurexTradeLeg> tradeLegInputForDMC = List.of(CopyUtils.clone(embeddedSpotLeg, MurexTradeLeg.class));

        MurexTradeLeg forwardLeg = murexBookings.stream()
                .filter(booking -> !booking.equals(embeddedSpotLeg))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unable to identify embedded spot leg"));

        // Step 3: Locate matching FX Spot DTO using all available GroupedRecords
        MurexTradeLeg fxSpotDTO = locateFxSpotDTO(embeddedSpotLeg, config, transformationContext.getAllGroupedRecords());

        // Step 4: Apply inter-DTO field overrides
        MurexTradeLeg modifiedEmbeddedSpotLeg = applyInterDtoFieldOverrides(
                embeddedSpotLeg, forwardLeg, fxSpotDTO, nodes);

        // Step 5: Apply standard transformations to modified embedded spot leg
        MurexTradeLeg transformedEmbeddedSpotLeg = applyNdfTransformation(
                modifiedEmbeddedSpotLeg, nodes.ndfTransformation, config, transformationContext);

        MurexTradeLeg tpsFieldFilteredTrade = applyTPSFieldTransformations(transformedEmbeddedSpotLeg, config);

        // Return both legs with transformations applied
        List<MurexTradeLeg> results = new ArrayList<>();
        results.add(tpsFieldFilteredTrade); // Embedded spot leg with transformations

        generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());

        return results;
    }

    /**
     * Parse dual transformations array and extract NDF and FX Spot transformation nodes
     */
    private DualTransformationNodes parseDualTransformations(List<JsonNode> transformations) {
        JsonNode ndfTransformation = null;
        JsonNode fxSpotTransformation = null;

        for (JsonNode transformation : transformations) {
            if (transformation.has("referenceTrade")) {
                String referenceTrade = transformation.get("referenceTrade").asText();

                if (NDF_TYPOLOGY.equals(referenceTrade)) {
                    ndfTransformation = transformation;
                } else if ("FX Spot".equals(referenceTrade)) {
                    fxSpotTransformation = transformation;
                }
            }
        }

        if (ndfTransformation == null) {
            throw new RuntimeException("No NDF transformation found in dual transformations array");
        }

        if (fxSpotTransformation == null) {
            throw new RuntimeException("No FX Spot transformation found in dual transformations array");
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
    private MurexTradeLeg locateFxSpotDTO(MurexTradeLeg embeddedSpotLeg,
                                          MurexBookingConfig config,
                                          List<GroupedRecord> allGroupedRecords) {

        log.info("Locating FX Spot DTO for embedded spot leg: {}", embeddedSpotLeg.getExternalDealId());

        if (allGroupedRecords == null || allGroupedRecords.isEmpty()) {
            throw new RuntimeException();
        }

        GroupedRecord groupedRecord = allGroupedRecords.stream().filter(record ->
                        record.getTypology().equals("FX Spot") &&
                                record.getNavType().equals(embeddedSpotLeg.getNavType()) &&
                                record.getComment0().equals(embeddedSpotLeg.getComment0()) &&
                                !record.getExternalDealId().equals(embeddedSpotLeg.getExternalDealId())
                ).findAny()
                .orElseThrow(RuntimeException::new);


        // Found matching FX Spot record - convert to DTO
        return CopyUtils.clone(groupedRecord.getRecords().getFirst(), MurexTradeLeg.class);
    }


    /**
     * Apply inter-DTO field overrides based on buy/sell flags in transformations
     * <p>
     * Business Rules:
     * 1. FX Spot transformation buy/sell flags override embedded spot leg currencies from FX Spot DTO
     * 2. NDF transformation buy/sell flags override embedded spot leg currencies from NDF forward DTO
     */
    private MurexTradeLeg applyInterDtoFieldOverrides(MurexTradeLeg embeddedSpotLeg,
                                                      MurexTradeLeg forwardLeg,
                                                      MurexTradeLeg fxSpotDTO,
                                                      DualTransformationNodes nodes) {

        log.info("Applying inter-DTO field overrides");

        // Create a copy of embedded spot leg for modification
        MurexTradeLeg modifiedEmbeddedSpotLeg = CopyUtils.clone(embeddedSpotLeg, MurexTradeLeg.class);

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
    private void applyFxSpotOverrides(MurexTradeLeg embeddedSpotLeg,
                                      MurexTradeLeg fxSpotDTO,
                                      JsonNode fxSpotTransformation) {

        if (!fxSpotTransformation.has("referenceTradeBuySell")) {
            return;
        }

        JsonNode buySellNode = fxSpotTransformation.get("referenceTradeBuySell");

        // Apply buy override (currency1 from FX Spot)
        if (buySellNode.has("buy") && buySellNode.get("buy").asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurrency1();
            embeddedSpotLeg.setCurrency1(fxSpotDTO.getCurrency1());

            log.info("FX Spot buy override: currency1 changed from; {} to {}", originalCurrency, fxSpotDTO.getCurrency1());
        }

        // Apply sell override (currency2 from FX Spot)
        if (buySellNode.has("sell") && buySellNode.get("sell").asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurrency1();
            embeddedSpotLeg.setCurrency2(fxSpotDTO.getCurrency2());

            log.info("FX Spot sell override: currency2 changed from: {} to {}", originalCurrency, fxSpotDTO.getCurrency2());
        }
    }

    /**
     * Apply NDF transformation overrides to embedded spot leg
     * <p>
     * Rules:
     * - If "buy": true in referenceSubTradeBuySell, override currency1 using NDF forward DTO currency1
     * - If "sell": true, override currency2 using NDF forward DTO currency2
     */
    private void applyNdfOverrides(MurexTradeLeg embeddedSpotLeg,
                                   MurexTradeLeg forwardLeg,
                                   JsonNode ndfTransformation) {

        if (!ndfTransformation.has("referenceSubTradeBuySell")) {
            return;
        }

        JsonNode subTradeBuySellArray = ndfTransformation.get("referenceSubTradeBuySell");

        if (!subTradeBuySellArray.isArray() || subTradeBuySellArray.isEmpty()) {
            return;
        }

        // Use the first (and typically only) sub-trade for dual transformations
        JsonNode subTradeBuySell = subTradeBuySellArray.get(0);

        // Apply buy override (currency1 from NDF forward)
        if (subTradeBuySell.has("buy") && subTradeBuySell.get("buy").asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurrency1();
            embeddedSpotLeg.setCurrency1(forwardLeg.getCurrency1());

            log.info("NDF buy override: currency1 changed from: {} to {}", originalCurrency, forwardLeg.getCurrency1());
        }

        // Apply sell override (currency2 from NDF forward)
        if (subTradeBuySell.has("sell") && subTradeBuySell.get("sell").asBoolean()) {
            String originalCurrency = embeddedSpotLeg.getCurrency2();
            embeddedSpotLeg.setCurrency2(forwardLeg.getCurrency2());

            log.info("NDF sell override: currency2 changed from: {} to {} ", originalCurrency, forwardLeg.getCurrency2());
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
    private MurexTradeLeg identifyEmbeddedSpotLeg(List<MurexTradeLeg> murexBookings) {

        MurexTradeLeg embeddedSpotLeg = murexBookings.stream().min(Comparator.comparing(MurexTradeLeg::getValueDate)).orElse(null);

        if (embeddedSpotLeg == null) {
            // Fallback: use first booking if date parsing fails
            System.err.println("Unable to determine forward leg by valueDate");
        }

        return embeddedSpotLeg;
    }

    /**
     * Apply NDF transformation logic to a MurexBookingDTO
     * Follows FX Spot transformation standards with NDF-specific adaptations
     */
    private MurexTradeLeg applyNdfTransformation(MurexTradeLeg booking,
                                                 JsonNode ndfTransformation,
                                                 MurexBookingConfig config,
                                                 TransformationContext transformationContext) {

        // Create a copy of the leg record for transformation
        MurexTradeLeg transformedRecord = CopyUtils.clone(booking, MurexTradeLeg.class);

        try {
            // Apply individual transformation using FX Spot logic (reused)
            applyIndividualTransformation(transformedRecord, ndfTransformation, transformationContext);

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
     * Apply flip currency logic (reused from FX Spot)
     */
    private void applyFlipCurrencyLogic(MurexTradeLeg booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = (BigDecimal) fieldMapper.getFieldValue(booking, "hedgeAmountAllocation");
        if (hedgeAmount == null) hedgeAmount = BigDecimal.ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = (String) fieldMapper.getFieldValue(booking, "currency1");
        String currency2 = (String) fieldMapper.getFieldValue(booking, "currency2");

        // Inverted logic for flipCurrency = true
        if (currenciesInFamily.contains(currency2)) {
            fieldMapper.setFieldValue(booking, "buyTransAmt", hedgeAmount);
            fieldMapper.setFieldValue(booking, "sellTransAmt", hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency1)) {
            fieldMapper.setFieldValue(booking, "sellTransAmt", hedgeAmount);
            fieldMapper.setFieldValue(booking, "buyTransAmt", hedgeAmount.multiply(exchangeRate));
        }
    }

    /**
     * Apply normal transformation logic (reused from FX Spot)
     */
    private void applyNormalTransformationLogic(MurexTradeLeg booking, BigDecimal exchangeRate, List<String> currenciesInFamily) {
        BigDecimal hedgeAmount = (BigDecimal) fieldMapper.getFieldValue(booking, "hedgeAmountAllocation");
        if (hedgeAmount == null) hedgeAmount = BigDecimal.ZERO;
        if (exchangeRate == null) exchangeRate = BigDecimal.ONE;

        String currency1 = (String) fieldMapper.getFieldValue(booking, "currency1");
        String currency2 = (String) fieldMapper.getFieldValue(booking, "currency2");

        if (currenciesInFamily.contains(currency1)) {
            fieldMapper.setFieldValue(booking, "buyTransAmt", hedgeAmount);
            fieldMapper.setFieldValue(booking, "sellTransAmt", hedgeAmount.multiply(exchangeRate));
        } else if (currenciesInFamily.contains(currency2)) {
            fieldMapper.setFieldValue(booking, "sellTransAmt", hedgeAmount);
            fieldMapper.setFieldValue(booking, "buyTransAmt", hedgeAmount.multiply(exchangeRate));
        }
    }

    /**
     * Enumeration for different transformation cases
     */
    private enum TransformationCase {
        BOTH_LEGS,           // Case 1: Apply to both legs
        EMBEDDED_SPOT_LEG_ONLY,    // Case 2: Apply to forward leg only
        DUAL_TRANSFORMATIONS // Case 3: Multiple transformations (Phase 3)
    }

    /**
     * Validation method to ensure GroupedRecord contains valid NDF data
     */
    public void validateNdfGroupedRecord(GroupedRecord groupedRecord) {
        if (groupedRecord == null) {
            throw new IllegalArgumentException("GroupedRecord cannot be null for NDF transformation");
        }

        if (!NDF_TYPOLOGY.equals(groupedRecord.getTypology())) {
            throw new IllegalArgumentException("GroupedRecord typology must be NDF, found: " +
                    groupedRecord.getTypology());
        }

        List<AggregatedDataResponse> records = groupedRecord.getRecords();
        if (records == null || records.size() != 2) {
            throw new IllegalArgumentException("NDF GroupedRecord must contain exactly 2 records, found: " +
                    (records != null ? records.size() : 0));
        }

        // Validate that records have required fields
        for (AggregatedDataResponse record : records) {
            if (record.getExternalDealId() == null || record.getExternalDealId().trim().isEmpty()) {
                throw new IllegalArgumentException("NDF record missing required externalDealId");
            }

            if (record.getValueDate() == null) {
                throw new IllegalArgumentException("NDF record missing required valueDate for leg identification");
            }
        }
    }
}