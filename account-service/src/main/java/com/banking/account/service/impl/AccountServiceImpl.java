package com.banking.account.service.impl;

import com.banking.account.dto.AccountDto;
import com.banking.account.dto.TransactionEventDto;
import com.banking.account.dto.TransferFundDto;
import com.banking.account.entity.Account;
import com.banking.account.entity.OutboxMessage;
import com.banking.account.exception.AccountException;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.account.repository.OutboxRepository;
import com.banking.account.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper; // For converting DTOs to JSON strings
    private static final String TOPIC = "banking-transactions";

    public AccountServiceImpl(AccountRepository accountRepository,
                              OutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.accountRepository = accountRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AccountDto createAccount(AccountDto accountDto) {
        Account account = AccountMapper.maptoAccount(accountDto);
        Account savedAccount = accountRepository.save(account);
        return AccountMapper.maptoAccountDto(savedAccount);
    }

    @Override
    public AccountDto getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));
        return AccountMapper.maptoAccountDto(account);
    }

    @Override
    public AccountDto deposit(Long id, double amount) {
        //1.fetch the entity from database.
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));
        //invoke the encapsulated business rule method.
        account.deposit(amount);
        //Save the updated valid state back to your database.
        account.setBalance(account.getBalance() + amount);
        //Account savedAccount = accountRepository.save(account);

        // 2. Stage the event messages directly into the outbox entity instead of calling Kafka
        try {
            // Prepare the event
            TransactionEventDto event = new TransactionEventDto(id, amount, "DEBIT",
                    LocalDateTime.now());

            // Convert payloads to JSON strings
            String creditJson = objectMapper.writeValueAsString(event);

            // Save records directly into the same transaction boundary
            outboxRepository.save(new OutboxMessage(TOPIC, creditJson));

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox transaction payloads", e);
        }


        return AccountMapper.maptoAccountDto(account);
    }

    @Override
    public AccountDto withdrawal(Long id, double amount) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));

        if (account.getBalance() < amount) {
            throw new AccountException("Insufficient amount.");
        }

        // 1. Mutate balances locally inside Oracle
        account.setBalance(account.getBalance() - amount);
        //Handle by Spring JPA transactional process as any changes made by
        //setters was saved so commenting below line.
        //Account savedAccount = accountRepository.save(account);

        // 2. Stage the event messages directly into the outbox entity instead of calling Kafka
        try {
            // Prepare the event
            TransactionEventDto event = new TransactionEventDto(id, amount, "DEBIT",
                    LocalDateTime.now());

            // Convert payloads to JSON strings
            String debitJson = objectMapper.writeValueAsString(event);

            // Save records directly into the same transaction boundary
            outboxRepository.save(new OutboxMessage(TOPIC, debitJson));

        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox transaction payloads", e);
        }

        return AccountMapper.maptoAccountDto(account);
    }

    @Override
    public List<AccountDto> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(AccountMapper::maptoAccountDto)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAccount(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));
        accountRepository.delete(account);
    }

    @Override
    @Transactional // Ensures atomicity over BOTH accounts and the outbox write
    public void transferFunds(TransferFundDto transferFundDto) {
        Account debitAccount = accountRepository.findById(transferFundDto.debitAccountId())
                .orElseThrow(() -> new AccountException("Debit account not found: " + transferFundDto.debitAccountId()));

        Account creditAccount = accountRepository.findById(transferFundDto.creditAccountId())
                .orElseThrow(() -> new AccountException("Credit account not found: " + transferFundDto.creditAccountId()));

        if (debitAccount.getBalance() < transferFundDto.amount()) {
            throw new AccountException("Insufficient Balance in Debit Account.");
        }

        // 1. Mutate balances locally inside Oracle
        debitAccount.setBalance(debitAccount.getBalance() - transferFundDto.amount());
        creditAccount.setBalance(creditAccount.getBalance() + transferFundDto.amount());
//        In Spring Data JPA, because your entities are
//        managed within a transaction, any changes you
//        make via setters are automatically written to
//        the database when the method ends. Calling
//                .save(debitAccount) explicitly is redundant
//        here and can sometimes confuse Hibernate's execution plan,
//        causing it to prematurely check the database state with a
//        SELECT query to decide whether it should perform an INSERT or an UPDATE.
//        accountRepository.save(debitAccount);
//        accountRepository.save(creditAccount);

        // 2. Stage the event messages directly into the outbox entity instead of calling Kafka
        try {
            // Prepare the event
            TransactionEventDto debitEvent = new TransactionEventDto(
                    transferFundDto.debitAccountId(), transferFundDto.amount(), "DEBIT", LocalDateTime.now());
            TransactionEventDto creditEvent = new TransactionEventDto(
                    transferFundDto.creditAccountId(), transferFundDto.amount(), "CREDIT", LocalDateTime.now());

            // Convert payloads to JSON strings
            String debitJson = objectMapper.writeValueAsString(debitEvent);
            String creditJson = objectMapper.writeValueAsString(creditEvent);

            // Save records directly into the same transaction boundary
            outboxRepository.save(new OutboxMessage(TOPIC, debitJson));
            outboxRepository.save(new OutboxMessage(TOPIC, creditJson));

        } catch (Exception e) {
            throw new AccountException("Failed to serialize outbox transaction payloads" + e);
        }
    }
}