package com.banking.transaction.repository;

import com.banking.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Fetches the transaction log history sorted with the newest updates first
    List<Transaction> findByAccountIdOrderByTimestampDesc(Long accountId);
}