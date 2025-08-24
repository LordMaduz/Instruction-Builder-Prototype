package com.ruchira.murex.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

@UtilityClass
@Slf4j
public class ConcurrencyUtil {

    /**
     * Processes a collection of items in parallel using structured concurrency.
     * <p>
     * All-or-none semantics:
     * - If any task throws an exception, all remaining tasks are cancelled.
     * - Detailed logs include which item failed.
     *
     * @param records List of records (e.g. List<T>)
     * @param task    the processing logic for each item
     * @param <T>     Type of record
     * @throws Exception If any processing task fails
     */
    public static <T> void processAllOrNone(List<T> records,
                                            RecordTask<T> task) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {

            for (var record : records) {
                scope.fork(() -> {
                    try {
                        task.process(record);
                    } catch (Exception e) {
                        log.error("Processing failed for item: {}", record, e);
                        throw e;
                    }
                    return null;
                });
            }
            scope.join();
            scope.throwIfFailed();
        } catch (Exception ex) {
            log.error("One or more tasks failed in processAllOrNone: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Functional interface for tasks that throw checked exceptions.
     */
    @FunctionalInterface
    public interface RecordTask<T> {
        void process(T item) throws Exception;
    }
}
