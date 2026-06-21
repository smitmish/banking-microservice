package com.banking.account.dto;

public record AccountDto (Long id, String accountHolderName, double balance, String accountType) {}
