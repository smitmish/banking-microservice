package com.banking.transaction.dto;

import java.time.LocalDateTime;

public record TransactionEventDto(Long accountId,
                                  double amount,
                                  String transactionType,
                                  LocalDateTime timestamp) {}
