package com.banking.account.dto;

public record TransferFundDto(Long debitAccountId, Long creditAccountId, double amount) {}
