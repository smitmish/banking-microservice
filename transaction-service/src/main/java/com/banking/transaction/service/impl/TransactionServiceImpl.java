package com.banking.transaction.service.impl;

import com.banking.transaction.dto.TransactionDto;
import com.banking.transaction.entity.Transaction;
import com.banking.transaction.repository.TransactionRepository;
import com.banking.transaction.service.TransactionService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Override
    public void logTransaction(TransactionDto dto) {
        Transaction tx = new Transaction();
        tx.setAccountId(dto.accountId());
        tx.setAmount(dto.amount());
        tx.setTransactionType(dto.transactionType());
        tx.setTimestamp(dto.timestamp());
        transactionRepository.save(tx);
    }

    @Override
    public List<TransactionDto> getAccountTransactions(Long accountId) {
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId)
                .stream()
                .map(t -> new TransactionDto(t.getId(), t.getAccountId(), t.getAmount(), t.getTransactionType(), t.getTimestamp()))
                .collect(Collectors.toList());
    }
}