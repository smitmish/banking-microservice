package com.banking.transaction.service.consumer;

import com.banking.transaction.dto.TransactionDto;
import com.banking.transaction.dto.TransactionEventDto;
import com.banking.transaction.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class TransactionConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransactionConsumer.class);
    private final TransactionService transactionService;

    public TransactionConsumer(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @KafkaListener(topics = "banking-transactions", groupId = "banking-group", autoStartup = "false")
    public void consume(TransactionEventDto event) {
        LOGGER.info(String.format("====> Kafka Event Consumed Successfully -> Account: %s, Type: %s, Amount: %s",
                event.accountId(), event.transactionType(), event.amount()));

        // Map the event straight to our DTO schema for database logging
        TransactionDto dto = new TransactionDto(
                null,
                event.accountId(),
                event.amount(),
                event.transactionType(),
                event.timestamp()
        );

        // This persists the data right into your running Docker PostgreSQL container
        transactionService.logTransaction(dto);
    }
}