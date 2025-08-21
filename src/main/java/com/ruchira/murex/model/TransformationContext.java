package com.ruchira.murex.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
/**
 * Encapsulates all input data required to execute a Murex booking transformation.
 *
 * <p>This context object is passed to transformation strategies to provide
 * a structured and extensible way of supplying parameters. It supports both
 * mandatory and optional fields depending on the transformation needs.</p>
 *
 * <ul>
 *   <li>{@code groupedRecord} – The primary grouped record to process</li>
 *   <li>{@code instructionEventRuleId} – Identifier for the instruction event rule</li>
 *   <li>{@code filteredMurexConfigs} – List of Murex book configurations filtered for processing</li>
 *   <li>{@code inputCurrency} – Input currency used for transformation calculations</li>
 *   <li>{@code allGroupedRecords} – (Optional) All grouped records, if the transformation
 *       requires context across multiple records</li>
 * </ul>
 */
public class TransformationContext {
    private GroupedRecord groupedRecord;
    private String instructionEventRuleId;
    private List<MurexBookingConfig> filteredMurexConfigs;
    private String inputCurrency;
    private List<String> currenciesInFamily;

    // optional
    private List<GroupedRecord> allGroupedRecords;


    public String getFlipCurrencyVariant() {
        return currenciesInFamily.stream()
                .filter(c -> c.matches(".*\\d$"))
                .findFirst().orElse(inputCurrency);
    }
}
