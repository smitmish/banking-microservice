package com.banking.account.scheduler;

import com.banking.account.entity.OutboxMessage;
import com.banking.account.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class OutboxMessageRelayer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxMessageRelayer.class);
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxMessageRelayer(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000) // Checks your outbox table every 3 seconds
    public void relayPendingMessages() {
        List<OutboxMessage> pendingList = outboxRepository.findByStatusOrderByCreatedAtAsc("PENDING");

        if (pendingList.isEmpty()) {
            return;
        }

        LOGGER.info("Found {} pending outbox messages to dispatch to Kafka...", pendingList.size());

        for (OutboxMessage message : pendingList) {
            try {
                //1. Synchronously push to Kafka broker
                kafkaTemplate.send(message.getTopic(), message.getPayload()).get();
                LOGGER.info("Successfully dispatched outbox message ID: {} to Kafka", message.getId());
                // 2. FORCE a separate write transaction to commit right now
                updateMessageStatus(message.getId(), "PROCESSED");


            } catch (Exception e) {
                LOGGER.error("Kafka broker is unreachable. Retrying outbox dispatch next cycle: {}", e.getMessage());
                break; // Break the loop so order preservation stays locked until Kafka recovers
            }
        }
    }

    // REQUIRES_NEW forces Spring to suspend the main execution thread,
    // open a fresh database transaction connection, execute the update, and COMMIT it instantly.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateMessageStatus(Long id, String status) {
        outboxRepository.findById(id).ifPresent(msg -> {
            msg.setStatus(status);
            // Force an immediate save and database synchronization flush
            outboxRepository.saveAndFlush(msg);
        });
    }
}