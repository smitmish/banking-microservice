package com.banking.account.dto; // (Adjust package path for transaction-service)

import java.time.LocalDateTime;

public record TransactionEventDto(
        Long accountId,
        double amount,
        String transactionType,
        LocalDateTime timestamp
) {}