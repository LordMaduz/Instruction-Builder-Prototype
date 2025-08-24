package com.ruchira.murex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.ruchira.murex.constant.Constants;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.freemaker.FtlQueryBuilder;
import com.ruchira.murex.kafka.model.HAWKMurexBookingRecord;
import com.ruchira.murex.model.AggregatedDataResponse;
import com.ruchira.murex.model.Currency;
import com.ruchira.murex.model.GroupedRecord;
import com.ruchira.murex.model.InstructionEventConfig;
import com.ruchira.murex.model.MurexBookingConfig;
import com.ruchira.murex.exception.ValidationException;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import com.ruchira.murex.model.trade.MurexTradeLegComponent;
import com.ruchira.murex.parser.JsonParser;
import com.ruchira.murex.repository.GenericJdbcDataRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.ruchira.murex.constant.Constants.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeDataHandlerService {

    private final GenericJdbcDataRepository repository;
    private final FtlQueryBuilder ftlQueryBuilder;
    private final JsonParser jsonParser;

    /**
     * Fetches aggregated data by joining records across four tables,
     * filtered by business date, external trade IDs, and currency.
     *
     * <p>The method retrieves relevant trade and booking information,
     * consolidates it into a structured response, and ensures only
     * records matching all provided filters are included.</p>
     *
     * @param businessDate     The business date used as a filter criterion
     * @param externalTradeIds Comma-separated string of external trade IDs to filter results
     * @param currency         The ISO 4217 currency code to restrict results to a specific currency
     * @return List of {@link AggregatedDataResponse} objects containing the joined and aggregated data
     */
    public List<AggregatedDataResponse> fetchData(
            final String businessDate,
            final String externalTradeIds,
            final String typology,
            final String currency
    ) {

        // Parse the comma-separated external trade IDs
        List<String> tradeIdList = Arrays.stream(externalTradeIds.split(":"))
                .map(String::trim)
                .toList();

        // Build the SQL query
        Map<String, Object> inputs = Map.of(
                "businessDate", businessDate,
                "contractList", tradeIdList,
                "typologyMx3", typology,
                "inputCurrency", currency,
                "USDCurrency", Constants.FUNCTIONAL_CURRENCY_USD,
                "tradingPortf", TRADING_PORTFOLIO_SG_BANK_SFX
        );
        String sql = ftlQueryBuilder.buildQuery(inputs, "aggregatedDataFetch.ftl");
        return repository.fetchData(sql, createRowMapper());
    }

    /**
     * Rule ID Lookup
     * Queries h_business_event_config table for rule configurations based on input parameters.
     * Returns exactly 2 Rule IDs (one for COI, one for RE and Reserves) for each unique combination.
     *
     * @param instructionEvent      The instruction event type
     * @param hedgeMethod           hedge Method
     * @param hedgingInstrumentType Type of hedging instrument
     * @param currencyType          CurrencyType (Restricted/Non-Restricted)
     * @return List of {@link InstructionEventConfig} matching the criteria
     */
    public List<InstructionEventConfig> fetchBusinessEventRules(
            final String instructionEvent,
            final String hedgeMethod,
            final String hedgingInstrumentType,
            final String currencyType) {

        Map<String, Object> inputs = Map.of(
                "businessEvent", instructionEvent,
                "hedgingInstrument", hedgingInstrumentType,
                "hedgeMethod", hedgeMethod,
                "currencyType", currencyType,
                "status", 1
        );

        String sql = ftlQueryBuilder.buildQuery(inputs, FETCH_BUSINESS_EVENT_RULE_FTL_FILE);

        List<InstructionEventConfig> rules = repository.fetchData(sql, createBusinessEventConfigRowMapper());

        // Validation: Should have exactly 2 rules (COI and RE)
        if (rules.size() != 2) {
            throw new IllegalStateException(String.format(
                    "Expected exactly 2 rule configurations for combination [%s, %s, %s, %s], but found %d",
                    instructionEvent, hedgeMethod, hedgingInstrumentType, currencyType, rules.size()));
        }

        return rules;
    }

    /**
     * Fetches murex book configurations based on comma-separated IDs
     *
     * @param ruleId instruction event rule id
     * @return List of {@link MurexBookingConfig} objects
     */
    @Cacheable(value = "murexConfigs", key = "#ruleId")
    public List<MurexBookingConfig> fetchMurexBookConfigs(String ruleId) {
        String sql = ftlQueryBuilder.buildQuery(Map.of("ruleId", ruleId), FETCH_MUREX_BOOK_CODES_FTL_FILE);
        return repository.fetchData(sql, createMurexBookConfigRowMapper());
    }

    public List<Currency> fetchCurrencyConfigs(final String currency) {

        final Map<String, Object> inputs = Map.of("currency", currency, "isActive", 1);
        final String sql = ftlQueryBuilder.buildQuery(inputs, FETCH_CURRENCY_CONFIG_FTL_FILE);
        return repository.fetchData(sql, createCurrencyRowMapper());
    }

    @Transactional
    public void insertStgMrxExtDmcRecordsToDatabase(List<StgMrxExtDmcDto> dmcDtoList) {
        final String sql = ftlQueryBuilder.buildQuery(Map.of(), INSERT_DATA_TO_STG_MTX_EXT_DMC_FTL_FILE);
        repository.executeBatch(sql, dmcDtoList);
    }

    /**
     * Inserts a MurexTrade into the database including near/far legs and their components.
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishMurexBookingToDatabase(
            final MurexTrade murexTrade,
            final HAWKMurexBookingRecord murexBookingRecord
    ) {

        final String tradeRef = murexTrade.getTradeReference();
        try {

            // 1. Insert Main trade
            Long tradeId = insertMainTrade(murexTrade, murexBookingRecord);
            log.info("Inserted main trade {} with ID {}", tradeRef, tradeId);

            insertTradeLegIfPresent(murexTrade.getNearLeg(), tradeId, NEAR_LEG_TYPE, tradeRef);
            insertTradeLegIfPresent(murexTrade.getFarLeg(), tradeId, FAR_LEG_TYPE, tradeRef);

        } catch (Exception e) {
            log.error("Failed to insert trade {}: {}", tradeRef, e.getMessage(), e);
        }
    }

    private Long insertMainTrade(
            final MurexTrade trade,
            final HAWKMurexBookingRecord murexBookingRecord
    ) throws JsonProcessingException {
        final Map<String, Object> tradeMap = jsonParser.convertValue(trade);
        final String murexBookingJsonString = jsonParser.serializesToJsonString(murexBookingRecord);
        tradeMap.put("murexBookingRecord", murexBookingJsonString);

        final String sql = ftlQueryBuilder.buildQuery(Map.of(), INSERT_DATA_TO_MUREX_BOOKING_FTL_FILE);
        return repository.insertAndReturnId(sql, tradeMap, "id");
    }

    private void insertTradeLegIfPresent(MurexTradeLeg leg, Long tradeId, String legType, String tradeRef) {
        if (leg == null) return;

        try {
            // Convert leg and additional fields
            final Map<String, Object> legMap = jsonParser.convertValue(leg);

            final Map<String, Object> additionalFieldsMap = jsonParser.convertValue(leg.getAdditionalFields());
            legMap.putAll(additionalFieldsMap);
            legMap.put("legType", legType);
            legMap.put("tradeId", tradeId);

            // Insert leg and get generated leg ID
            final String murexSql = ftlQueryBuilder.buildQuery(Map.of(), INSERT_DATA_TO_MUREX_BOOK_TRADE_LEG_FTL_FILE);
            Long tradeLegId = repository.insertAndReturnId(murexSql, legMap, "id");

            log.info("Inserted {} leg for trade {} with leg ID {}", legType, tradeRef, tradeLegId);

            // Insert leg components if any
            List<MurexTradeLegComponent> components = leg.getComponents();
            if (CollectionUtils.isNotEmpty(components)) {
                List<Map<String, Object>> componentMaps = jsonParser.convertValue(components);
                componentMaps.forEach(component -> component.put("tradeLegId", tradeLegId));

                final String componentSql = ftlQueryBuilder.buildQuery(Map.of(), INSERT_DATA_TO_MUREX_BOOK_TRADE_LEG_COMPONENTS_FTL_FILE);
                repository.executeBatch(componentSql, componentMaps);
                log.info("Inserted {} components for {} leg of trade {}", components.size(), legType, tradeRef);
            }
        } catch (Exception e) {
            log.error("Failed to insert {} leg for trade {}: {}", legType, tradeRef, e.getMessage(), e);
            throw e; // Rethrow if you want the main loop to handle or collect failed trades
        }
    }

    /**
     * Grouping & Validating Results
     * Groups the fetch results by external_deal_id, comment_0, and nav_type.
     * Validates that each group has the correct number of records based on typology:
     * - FX Spot: exactly 1 record per group
     * - FX Swap: exactly 2 records per group
     *
     * @param fetchResults List of records from Stage 1 fetch operation
     * @return List of validated GroupedRecord objects
     * @throws ValidationException if validation rules are violated
     */
    public List<GroupedRecord> performGroupingAndValidation(List<AggregatedDataResponse> fetchResults) {

        // Group by contract, comment_0, and nav_type
        Map<GroupingKey, List<AggregatedDataResponse>> groupedMap = fetchResults.stream()
                .collect(Collectors.groupingBy(record ->
                        new GroupingKey(record.getContract(), record.getComment0(), record.getNavType())));


        List<GroupedRecord> validatedGroups = new ArrayList<>();

        for (Map.Entry<GroupingKey, List<AggregatedDataResponse>> entry : groupedMap.entrySet()) {
            GroupingKey groupKey = entry.getKey();
            List<AggregatedDataResponse> records = entry.getValue();

            if (records.isEmpty()) {
                continue; // Skip empty groups
            }

            // Extract group identifiers from first record (all records in group should have same values)
            AggregatedDataResponse firstRecord = records.getFirst();
            GroupedRecord groupedRecord = getGroupedRecord(records, groupKey, firstRecord.getTypologyMx3());
            validatedGroups.add(groupedRecord);
        }

        return validatedGroups;
    }

    private static GroupedRecord getGroupedRecord(List<AggregatedDataResponse> records, GroupingKey groupKey, String typology) {
        String externalDealId = groupKey.getContract();
        String comment0 = groupKey.getComment0();
        String navType = groupKey.getNavType();

        // Validation Rules
        if (FX_SPOT_TYPOLOGY.equals(typology)) {
            if (records.size() != 1) {
                throw new ValidationException(
                        String.format("FX Spot group must have exactly 1 record, but found %d", records.size()),
                        groupKey.toString(), typology, records.size());
            }
        } else if (FX_SWAP_TYPOLOGY.equals(typology) || FX_NDF_TYPOLOGY.equals(typology)) {
            if (records.size() != 2) {
                throw new ValidationException(
                        String.format("%s group must have exactly 2 records, but found %d", typology, records.size()),
                        groupKey.toString(), typology, records.size());
            }
        } else {
            throw new ValidationException(
                    String.format("Unknown typology '%s' for group", typology),
                    groupKey.toString(), typology, records.size());
        }

        // Create validated GroupedRecord
        return new GroupedRecord(externalDealId, comment0, navType, typology, records);
    }

    /**
     * Row mapper for AggregatedDataResponse objects
     */
    private RowMapper<AggregatedDataResponse> createRowMapper() {
        return new BeanPropertyRowMapper<>(AggregatedDataResponse.class);
    }

    /**
     * Row mapper for InstructionEventConfig
     */
    private RowMapper<InstructionEventConfig> createBusinessEventConfigRowMapper() {
        return new BeanPropertyRowMapper<>(InstructionEventConfig.class);
    }

    /**
     * Row mapper for MurexBookConfig
     */
    private RowMapper<MurexBookingConfig> createMurexBookConfigRowMapper() {
        return new BeanPropertyRowMapper<>(MurexBookingConfig.class);
    }

    /**
     * Row mapper for Currency
     */
    private RowMapper<Currency> createCurrencyRowMapper() {
        return new BeanPropertyRowMapper<>(Currency.class);
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class GroupingKey {
        private String contract;
        private String comment0;
        private String navType;


        @Override
        public String toString() {
            return String.join("|",
                    contract != null ? contract : "",
                    comment0 != null ? comment0 : "",
                    navType != null ? navType : ""
            );
        }
    }
}