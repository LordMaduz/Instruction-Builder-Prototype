package com.ruchira.murex.strategy;

import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.exception.TransformationException;
import com.ruchira.murex.model.*;
import com.ruchira.murex.service.StgMrxExtProcessingService;
import com.ruchira.murex.util.CopyUtils;
import com.ruchira.murex.parser.DynamicFieldParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruchira.murex.parser.JsonTransformationParser;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Transformation strategy for FX Spot typology
 * Implements complex business logic for FX Spot transformations with dynamic field mapping
 */
@Component
public class FxSpotTransformationStrategy extends TransformationStrategy {


    private static final String FX_SPOT_TYPOLOGY = "FX Spot";

    public FxSpotTransformationStrategy(final DynamicFieldParser fieldMapper,
                                        final JsonTransformationParser jsonTransformationParser,
                                        final StgMrxExtProcessingService stgMrxExtProcessingService
    ) {
        super(fieldMapper, jsonTransformationParser, stgMrxExtProcessingService);
    }

    @Override
    public boolean supports(String typology) {
        return FX_SPOT_TYPOLOGY.equals(typology);
    }

    @Override
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> process(final TransformationContext transformationContext) {

        final GroupedRecord groupedRecord = transformationContext.getGroupedRecord();
        if (groupedRecord.getRecords().size() != 1) {
            throw new TransformationException(
                    "FX Spot must have exactly 1 record, found: " + groupedRecord.getRecords().size(),
                    getTransformationType()
            );
        }

        List<MurexTrade> murexTrades = new ArrayList<>();
        List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        AggregatedDataResponse record = groupedRecord.getRecords().getFirst();

        for (MurexBookingConfig config : transformationContext.getFilteredMurexConfigs()) {
            try {

                final List<MurexTradeLeg> tradeLegInputForDMC = groupedRecord.getRecords()
                        .stream()
                        .map(r -> CopyUtils.clone(r, MurexTradeLeg.class))
                        .toList();

                generaStgMurexExtDmcRecords(stgMrxExtDmcs, tradeLegInputForDMC, config.getMurexBookCode(), transformationContext.getInstructionEventRuleId());

                final MurexTradeLeg tradeLeg = createBaseBooking(record, config);
                validateSpotTransformations(config);
                applyTransformations(tradeLeg, config, transformationContext);
                applyOutputCustomizations(tradeLeg, config);

                MurexTradeLeg outPutLeg = applyTPSFieldTransformations(tradeLeg, config);
                final MurexTrade murexTrade = MurexTrade.builder()
                        .murexTradeLegs(List.of(outPutLeg))
                        .build();

                murexTrades.add(murexTrade);
            } catch (Exception e) {
                throw new TransformationException(
                        "Failed to transform record for config " + config.getId() + ": " + e.getMessage(),
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

    private void validateSpotTransformations(MurexBookingConfig config) {
        // Use new parser for backward compatibility and consistency
        JsonNode transformationNode = jsonTransformationParser.getFirstTransformation(config.getTransformations());

        // Validate FX Swap has exactly one transformation
        int transformationCount = jsonTransformationParser.getTransformationCount(config.getTransformations());
        if (transformationCount != 1) {
            throw new TransformationException(
                    "FX Spot transformations must have exactly 1 element, found: " + transformationCount,
                    getTransformationType()
            );
        }
    }

    @Override
    public String getTransformationType() {
        return FX_SPOT_TYPOLOGY;
    }

    private MurexTradeLeg createBaseBooking(AggregatedDataResponse record, MurexBookingConfig config) {
        return CopyUtils.clone(record, MurexTradeLeg.class, Map.of("murexBookCode", config.getMurexBookCode()));
    }

    private void applyTransformations(MurexTradeLeg booking, MurexBookingConfig config, TransformationContext transformationContext) {
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