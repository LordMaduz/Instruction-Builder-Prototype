package com.ruchira.murex.kafka.producer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaPublisherHandler {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publish(final String topic, final Object payload) {

        kafkaTemplate.send(topic, payload).whenCompleteAsync((result, exception) -> {
            if (exception == null) {
                log.info("Event Published Successfully with Offset: {}", result.getRecordMetadata().offset());
                return;
            }
            log.error("Unable to Publish Message: {}", exception, exception);
        });
    }
}
