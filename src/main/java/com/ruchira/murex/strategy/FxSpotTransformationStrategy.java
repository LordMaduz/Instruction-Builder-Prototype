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
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.ruchira.murex.constant.Constants.*;

/**
 * Transformation strategy for FX Spot typology
 * Implements complex business logic for FX Spot transformations with dynamic field mapping
 */
@Component
public class FxSpotTransformationStrategy extends TransformationStrategy {


    public FxSpotTransformationStrategy(
            final MurexTradeRecordMapper murexTradeRecordMapper,
            final DynamicMapper dynamicMapper,
            final DynamicFieldParser fieldMapper,
            final JsonParser jsonParser,
            final TransformationFieldConfig transformationFieldConfig,
            final StgMrxExtProcessingService stgMrxExtProcessingService

    ) {
        super(murexTradeRecordMapper, dynamicMapper, fieldMapper, jsonParser, transformationFieldConfig, stgMrxExtProcessingService);
    }

    @Override
    public boolean supports(String typology) {
        return FX_SPOT_TYPOLOGY.equals(typology);
    }

    @Override
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(final TransformationContext transformationContext) {

        final GroupedRecord groupedRecord = transformationContext.getGroupedRecord();
        validateRecordCount(groupedRecord);

        List<MurexTrade> murexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        AggregatedDataResponse record = groupedRecord.getRecords().getFirst();

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {

            // Generate unique trace ID for tracking
            final String traceId = TraceIdGenerator.generateTimestampBasedTraceId();
            try {
                final List<TransformedMurexTrade> tradeLegInputForDMC = mapAggregatedDataRecordsToTradeLegs(groupedRecord);

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId(), traceId);

                final TransformedMurexTrade tradeLeg = createBaseBooking(record, config, traceId);
                validateSpotTransformations(config);
                applyTransformations(tradeLeg, config, transformationContext);
                applyOutputCustomizations(tradeLeg, config);

                TransformedMurexTrade outPutLeg = applyTPSFieldTransformations(tradeLeg, config);

                final MurexTrade murexTrade = buildMurexTrade(outPutLeg);

                murexTrades.add(murexTrade);
            } catch (Exception e) {
                throw new TransformationException(
                        String.format("Failed to transform record for config %s : %s", config.getId(), e.getMessage()),
                        getTransformationType(),
                        e
                );
            }
        }

        return Pair.of(stgMrxExtDmcs, murexTrades);
    }

    private MurexTrade buildMurexTrade(TransformedMurexTrade trade) {
        MurexTrade murexTrade = murexTradeRecordMapper.toMurexTrade(trade);

        MurexTradeLeg leg = murexTradeRecordMapper.toMurexTradeLeg(trade);
        MurexTradeLegComponent component = murexTradeRecordMapper.toMurexTradeLegComponent(trade);
        MurexTradeLegAdditionalFields additionalFields = murexTradeRecordMapper.toMurexTradeLegAdditionalFields(trade);

        leg.setComponents(List.of(component));
        leg.setAdditionalFields(additionalFields);
        murexTrade.setNearLeg(leg);

        return murexTrade;
    }

    private List<TransformedMurexTrade> mapAggregatedDataRecordsToTradeLegs(GroupedRecord groupedRecord) {
        return groupedRecord.getRecords()
                .stream()
                .map(dynamicMapper::mapToMurexTradeLeg)
                .toList();
    }

    private void validateRecordCount(GroupedRecord groupedRecord) {
        int size = groupedRecord.getRecords().size();
        if (size != 1) {
            throw new TransformationException(
                    String.format("FX Spot must have exactly 1 record, found: %s", size),
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

    private void validateSpotTransformations(MurexBookingConfig config) {

        // Validate FX Swap has exactly one transformation
        int transformationCount = jsonParser.getTransformationCount(config.getTransformations());
        if (transformationCount != 1) {
            throw new TransformationException(
                    String.format("FX Spot transformations must have exactly 1 element, found: %s", transformationCount),
                    getTransformationType()
            );
        }
    }

    @Override
    public String getTransformationType() {
        return FX_SPOT_TYPOLOGY;
    }

    private TransformedMurexTrade createBaseBooking(AggregatedDataResponse record, MurexBookingConfig config, final String traceId) {
        return dynamicMapper.mapToMurexTradeLeg(record, Map.of(FIELD_MUREX_BOOK_CODE, config.getMurexBookCode(), FIELD_TRACE_ID, traceId));
    }

    private void applyTransformations(TransformedMurexTrade booking, MurexBookingConfig config, TransformationContext transformationContext) {
        try {
            JsonNode transformationsNode = objectMapper.readTree(config.getTransformations());

            if (transformationsNode.isArray()) {
                for (JsonNode transformation : transformationsNode) {
                    applyIndividualTransformation(booking, transformation, transformationContext);
                }
            } else {
                applyIndividualTransformation(booking, transformationsNode, transformationContext);
            }

        } catch (Exception e) {
            throw new TransformationException("Error parsing transformations JSON", getTransformationType(), e);
        }
    }

}