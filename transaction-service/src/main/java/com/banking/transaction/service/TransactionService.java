package com.banking.transaction.service;

import com.banking.transaction.dto.TransactionDto;
import java.util.List;

public interface TransactionService {
    void logTransaction(TransactionDto transactionDto);
    List<TransactionDto> getAccountTransactions(Long accountId);
}