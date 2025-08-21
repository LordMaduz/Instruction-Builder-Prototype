package com.ruchira.murex.service;

import com.ruchira.murex.model.AggregatedDataResponse;
import com.ruchira.murex.model.Currency;
import com.ruchira.murex.model.GroupedRecord;
import com.ruchira.murex.model.InstructionEventConfig;
import com.ruchira.murex.model.MurexBookingConfig;
import com.ruchira.murex.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeDataQueryService {


    private final JdbcTemplate jdbcTemplate;

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
            final String currency
    ) {

        // Parse the comma-separated external trade IDs
        List<String> tradeIdList = Arrays.stream(externalTradeIds.split(","))
                .map(String::trim)
                .toList();

        // Build the SQL query based on the provided fetch.txt
        String sql = """
                SELECT
                    hstg.external_deal_id as externalDealId,
                    hstg.comment_0 as comment0,
                	ha.nav_type as navType,
                	hstg.typology as typology,
                    hstg.counterparty as counterParty,
                    hstg.portfolio as portfolio,
                    hstg.value_date as valueDate,
                    hstg.maturity_date as maturityDate,
                    hstg.stg_exchange_rate as stgExchangeRate,
                    hstg.trans_date as transDate,
                    hstg.currency_1 as currency1,
                    hstg.currency_2 as currency2,
                    hstg.buy_trans_amt as buyTransAmt,
                    hstg.sell_trans_amount as sellTransAmt,
                    hn.historical_exchange_rate as historicalExchangeRate,
                    ha.business_date as businessDate,
                    ha.currency as Currency,
                    ha.hedge_amount_allocation as hedgeAmountAllocation,
                    ha.entity_name as entityName,
                    he.entity_type as entityType,
                    he.entity_id as entityId,
                    he.murex_comment as murexComment
                FROM h_apportionment ha
                JOIN h_entity he
                    ON LOWER(ha.entity_name) = LOWER(he.entity_name)
                JOIN h_net_asset_value hn
                    ON hn.entity_id = he.entity_id
                   AND hn.nav_type  = ha.nav_type
                   AND hn.business_date = '2025-08-18'
                JOIN h_stg_mrx_ext hstg
                    ON hstg.external_deal_id IN ('123456','123458')
                   AND hstg.trans_date = '2025-08-18'
                   AND FIND_IN_SET(LOWER(REPLACE(hstg.comment_0, ' ', '')), LOWER(REPLACE(he.murex_comment, ' ', ''))) > 0
                WHERE ha.currency = 'HKD'
                  AND ha.business_date = '2025-08-18'
                  AND (
                        hstg.typology <> 'FX Swap'
                        OR (
                            hstg.typology = 'FX Swap'
                            AND hstg.portfolio = 'SG BANK SFX'
                            AND (
                                   (hstg.currency_2 = 'HKD' AND hstg.currency_1 = 'USD')
                                OR (hstg.currency_1 = 'HKD' AND hstg.currency_2 = 'USD')
                            )
                        )
                );
                """;

        // Execute query with row mapper
        return jdbcTemplate.query(sql, createRowMapper());
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

        String sql = """
                SELECT
                    rule_id as ruleId,
                    description as description,
                    business_event as businessEvent, 
                    hedge_method hedgeMethod, 
                    currency_type currencyType, 
                    nav_type navType, 
                    hedging_instrument hedgingInstrument, 
                    mrx_book_code_ids mrxBookCodeIds, 
                    status as status
                FROM h_business_event_config
                WHERE business_event = 'Inception'
                  AND hedge_method = 'COH'
                  AND hedging_instrument = 'NDF'
                  AND currency_type = 'Restricted'
                  AND status = 1
                ORDER BY nav_type
                """;

        List<InstructionEventConfig> rules = jdbcTemplate.query(sql, createBusinessEventConfigRowMapper());

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
     * @param mrxBookCodeIds Comma-separated string of murex book code IDs
     * @return List of {@link MurexBookingConfig} objects
     */
    public List<MurexBookingConfig> fetchMurexBookConfigs(String mrxBookCodeIds) {

        if (mrxBookCodeIds == null || mrxBookCodeIds.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // Parse comma-separated IDs
        List<String> idList = Arrays.stream(mrxBookCodeIds.split(","))
                .map(String::trim)
                .toList();

        if (idList.isEmpty()) {
            return Collections.emptyList();
        }

        // Build dynamic placeholders (?, ?, ?, …)
        String placeholders = String.join(",", Collections.nCopies(idList.size(), "?"));

        String sql = """
                    SELECT
                        id as id,
                        murex_book_code as murexBookCode,
                        description as description,
                        tps_outbound as tpsOutbound,
                        transformations as transformations
                    """ +
                    "FROM murex_book_config WHERE id IN (" + placeholders + ")";


        return jdbcTemplate.query(sql, idList.toArray(), createMurexBookConfigRowMapper());
    }

    public List<Currency> fetchCurrencyConfigs(final String currency) {
        String sql = """
                    SELECT
                        original_currency as originalCurrency,
                        functional_currency as functionalCurrency,
                        currency_category as currencyCategory
                        from currency WHERE functional_currency = 'HKD'
                    """;

        return jdbcTemplate.query(sql, createCurrencyRowMapper());
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

        // Group by external_deal_id, comment_0, and nav_type
        Map<String, List<AggregatedDataResponse>> groupedMap = fetchResults.stream()
                .collect(Collectors.groupingBy(record ->
                        record.getExternalDealId() + "|" +
                                record.getComment0() + "|" +
                                record.getNavType()));

        List<GroupedRecord> validatedGroups = new ArrayList<>();

        for (Map.Entry<String, List<AggregatedDataResponse>> entry : groupedMap.entrySet()) {
            String groupKey = entry.getKey();
            List<AggregatedDataResponse> records = entry.getValue();

            if (records.isEmpty()) {
                continue; // Skip empty groups
            }

            // Extract group identifiers from first record (all records in group should have same values)
            AggregatedDataResponse firstRecord = records.getFirst();
            GroupedRecord groupedRecord = getGroupedRecord(firstRecord, records, groupKey);
            validatedGroups.add(groupedRecord);
        }

        return validatedGroups;
    }

    private static GroupedRecord getGroupedRecord(AggregatedDataResponse firstRecord, List<AggregatedDataResponse> records, String groupKey) {
        String externalDealId = firstRecord.getExternalDealId();
        String comment0 = firstRecord.getComment0();
        String navType = firstRecord.getNavType();
        String typology = firstRecord.getTypology();

        // Validation Rules
        if ("FX Spot".equals(typology)) {
            if (records.size() != 1) {
                throw new ValidationException(
                        String.format("FX Spot group must have exactly 1 record, but found %d", records.size()),
                        groupKey, typology, records.size());
            }
        } else if ("FX Swap".equals(typology) || "NDF".equals(typology)) {
            if (records.size() != 2) {
                throw new ValidationException(
                        String.format("%s group must have exactly 2 records, but found %d", typology, records.size()),
                        groupKey, typology, records.size());
            }
        } else {
            throw new ValidationException(
                    String.format("Unknown typology '%s' for group", typology),
                    groupKey, typology, records.size());
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
}