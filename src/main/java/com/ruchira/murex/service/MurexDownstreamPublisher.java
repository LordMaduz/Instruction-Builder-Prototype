package com.ruchira.murex.service;

import com.ruchira.murex.kafka.model.HAWKMurexBookingRecord;
import com.ruchira.murex.kafka.model.HAWKMurexBookingTradeLeg;
import com.ruchira.murex.kafka.model.HawkMurexBookingTradeLegAdditionalFields;
import com.ruchira.murex.kafka.model.HawkMurexBookingTradeLegComponent;
import com.ruchira.murex.kafka.producer.KafkaPublisherHandler;
import com.ruchira.murex.mapper.HawkMurexBookingMapper;
import com.ruchira.murex.model.trade.MurexTrade;
import com.ruchira.murex.model.trade.MurexTradeLeg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MurexDownstreamPublisher {

    private final KafkaPublisherHandler publisherHandler;
    private final HawkMurexBookingMapper murexBookingMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishMurexTradesToDownStream(MurexTrade murexTrade) {

        try {
            log.debug("Starting the process to publish the trade ID: {}", murexTrade.getTradeReference());

            // Map main booking record
            HAWKMurexBookingRecord murexBookingRecord = murexBookingMapper.toHawkMurexBooking(murexTrade);
            log.debug("Mapped HAWK booking record for trade reference {}: {}", murexTrade.getTradeReference(), murexBookingRecord);

            // Process near leg if present
            if (ObjectUtils.isNotEmpty(murexTrade.getNearLeg())) {
                log.debug("Processing near leg for trade reference: {}", murexTrade.getTradeReference());
                HAWKMurexBookingTradeLeg nearLeg = mapTradeLeg(murexTrade.getNearLeg());
                murexBookingRecord.setNearLeg(nearLeg);
            } else {
                log.debug("No near leg present for trade reference: {}", murexTrade.getTradeReference());
            }

            // Process far leg if present
            if (ObjectUtils.isNotEmpty(murexTrade.getFarLeg())) {
                log.debug("Processing far leg for trade reference: {}", murexTrade.getTradeReference());
                HAWKMurexBookingTradeLeg farLeg = mapTradeLeg(murexTrade.getFarLeg());
                murexBookingRecord.setFarLeg(farLeg);
            } else {
                log.debug("No far leg present for trade reference: {}", murexTrade.getTradeReference());
            }

            // Publish the trade
            log.info("Publishing HAWK Murex trade to topic 'murex-topic', trade reference: {}", murexTrade.getTradeReference());
            publisherHandler.publish("murex-topic", murexBookingRecord);

        } catch (Exception ex) {
            log.error("Failed to publish trade reference: {} due to error: {}", murexTrade.getTradeReference(), ex.getMessage(), ex);
        }
    }

    /**
     * Helper method to map a trade leg with components and additional fields
     */
    private HAWKMurexBookingTradeLeg mapTradeLeg(MurexTradeLeg tradeLeg) {
        HAWKMurexBookingTradeLeg leg = murexBookingMapper.toHawkMurexTradeLeg(tradeLeg);

        if (CollectionUtils.isNotEmpty(tradeLeg.getComponents())) {
            List<HawkMurexBookingTradeLegComponent> components = murexBookingMapper.toHawkMurexTradeLegComponents(tradeLeg.getComponents());
            leg.setComponents(components);
        }

        if (ObjectUtils.isNotEmpty(tradeLeg.getAdditionalFields())) {
            HawkMurexBookingTradeLegAdditionalFields additionalFields = murexBookingMapper.toHawkMurexTradeLegAdditionalFields(tradeLeg.getAdditionalFields());
            leg.setAdditionalFields(additionalFields);
        }

        return leg;
    }
}
