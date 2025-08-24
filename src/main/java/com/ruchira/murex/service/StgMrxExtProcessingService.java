package com.ruchira.murex.service;


import com.ruchira.murex.mapper.DynamicMapper;
import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.model.TransformedMurexTrade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Dedicated service for DMC (Data Management Center) record processing
 * Handles the complete Stage of DMC generation workflow as a separate concern
 */

@Service
@RequiredArgsConstructor
public class StgMrxExtProcessingService {


    private final DynamicMapper dynamicMapper;

    /**
     * Generate DMC records from processing results
     * Enhanced for Stage 3 Phase 3 with advanced booking transformation logic
     *
     * @param transformedMurexTrades The murex Trade legs to process
     * @param instructionEventRuleId Murex book code from Instruction Event configurations
     * @param murexBookCode Murex book code from murex book configurations
     * @return List of generated DMC records with enhanced metadata
     */
    @Transactional
    public List<StgMrxExtDmcDto> generateDmcRecords(final List<TransformedMurexTrade> transformedMurexTrades,
                                                    final String murexBookCode,
                                                    final String instructionEventRuleId,
                                                    final String traceId

    ) {

        final List<StgMrxExtDmcDto> stgMrxExtDmcs = new ArrayList<>();

        for (TransformedMurexTrade tradeLeg : transformedMurexTrades) {

            // Create DMC record from original fetch result
            StgMrxExtDmcDto stgMrxExtDmcDto = dynamicMapper.mapToDmcDto(tradeLeg);

            stgMrxExtDmcDto.setTraceId(traceId);

            // Set rule ID from business event configuration
            stgMrxExtDmcDto.setInstructionRuleId(instructionEventRuleId);

            // Set booking code from Murex configuration
            stgMrxExtDmcDto.setMurexBookCode(murexBookCode);

            stgMrxExtDmcs.add(stgMrxExtDmcDto);
        }
        return stgMrxExtDmcs;
    }
}