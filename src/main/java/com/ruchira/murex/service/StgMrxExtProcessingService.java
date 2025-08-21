package com.ruchira.murex.service;


import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.model.MurexTradeLeg;
import com.ruchira.murex.util.CopyUtils;
import com.ruchira.murex.util.TraceIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Dedicated service for DMC (Data Management Center) record processing
 * Handles the complete Stage 3 DMC generation workflow as a separate concern
 */

@Service
@RequiredArgsConstructor
public class StgMrxExtProcessingService {


    private final TraceIdGenerator traceIdGenerator;

    /**
     * Generate DMC records from processing results
     * Enhanced for Stage 3 Phase 3 with advanced booking transformation logic
     *
     * @param murexTradeLegs The murex Trade legs to process
     * @param instructionEventRuleId Murex book code from Instruction Event configurations
     * @param murexBookCode Murex book code from murex book configurations
     * @return List of generated DMC records with enhanced metadata
     */
    public List<StgMrxExtDmcDto> generateDmcRecords(final List<MurexTradeLeg> murexTradeLegs,
                                                    final String murexBookCode,
                                                    final String instructionEventRuleId

    ) {

        final List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        // Generate unique trace ID for tracking
        final String traceId = traceIdGenerator.generateUniqueTraceId();

        for (MurexTradeLeg tradeLeg : murexTradeLegs) {

            // Create DMC record from original fetch result
            StgMrxExtDmcDto stgMrxExtDmcDto = CopyUtils.clone(tradeLeg, StgMrxExtDmcDto.class);

            stgMrxExtDmcDto.setTraceId(traceId);

            // Set rule ID from business event configuration
            stgMrxExtDmcDto.setInstructionConfigRuleId(instructionEventRuleId);

            // Set booking code from Murex configuration
            stgMrxExtDmcDto.setMurexBookingCode(murexBookCode);

            stgMrxExtDmcs.add(stgMrxExtDmcDto);
        }
        return stgMrxExtDmcs;
    }
}