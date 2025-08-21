package com.ruchira.murex.service;

import com.ruchira.murex.dto.InstructionRequestDto;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.BusinessException;
import com.ruchira.murex.kafka.producer.KafkaPublisherHandler;
import com.ruchira.murex.kafka.dto.MurexTradeOutBound;
import com.ruchira.murex.model.*;
import com.ruchira.murex.parser.JsonTransformationParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundInstructionProcessingService {

    private final TradeDataQueryService tradeDataQueryService;
    private final StgMrxExtProcessingService stgMrxExtProcessingService;
    private final JsonTransformationParser jsonTransformationParser;
    private final MurexDataTransformationService murexDataTransformationService;
    private final KafkaPublisherHandler publisherHandler;

    /**
     * Entry point for processing an instruction request.
     * This orchestrates the full pipeline:
     * 1. Fetch aggregated data
     * 2. Group & validate records
     * 3. Fetch event configuration rules
     * 4. Apply rules per record (generate bookings + publish trades)
     *
     * @param instructionRequestDto The request payload containing instruction details
     */
    public void processInstruction(final InstructionRequestDto instructionRequestDto) {
        log.info("Processing instruction event: {}", instructionRequestDto.getInstructionEvent());

        // Step 1: fetch aggregated data
        List<AggregatedDataResponse> results = fetchAggregatedData(instructionRequestDto);

        // Step 2: group and validate records
        List<GroupedRecord> groupedRecords = groupAndValidate(results);

        List<Currency> currencies = fetchCurrencyData(instructionRequestDto);
        List<String> currenciesInFamily = currencies.stream().map(Currency::getOriginalCurrency).toList();

        // Step 3: fetch configuration rules and build rule map
        Map<String, InstructionEventConfig> ruleMap = fetchBusinessEventRuleMap(instructionRequestDto, currencies);

        // Collect all trades across grouped records (publishing deferred)
        List<MurexTrade> allMurexTrades = new ArrayList<>();

        // Collect all StgMrxExtDmcDto across grouped records
        List<StgMrxExtDmcDto> allStgMrxExtDmcs = new ArrayList<>();

        // Step 4: process each grouped record against rule map
        groupedRecords.forEach(record -> processRecord(record, instructionRequestDto, ruleMap, groupedRecords, allStgMrxExtDmcs, allMurexTrades, currenciesInFamily));

        // Step 5: publish trades to downstream systems
        publishTrades(allMurexTrades);
    }

    /**
     * Fetches raw aggregated data required for processing instructions.
     *
     * @param dto The instruction request DTO containing filter criteria
     * @return List of aggregated data responses
     */
    private List<AggregatedDataResponse> fetchAggregatedData(InstructionRequestDto dto) {
        return tradeDataQueryService.fetchData(dto.getInstructionEvent(), dto.getExternalTradeIds(), dto.getCurrency());
    }

    /**
     * Fetches currency data required for processing instructions.
     *
     * @param dto The instruction request DTO containing filter criteria
     * @return List of Currency data responses
     */
    private List<Currency> fetchCurrencyData(InstructionRequestDto dto) {
        return tradeDataQueryService.fetchCurrencyConfigs(dto.getCurrency());
    }

    /**
     * Performs grouping and validation of the raw aggregated data.
     * This ensures that records are prepared and validated before business rules are applied.
     *
     * @param results Raw aggregated results fetched from data service
     * @return Grouped and validated records
     */
    private List<GroupedRecord> groupAndValidate(List<AggregatedDataResponse> results) {
        return tradeDataQueryService.performGroupingAndValidation(results);
    }

    /**
     * Fetches business event rules and converts them into a lookup map by navType.
     * The map is used to quickly retrieve the appropriate configuration rule for each grouped record.
     *
     * @param dto The instruction request DTO containing event, hedge method, etc.
     * @return A map of navType -> InstructionEventConfig
     */
    private Map<String, InstructionEventConfig> fetchBusinessEventRuleMap(InstructionRequestDto dto, List<Currency> currencies) {

        final String currencyCategory = currencies.getFirst().getCurrencyCategory();

        List<InstructionEventConfig> configs = tradeDataQueryService.fetchBusinessEventRules(
                dto.getInstructionEvent(),
                dto.getHedgeMethod(),
                dto.getHedgeInstrumentType(),
                currencyCategory
        );

        if (configs.isEmpty()) {
            log.warn("No business event configs found for event={} hedgeMethod={} hedgeInstrumentType={}",
                    dto.getInstructionEvent(), dto.getHedgeMethod(), dto.getHedgeInstrumentType());
        }

        return configs.stream()
                .collect(Collectors.toMap(InstructionEventConfig::getNavType, cfg -> cfg));
    }

    /**
     * Processes a single grouped record without publishing immediately:
     * - Retrieves the appropriate rule configuration based on navType
     * - Fetches related Murex booking configurations
     * - Generates Murex trades
     * - Generates staging DTOs (StgMrxExtDmcDto)
     * - Appends generated trades and DTOs to the provided lists for deferred publishing
     * <p>
     * This ensures that trades are published only if all grouped records are processed successfully.
     *
     * @param record              Grouped record being processed
     * @param dto                 Instruction request DTO providing context
     * @param ruleMap             Precomputed map of navType -> InstructionEventConfig
     * @param groupedRecords      Complete list of grouped records (for context in booking generation)
     * @param allStgMrxExtDmcDtoS Accumulator list to collect all generated staging DTOs
     * @param allMurexTrades      Accumulator list to collect all generated Murex trades
     */
    private void processRecord(GroupedRecord record,
                               InstructionRequestDto dto,
                               Map<String, InstructionEventConfig> ruleMap,
                               List<GroupedRecord> groupedRecords,
                               List<StgMrxExtDmcDto> allStgMrxExtDmcDtoS,
                               List<MurexTrade> allMurexTrades,
                               List<String> currenciesInFamily) {
        InstructionEventConfig ruleConfig = ruleMap.get(record.getNavType());

        if (ruleConfig == null) {
            log.error("No rule configuration found for navType={} in instructionEvent={}",
                    record.getNavType(), dto.getInstructionEvent());
            return; // skip or throw a business exception depending on use case
        }

        // Step 1: fetch murex booking configs linked to this rule
        List<MurexBookingConfig> bookConfigs = tradeDataQueryService.fetchMurexBookConfigs(ruleConfig.getMrxBookCodeIds());

        // Step 2: generate bookings using record + configs
        Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> bookingPair =
                generateMurexBookings(record, bookConfigs, dto.getCurrency(), ruleConfig.getRuleId(), groupedRecords, currenciesInFamily);

        allMurexTrades.addAll(bookingPair.getValue());
        allStgMrxExtDmcDtoS.addAll(bookingPair.getKey());
    }


    /**
     * Publishes Murex trades to the outbound messaging system.
     *
     * @param trades List of trades to publish
     */
    private void publishTrades(List<MurexTrade> trades) {
        trades.forEach(trade -> {
            log.debug("Publishing Murex trade: {}", trade);
            publisherHandler.publish("murex-topic",
                    MurexTradeOutBound.builder().murexTrade(trade).build());
        });
    }

    /**
     * Generates Murex booking records and corresponding StgMrxExtDmc record details
     * using transformation logic.
     *
     * <p>This method applies booking transformations on the provided grouped record,
     * leveraging Murex book configurations and input currency to compute
     * the final booking results. It also associates the results with an
     * instruction event rule identifier and may consider multiple grouped records
     * for cross-record processing.</p>
     *
     * @param groupedRecord          The primary validated grouped record to transform
     * @param murexConfigs           List of Murex book configurations used for filtering and processing
     * @param inputCurrency          Input currency for transformation and calculation logic
     * @param instructionEventRuleId Identifier for the instruction event rule driving transformation logic
     * @param groupedRecords         Additional grouped records that may influence transformation logic
     * @return A pair containing:
     * <ul>
     *   <li>List of transformed {@link StgMrxExtDmcDto} booking DTOs</li>
     *   <li>List of corresponding {@link MurexTrade} trade details</li>
     * </ul>
     */
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> generateMurexBookings(GroupedRecord groupedRecord,
                                                                               List<MurexBookingConfig> murexConfigs,
                                                                               String inputCurrency,
                                                                               String instructionEventRuleId,
                                                                               List<GroupedRecord> groupedRecords,
                                                                               List<String> currenciesInFamily) {

        // Step 1: Filter MurexBookConfig records based on typology matching (reuse existing logic)
        List<MurexBookingConfig> filteredMurexConfigs = filterMurexConfigsByTypology(murexConfigs, groupedRecord.getTypology());
        TransformationContext transformationContext = TransformationContext.builder()
                .filteredMurexConfigs(filteredMurexConfigs)
                .groupedRecord(groupedRecord)
                .inputCurrency(inputCurrency)
                .currenciesInFamily(currenciesInFamily)
                .instructionEventRuleId(instructionEventRuleId)
                .build();
        if (groupedRecord.getTypology().equals("NDF")) {
            transformationContext.setAllGroupedRecords(groupedRecords);
        }

        // Step 2: Pass to advanced transformation service for booking generation
        return murexDataTransformationService.generateMurexBookings(transformationContext);
    }


    /**
     * Enhanced filter for MurexBookConfig records with strict typology separation
     * <p>
     * Filtering Strategy:
     * - FX Spot/Swap: ONLY matches single-element arrays where the sole referenceTrade equals typology
     * - NDF: Matches both single-element NDF arrays AND multi-element arrays containing NDF
     * <p>
     * This prevents NDF multi-element configs from being incorrectly matched for FX Spot/Swap processing
     * <p>
     * Transformation Array Examples:
     * FX Spot Config:     [{"referenceTrade": "FX Spot", ...}] → Matches ONLY FX Spot typology
     * FX Swap Config:     [{"referenceTrade": "FX Swap", ...}] → Matches ONLY FX Swap typology
     * NDF Single Config:  [{"referenceTrade": "NDF", ...}] → Matches ONLY NDF typology
     * NDF Multi Config:   [{"referenceTrade": "FX Spot", ...}, {"referenceTrade": "NDF", ...}] → Matches ONLY NDF typology
     *
     * @param murexConfigs List of Murex configurations to filter
     * @param typology     The typology to match against (e.g., "FX Spot", "FX Swap", "NDF")
     * @return Filtered list of MurexBookConfig records that match the typology
     */
    private List<MurexBookingConfig> filterMurexConfigsByTypology(List<MurexBookingConfig> murexConfigs, String typology) {

        if (murexConfigs == null || murexConfigs.isEmpty() || typology == null) {
            return Collections.emptyList();
        }

        List<MurexBookingConfig> filteredConfigs = new ArrayList<>();

        for (MurexBookingConfig config : murexConfigs) {
            try {
                String transformationsJson = config.getTransformations();

                // Apply conditional filtering logic based on typology
                if (shouldIncludeConfig(transformationsJson, typology)) {
                    filteredConfigs.add(config);
                }

            } catch (Exception e) {
                log.error("Error parsing transformations for MurexBookConfig ID {} : ", config.getId(), e);
                throw new BusinessException(e.getMessage());
            }
        }

        return filteredConfigs;
    }

    /**
     * Determine if a MurexBookConfig should be included for the given typology using strict filtering rules.
     *
     * @param transformationsJson JSON string containing transformations
     * @param typology            The typology to match against
     * @return true if config should be included, false otherwise
     */
    private boolean shouldIncludeConfig(String transformationsJson, String typology) {
        try {
            List<JsonNode> transformations = jsonTransformationParser.parseTransformations(transformationsJson);

            if (transformations.isEmpty()) {
                return false;
            }

            // Apply different filtering logic based on typology
            if ("FX Spot".equals(typology) || "FX Swap".equals(typology)) {
                // FX Spot/Swap: STRICT matching - only single-element arrays with exact match
                return isSingleElementExactMatch(transformations, typology);

            } else if ("NDF".equals(typology)) {
                // NDF: Flexible matching - single NDF OR multi-element containing NDF
                return isNdfCompatibleConfig(transformations);

            } else {
                // Future typologies: default to single-element exact match
                return isSingleElementExactMatch(transformations, typology);
            }

        } catch (Exception e) {
            log.error("Error evaluating config inclusion for typology: {} : ", typology , e);
            return false;
        }
    }

    /**
     * Check if transformations array is single-element with exact referenceTrade match.
     * Used for FX Spot, FX Swap, and other single-element typologies.
     */
    private boolean isSingleElementExactMatch(List<JsonNode> transformations, String typology) {
        // Must be exactly 1 transformation
        if (transformations.size() != 1) {
            return false;
        }

        JsonNode transformation = transformations.getFirst();
        if (transformation.has("referenceTrade")) {
            String referenceTrade = transformation.get("referenceTrade").asText();
            return typology.equals(referenceTrade);
        }

        return false;
    }

    /**
     * Check if transformations array is compatible with NDF processing.
     * Accepts both single NDF elements and multi-element arrays containing NDF.
     */
    private boolean isNdfCompatibleConfig(List<JsonNode> transformations) {
        // Check if any transformation contains "NDF" referenceTrade
        for (JsonNode transformation : transformations) {
            if (transformation.has("referenceTrade")) {
                String referenceTrade = transformation.get("referenceTrade").asText();
                if ("NDF".equals(referenceTrade)) {
                    return true;
                }
            }
        }

        return false;
    }
}
