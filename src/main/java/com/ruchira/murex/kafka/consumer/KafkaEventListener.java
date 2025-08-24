package com.ruchira.murex.kafka.consumer;

import com.ruchira.murex.kafka.model.HAWKMurexBookingRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventListener {


    @KafkaListener(id = "murex-group-id", topics = "murex-topic", containerFactory = "concurrentKafkaListenerContainerFactory")
    public void onTradeResponse(ConsumerRecord<String, HAWKMurexBookingRecord> record,
                                     Acknowledgment acknowledgment) {
        final HAWKMurexBookingRecord response = record.value();
        log.info("MurexTradeOutBound Processed Event Received: {}", response);

        acknowledgment.acknowledge();
    }
}
