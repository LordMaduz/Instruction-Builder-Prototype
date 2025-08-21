package com.ruchira.murex.service;

import com.ruchira.murex.dto.StgMrxExtDmcDto;
import com.ruchira.murex.model.MurexTrade;
import com.ruchira.murex.model.TransformationContext;
import com.ruchira.murex.strategy.TransformationStrategyFactory;
import com.ruchira.murex.strategy.TransformationStrategy;
import com.ruchira.murex.exception.TransformationException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Refactored service for Murex booking transformation logic
 * Uses Strategy pattern for extensible transformation handling
 */
@Service
@RequiredArgsConstructor
public class MurexDataTransformationService {

    private final TransformationStrategyFactory strategyFactory;

    /**
     * Generate MurexBookingDTO objects using strategy pattern
     * 
     * @param transformationContext which include The grouped record to process, Filtered Murex configurations,
     *                              The input currency for transformation calculations, and all grouped records
     * @return List of transformed MurexBookingDTO objects
     */
    public Pair<List<StgMrxExtDmcDto>, List<MurexTrade>> generateMurexBookings(TransformationContext transformationContext) {
        
        String typology = transformationContext.getGroupedRecord().getTypology();
        
        try {
            TransformationStrategy strategy = strategyFactory.getStrategy(typology);
            return strategy.process(transformationContext);
            
        } catch (Exception e) {
            throw new TransformationException(
                "Failed to generate Murex bookings for typology: " + typology + ". " + e.getMessage(),
                typology,
                e
            );
        }
    }

    /**
     * Check if transformation is supported for the given typology
     * 
     * @param typology The typology to check
     * @return true if supported
     */
    public boolean isTransformationSupported(String typology) {
        return strategyFactory.hasStrategy(typology);
    }

    /**
     * Get all supported transformation types
     * 
     * @return List of supported transformation types
     */
    public List<String> getSupportedTransformationTypes() {
        return strategyFactory.getAllStrategies().stream()
                .map(TransformationStrategy::getTransformationType)
                .toList();
    }
}