package com.ruchira.murex.service;

import com.ruchira.murex.util.ConcurrencyUtil;
import com.ruchira.murex.dto.InstructionRequestDto;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.BusinessException;
import com.ruchira.murex.exception.InstructionProcessingException;
import com.ruchira.murex.model.*;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.parser.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ruchira.murex.constant.Constants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboundInstructionProcessingService {

    private final TradeDataHandlerService tradeDataHandlerService;
    private final MurexDownstreamPublisher murexDownstreamPublisher;
    private final JsonParser jsonParser;
    private final MurexDataTransformationService murexDataTransformationService;


    /**
     * Entry point for processing an instruction request in a production-safe manner.
     * <p>
     * This method orchestrates the full instruction processing pipeline:
     * 1. Fetch aggregated data
     * 2. Group and validate records
     * 3. Fetch currency and rule maps
     * 4. Process each grouped record concurrently (all-or-none semantics)
     * 5. Insert transformed records into StgMrxExtDmc table
     * 6. Publish generated trades to downstream systems
     * <p>
     * Error Handling:
     * - Any exception at any stage will trigger a full rollback
     * (no partial writes or downstream publishing)
     * - Concurrent processing failures will cancel remaining tasks
     * - Detailed logging provided for debugging
     * - Failure notification sent via `notifyBookingFailed`
     *
     * @param instructionRequestDto The instruction request payload
     * @throws InstructionProcessingException if any step fails
     */
    @Transactional
    public void processInstruction(final InstructionRequestDto instructionRequestDto) throws Exception {
        log.info("Processing instruction event: {}", instructionRequestDto.getInstructionEvent());
        long start = System.currentTimeMillis();
        try {
            // Step 1: fetch aggregated data
            List<AggregatedDataResponse> results = fetchAggregatedData(instructionRequestDto);

            // Step 2: group and validate records
            List<GroupedRecord> groupedRecords = groupAndValidate(results);

            // Step 3: fetch currency and rule map
            List<Currency> currencies = fetchCurrencyData(instructionRequestDto);
            List<String> currenciesInFamily = extractCurrencies(currencies);
            Map<String, InstructionEventConfig> ruleMap = fetchBusinessEventRuleMap(instructionRequestDto, currencies);

            // Step 4: process records
            RecordProcessingResult processingResult = processGroupedRecords(groupedRecords, instructionRequestDto, ruleMap, currenciesInFamily);


            //Step 5: Insert StgMrxExtDmc Data to Database
            insertStgMrxExtDmcRecordsToDatabase(processingResult.getAllStgMrxExtDmcs());

            // Step 5: publish trades to downstream systems and databases This is handled in its Onw Transaction Context
            publishGeneratedMurexTrades(processingResult.getAllMurexTrades());

            long end = System.currentTimeMillis();
            log.info("Time Taken: {}", end - start);
        } catch (Exception ex) {
            log.error("Instruction processing failed for {}: {}", instructionRequestDto.getInstructionEvent(), ex.getMessage(), ex);
            throw new InstructionProcessingException(String.format("Failed to process instruction: %s", instructionRequestDto.getInstructionEvent()), ex);
        }
    }

    private RecordProcessingResult processGroupedRecords(List<GroupedRecord> groupedRecords,
                                                         InstructionRequestDto requestDto,
                                                         Map<String, InstructionEventConfig> ruleMap,
                                                         List<String> currenciesInFamily) throws Exception {
        List<MurexTrade> allMurexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> allStgMrxExtDmcs = new ArrayList<>();

        ConcurrencyUtil.processAllOrNone(
                groupedRecords,
                record -> processRecord(
                        record,
                        requestDto,
                        ruleMap,
                        groupedRecords,
                        allStgMrxExtDmcs,
                        allMurexTrades,
                        currenciesInFamily
                )
        );

        return new RecordProcessingResult(allStgMrxExtDmcs, allMurexTrades);
    }

    private List<String> extractCurrencies(List<Currency> currencies) {
        return currencies.stream().map(Currency::getOriginalCurrency).toList();
    }

    /**
     * Fetches raw aggregated data required for processing instructions.
     *
     * @param dto The instruction request DTO containing filter criteria
     * @return List of aggregated data responses
     */
    private List<AggregatedDataResponse> fetchAggregatedData(InstructionRequestDto dto) {
        return tradeDataHandlerService.fetchData(dto.getBusinessDate(), dto.getExternalTradeIds(), dto.getHedgeInstrumentType(), dto.getCurrency());
    }


    private void insertStgMrxExtDmcRecordsToDatabase(List<StgMrxExtDmcDto> dmcDtoList) {
        tradeDataHandlerService.insertStgMrxExtDmcRecordsToDatabase(dmcDtoList);
    }

    /**
     * Fetches currency data required for processing instructions.
     *
     * @param dto The instruction request DTO containing filter criteria
     * @return List of Currency data responses
     */
    private List<Currency> fetchCurrencyData(InstructionRequestDto dto) {
        return tradeDataHandlerService.fetchCurrencyConfigs(dto.getCurrency());
    }

    /**
     * Performs grouping and validation of the raw aggregated data.
     * This ensures that records are prepared and validated before business rules are applied.
     *
     * @param results Raw aggregated results fetched from data service
     * @return Grouped and validated records
     */
    private List<GroupedRecord> groupAndValidate(List<AggregatedDataResponse> results) {
        return tradeDataHandlerService.performGroupingAndValidation(results);
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

        List<InstructionEventConfig> configs = tradeDataHandlerService.fetchBusinessEventRules(
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
            log.warn("No rule configuration found for navType={} in instructionEvent={}",
                    record.getNavType(), dto.getInstructionEvent());
            return; // skip or throw a business exception depending on use case
        }

        // Step 1: fetch murex booking configs linked to this rule
        List<MurexBookingConfig> bookConfigs = tradeDataHandlerService.fetchMurexBookConfigs(ruleConfig.getRuleId());

        // Step 2: generate bookings using record configs
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

    @Async
    private void publishGeneratedMurexTrades(List<MurexTrade> trades) {
        if (CollectionUtils.isEmpty(trades)) {
            log.warn("No trades to publish to Database or Downstream");
            return;
        }
        for (MurexTrade trade : trades) {
            final String tradeRef = trade.getTradeReference();
            try {
                log.debug("Starting processing for trade ID: {}", trade.getTradeReference());

                publishMurexBookingToDatabase(trade);
                publishMurexTradesToDownStream(trade);
            } catch (Exception e) {
                log.error("Failed to publish the GeneratedMurexTrade trade {}: {}", tradeRef, e.getMessage(), e);
            }
        }

    }

    private void publishMurexTradesToDownStream(MurexTrade murexTrade) {
        murexDownstreamPublisher.publishMurexTradesToDownStream(murexTrade);
    }

    /**
     * Inserts a MurexTradeDTO into the database including near/far legs and their components.
     *
     * <p>For each trade:
     * - Inserts the main trade and retrieves tradeId.
     * - Inserts the NearLeg (if present) and its components.
     * - Inserts the FarLeg (if present) and its components.
     * </p>
     * <p>
     * Uses ObjectMapper to convert POJOs to Maps for named-parameter JDBC inserts.
     * Logs errors with full context for traceability.
     *
     * @param murexTrade list of trades to insert
     */
    private void publishMurexBookingToDatabase(MurexTrade murexTrade) {
        tradeDataHandlerService.publishMurexBookingToDatabase(murexTrade);
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
        if (groupedRecord.getTypology().equals(FX_NDF_TYPOLOGY)) {
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
            List<JsonNode> transformations = jsonParser.parseTransformations(transformationsJson);

            if (transformations.isEmpty()) {
                return false;
            }

            // Apply different filtering logic based on typology
            if (FX_SPOT_TYPOLOGY.equals(typology) || FX_SWAP_TYPOLOGY.equals(typology)) {
                // FX Spot/Swap: STRICT matching - only single-element arrays with exact match
                return isSingleElementExactMatch(transformations, typology);

            } else if (FX_NDF_TYPOLOGY.equals(typology)) {
                // NDF: Flexible matching - single NDF OR multi-element containing NDF
                return isNdfCompatibleConfig(transformations);

            } else {
                // Future typologies: default to single-element exact match
                return isSingleElementExactMatch(transformations, typology);
            }

        } catch (Exception e) {
            log.error("Error evaluating config inclusion for typology: {} : ", typology, e);
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
        if (transformation.has(REFERENCE_TRADE_FIELD)) {
            String referenceTrade = transformation.get(REFERENCE_TRADE_FIELD).asText();
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
            if (transformation.has(REFERENCE_TRADE_FIELD)) {
                String referenceTrade = transformation.get(REFERENCE_TRADE_FIELD).asText();
                if (FX_NDF_TYPOLOGY.contains(referenceTrade)) {
                    return true;
                }
            }
        }

        return false;
    }
}
