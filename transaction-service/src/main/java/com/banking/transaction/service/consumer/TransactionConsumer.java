package com.banking.transaction.service.consumer;

import com.banking.transaction.dto.TransactionEventDto;
import com.banking.transaction.dto.TransactionDto;
import com.banking.transaction.service.TransactionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionConsumer.class);
    private final TransactionService transactionService;
    private final ObjectMapper objectMapper;

    public TransactionConsumer(TransactionService transactionService) {
        this.transactionService = transactionService;
        // Instantiating a clean ObjectMapper equipped to parse Java 8 LocalDateTime arrays
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    // TARGET 1: Handles perfectly parsed clean JSON entities
    @KafkaListener(
            topics = "banking-transactions",
            groupId = "banking-group",
            autoStartup = "true",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(TransactionEventDto event) {
        processTransaction(event);
    }

    // TARGET 2: Fallback listener execution signature handling plain JSON literal strings
    @KafkaListener(
            topics = "banking-transactions",
            groupId = "banking-group",
            autoStartup = "true",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeRawString(String rawJsonMessage) {
        try {
            LOGGER.info("Intercepted raw string literal payload from outbox. Processing manual mapping...");

            // If the string contains outer escape quotes, clean them up before mapping
            if (rawJsonMessage.startsWith("\"") && rawJsonMessage.endsWith("\"")) {
                rawJsonMessage = rawJsonMessage.substring(1, rawJsonMessage.length() - 1).replace("\\\"", "\"");
            }

            TransactionEventDto mappedEvent = objectMapper.readValue(rawJsonMessage, TransactionEventDto.class);
            processTransaction(mappedEvent);

        } catch (Exception e) {
            LOGGER.error("Failed manual serialization backup mapping: {}", e.getMessage());
            // This safely handles the message error without crashing your service instance
            throw new RuntimeException("Routing bad string message to DLT queue.", e);
        }
    }

    private void processTransaction(TransactionEventDto event) {
        try {
            LOGGER.info("====> Kafka Event Consumed Successfully -> Account: {}, Type: {}, Amount: {}",
                    event.accountId(), event.transactionType(), event.amount());

            TransactionDto dto = new TransactionDto(
                    null,
                    event.accountId(),
                    event.amount(),
                    event.transactionType(),
                    event.timestamp()
            );

            transactionService.logTransaction(dto);

        } catch (Exception e) {
            LOGGER.error("Failed to insert record into PostgreSQL table: {}", e.getMessage());
        }
    }
}