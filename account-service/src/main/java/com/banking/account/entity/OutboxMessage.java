package com.banking.account.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, length = 4000) // Keeps JSON payload safe
    private String payload;

    @Column(nullable = false)
    private String status; // PENDING, PROCESSED, FAILED

    @Column(nullable = false)
    private LocalDateTime createdAt;

    // Default Constructor
    public OutboxMessage() {}

    public OutboxMessage(String topic, String payload) {
        this.topic = topic;
        this.payload = payload;
        this.status = "PENDING";
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public String getTopic() { return topic; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}