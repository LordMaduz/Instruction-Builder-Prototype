package com.ruchira.murex.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    public static <T, R> List<R> processAllOrNone(List<T> records,
                                                  RecordTask<T, R> task) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<R>> futures = new ArrayList<>();

            for (var record : records) {
                futures.add(scope.fork(() -> {
                    try {
                        return task.process(record);
                    } catch (Exception e) {
                        log.error("Processing failed for item: {}", record, e);
                        throw e;
                    }
                }));
            }
            scope.join();
            scope.throwIfFailed();

            // collect all results (non-null)
            return futures.stream()
                    .map(StructuredTaskScope.Subtask::get)
                    .filter(Objects::nonNull)
                    .toList();

        } catch (Exception ex) {
            log.error("One or more tasks failed in processAllOrNone: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Functional interface for tasks that throw checked exceptions.
     */
    @FunctionalInterface
    public interface RecordTask<T, R> {
        R process(T item) throws Exception;
    }
}
