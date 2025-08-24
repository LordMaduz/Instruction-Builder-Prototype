package com.ruchira.murex.kafka.config;

import com.ruchira.murex.kafka.model.HAWKMurexBookingRecord;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SerializationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka Consumer configuration class for the data service application
 */
@Configuration
@Slf4j
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, HAWKMurexBookingRecord> concurrentKafkaListenerContainerFactory(
            ConsumerFactory<String, HAWKMurexBookingRecord> consumerFactory
    ) {
        ConcurrentKafkaListenerContainerFactory<String, HAWKMurexBookingRecord> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler());

        // Enable manual acknowledgment
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }

    public DefaultErrorHandler errorHandler() {
        // 6 retries with exponential delay: 1s, 2s, 4s, 8s, 10s, 10s
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(6);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, exception) -> {
            // Recovery callback: executed after retries are exhausted
            log.error("""
                            Kafka record dropped after retries
                            ─────────────────────────────────────────
                            Topic      : {}
                            Partition  : {}
                            Offset     : {}
                            Key        : {}
                            Payload    : {}
                            Error Type : {}
                            Error Msg  : {}
                            ─────────────────────────────────────────
                            Action     : Skipped (no DLT)
                            """,
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    record.key(),
                    record.value(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage()
            );
        }, backOff);


        // Tell the handler which exceptions should NOT be retried
        handler.addNotRetryableExceptions(
                IllegalArgumentException.class,    // validation errors, etc.
                SerializationException.class,
                ListenerExecutionFailedException.class
        );

        // Nice to have: observe each attempt for metrics/logging
        handler.setRetryListeners((record, ex, attempt) ->
                log.info("Retry {} for{} due to {}", attempt, record, ex.toString())
        );

        // If you use AckMode.MANUAL_IMMEDIATE and want the offset of "recovered" records committed:
        handler.setCommitRecovered(true);

        return handler;
    }
}