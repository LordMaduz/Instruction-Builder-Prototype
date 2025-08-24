package com.ruchira.murex.util;

import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for generating unique trace IDs for DMC records
 * Provides multiple strategies for trace ID generation
 */
@UtilityClass
public class TraceIdGenerator {

    private final AtomicLong counter = new AtomicLong(0);
    private static final String PREFIX = "HAWK-";

    /**
     * Generate a unique trace ID using timestamp and counter
     * Format: HAWK-13477593729-4561-178873-27773
     *
     * @return Unique trace ID string with timestamp
     */
    public String generateTimestampBasedTraceId() {
        long currentTime = Instant.now().getEpochSecond();
        return PREFIX.concat(String.valueOf(currentTime)).concat(UUID.randomUUID().toString().substring(8, 23));
    }

}