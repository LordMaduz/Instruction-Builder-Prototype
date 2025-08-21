package com.ruchira.murex.util;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Utility class for generating unique trace IDs for DMC records
 * Provides multiple strategies for trace ID generation
 */
@Component
public class TraceIdGenerator {

    private static final String PREFIX = "DMC";

    /**
     * Generate a unique trace ID using UUID
     * Format: DMC-{UUID}
     *
     * @return Unique trace ID string
     */
    public String generateUniqueTraceId() {
        return PREFIX + "-" + UUID.randomUUID();
    }

    /**
     * Generate a unique trace ID using timestamp and counter
     * Format: DMC-13477593729-4561-178873-27773-3yyy3
     *
     * @return Unique trace ID string with timestamp
     */
    public String generateTimestampBasedTraceId() {
        long currentTime = Instant.now().getEpochSecond();
        return PREFIX + "-" + currentTime + "-" + UUID.randomUUID();
    }
}