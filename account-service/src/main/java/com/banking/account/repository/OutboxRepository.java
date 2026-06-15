package com.banking.account.repository;

import com.banking.account.entity.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
    // Fetch records sequentially so they publish in order
    List<OutboxMessage> findByStatusOrderByCreatedAtAsc(String status);
}
