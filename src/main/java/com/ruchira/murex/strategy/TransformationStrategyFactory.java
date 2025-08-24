package com.ruchira.murex.strategy;

import com.ruchira.murex.exception.TransformationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Factory for transformation strategies
 * Implements Factory pattern for dynamic strategy selection
 */
@Component
@RequiredArgsConstructor
public class TransformationStrategyFactory {

    private final List<TransformationStrategy> strategies;

    /**
     * Get appropriate transformation strategy for the given typology
     *
     * @param typology The typology to find strategy for
     * @return Transformation strategy that supports the typology
     * @throws TransformationException if no strategy found
     */
    public TransformationStrategy getStrategy(String typology) {
        Optional<TransformationStrategy> strategy = strategies.stream()
                .filter(s -> s.supports(typology))
                .findFirst();

        return strategy.orElseThrow(() ->
                new TransformationException(
                        String.format("No transformation strategy found for typology: %s", typology)
                ));
    }

    /**
     * Get all available transformation strategies
     *
     * @return List of all registered strategies
     */
    public List<TransformationStrategy> getAllStrategies() {
        return List.copyOf(strategies);
    }

    /**
     * Check if a strategy exists for the given typology
     *
     * @param typology The typology to check
     * @return true if strategy exists
     */
    public boolean hasStrategy(String typology) {
        return strategies.stream().anyMatch(s -> s.supports(typology));
    }

}