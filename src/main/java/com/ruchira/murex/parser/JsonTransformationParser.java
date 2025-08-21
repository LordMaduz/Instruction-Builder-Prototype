package com.ruchira.murex.parser;

import com.ruchira.murex.exception.TransformationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced JSON transformation parser that handles both single and multi-element transformation arrays.
 * Background:
 * - Originally, transformation parsing assumed single transformation objects
 * - FX Spot and FX Swap use single-element arrays: [{ "referenceTrade": "FX Spot", ... }]
 * - NDF introduces multi-element arrays: [{ "referenceTrade": "FX Spot", ... }, { "referenceTrade": "NDF", ... }]
 * <p>
 * Design Goals:
 * 1. Maintain backward compatibility with existing Spot/Swap strategies
 * 2. Support multi-element transformation arrays for NDF
 * 3. Provide flexible transformation selection mechanisms
 * 4. Future-proof for cases with >2 transformation elements
 */
@Component
public class JsonTransformationParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse transformations JSON string into a list of transformation nodes.
     * Handles both legacy object format and new array format for backward compatibility.
     *
     * @param transformationsJson JSON string containing transformations
     * @return List of JsonNode objects representing individual transformations
     * @throws RuntimeException if JSON parsing fails
     */
    public List<JsonNode> parseTransformations(String transformationsJson) {
        try {
            JsonNode rootNode = objectMapper.readTree(transformationsJson);
            List<JsonNode> transformationNodes = new ArrayList<>();

            if (rootNode.isArray()) {
                // New format: array of transformation objects
                for (JsonNode transformation : rootNode) {
                    transformationNodes.add(transformation);
                }
            } else {
                // Legacy format: single transformation object (wrap in list for consistency)
                transformationNodes.add(rootNode);
            }

            return transformationNodes;

        } catch (Exception e) {
            throw new TransformationException("Failed to parse transformations JSON: {}", "Generic Transformation", e);
        }
    }


    /**
     * Get the first transformation node from the transformations array.
     * Maintains backward compatibility for Spot/Swap strategies that expect a single transformation.
     *
     * @param transformationsJson JSON string containing transformations
     * @return The first transformation node
     * @throws RuntimeException if no transformations are found
     */
    public JsonNode getFirstTransformation(String transformationsJson) {
        List<JsonNode> transformations = parseTransformations(transformationsJson);

        if (transformations.isEmpty()) {
            throw new TransformationException("No transformations found in JSON");
        }

        return transformations.getFirst();
    }

    /**
     * Legacy method - Parse referenceTrade value from transformations JSON string (original signature)
     * Maintains backward compatibility with existing code
     *
     * @param transformationsJson JSON string containing transformations
     * @return Optional containing referenceTrade value if found
     */
    public Optional<String> parseReferenceTrade(String transformationsJson) {
        if (transformationsJson == null || transformationsJson.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            JsonNode firstTransformation = getFirstTransformation(transformationsJson);
            if (firstTransformation.has("referenceTrade")) {
                return Optional.of(firstTransformation.get("referenceTrade").asText());
            }

        } catch (Exception e) {
            throw new TransformationException("Error parsing referenceTrade from JSON for: {}", "referenceTrade", e);
        }

        return Optional.empty();
    }

    /**
     * Extract transformation nodes from JSON (legacy method for backward compatibility)
     *
     * @param transformationsJson JSON string containing transformations
     * @return List of JsonNode objects representing transformations
     */
    public List<JsonNode> extractTransformationNodes(String transformationsJson) {
        return parseTransformations(transformationsJson);
    }

    /**
     * Get transformation count from JSON string.
     *
     * @param transformationsJson JSON string containing transformations
     * @return Number of transformation objects in the array
     */
    public int getTransformationCount(String transformationsJson) {
        return parseTransformations(transformationsJson).size();
    }

    /**
     * Validate transformation structure and content.
     * Ensures all transformation nodes have required fields.
     *
     * @param transformationsJson JSON string containing transformations
     * @throws RuntimeException if validation fails
     */
    public void validateTransformations(String transformationsJson) {
        List<JsonNode> transformations = parseTransformations(transformationsJson);

        if (transformations.isEmpty()) {
            throw new RuntimeException("Transformations array cannot be empty");
        }

        for (int i = 0; i < transformations.size(); i++) {
            JsonNode transformation = transformations.get(i);

            if (!transformation.has("referenceTrade")) {
                throw new RuntimeException("Transformation at index " + i + " missing 'referenceTrade' field");
            }
        }
    }

    /**
     * Validate if transformations JSON is well-formed (legacy method)
     *
     * @param transformationsJson JSON string to validate
     * @return true if JSON is valid, false otherwise
     */
    public boolean isValidJson(String transformationsJson) {
        if (transformationsJson == null || transformationsJson.trim().isEmpty()) {
            return false;
        }

        try {
            objectMapper.readTree(transformationsJson);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}