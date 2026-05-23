package com.banking.account.service.impl;

import com.banking.account.dto.AccountDto;
import com.banking.account.dto.TransactionEventDto;
import com.banking.account.dto.TransferFundDto;
import com.banking.account.entity.Account;
import com.banking.account.exception.AccountException;
import com.banking.account.mapper.AccountMapper;
import com.banking.account.repository.AccountRepository;
import com.banking.account.service.AccountService;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final KafkaTemplate<String, TransactionEventDto> kafkaTemplate;
    private static final String TOPIC = "banking-transactions";

    public AccountServiceImpl(AccountRepository accountRepository, KafkaTemplate<String, TransactionEventDto> kafkaTemplate) {
        this.accountRepository = accountRepository;
        this.kafkaTemplate = kafkaTemplate;
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
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));

        account.setBalance(account.getBalance() + amount);
        Account savedAccount = accountRepository.save(account);

        // Prepare the event
        TransactionEventDto event = new TransactionEventDto(id, amount, "DEPOSIT", LocalDateTime.now());
        // Asynchronously dispatch to Kafka
        kafkaTemplate.send(TOPIC, event).whenComplete((result, exception) -> {
            if (exception != null) {
                System.err.printf("ALERT: Ledger log failed for Account %d. Error: %s%n", id, exception.getMessage());
            } else {
                System.out.printf("Ledger logged successfully to partition %d%n", result.getRecordMetadata().partition());
            }
        });
        // Return success to the customer instantly

        return AccountMapper.maptoAccountDto(savedAccount);
    }

    @Override
    public AccountDto withdrawal(Long id, double amount) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountException("Account does not exist: " + id));

        if (account.getBalance() < amount) {
            throw new AccountException("Insufficient amount.");
        }

        account.setBalance(account.getBalance() - amount);
        Account savedAccount = accountRepository.save(account);

        // Prepare the event
        TransactionEventDto event = new TransactionEventDto(id, amount, "WITHDRAW", LocalDateTime.now());
        // Asynchronously dispatch to Kafka
        kafkaTemplate.send(TOPIC, event).whenComplete((result, exception) -> {
            if (exception != null) {
                System.err.printf("ALERT: Ledger log failed for Account %d. Error: %s%n", id, exception.getMessage());
            } else {
                System.out.printf("Ledger logged successfully to partition %d%n", result.getRecordMetadata().partition());
            }
        });
        // Return success to the customer instantly

        return AccountMapper.maptoAccountDto(savedAccount);
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
    public void transferFunds(TransferFundDto transferFundDto) {
        Account debitAccount = accountRepository.findById(transferFundDto.debitAccountId())
                .orElseThrow(() -> new AccountException("Debit account does not exist: " + transferFundDto.debitAccountId()));

        Account creditAccount = accountRepository.findById(transferFundDto.creditAccountId())
                .orElseThrow(() -> new AccountException("Credit account does not exist: " + transferFundDto.creditAccountId()));

        if (debitAccount.getBalance() < transferFundDto.amount()) {
            throw new AccountException("Insufficient amount.");
        }

        debitAccount.setBalance(debitAccount.getBalance() - transferFundDto.amount());
        creditAccount.setBalance(creditAccount.getBalance() + transferFundDto.amount());

        accountRepository.save(debitAccount);
        accountRepository.save(creditAccount);

       // Prepare the event
        TransactionEventDto inEvent = new TransactionEventDto(transferFundDto.debitAccountId(),
                transferFundDto.amount(), "TRANSFER_OUT", LocalDateTime.now());
        TransactionEventDto outEvent = new TransactionEventDto(transferFundDto.creditAccountId(),
                transferFundDto.amount(), "TRANSFER_IN", LocalDateTime.now());
        // Asynchronously dispatch to Kafka
        kafkaTemplate.send(TOPIC, inEvent).whenComplete((result, exception) -> {
            if (exception != null) {
                System.err.printf("ALERT: Ledger log failed for Account %d. Error: %s%n", transferFundDto.debitAccountId(), exception.getMessage());
            } else {
                System.out.printf("Ledger logged successfully to partition %d%n", result.getRecordMetadata().partition());
            }
        });
        kafkaTemplate.send(TOPIC, outEvent).whenComplete((result, exception) -> {
            if (exception != null) {
                System.err.printf("ALERT: Ledger log failed for Account %d. Error: %s%n", transferFundDto.debitAccountId(), exception.getMessage());
            } else {
                System.out.printf("Ledger logged successfully to partition %d%n", result.getRecordMetadata().partition());
            }
        });
    }
}