package com.banking.account;

import com.banking.account.dto.TransferFundDto;
import com.banking.account.entity.Account;
import com.banking.account.exception.AccountException;
import com.banking.account.repository.AccountRepository;
import com.banking.account.repository.OutboxRepository;
import com.banking.account.service.impl.AccountServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceApplicationTests {
	@Mock
	private AccountRepository accountRepository;

	@Mock
	private OutboxRepository outboxRepository;

	@Spy
	private ObjectMapper objectMapper = new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

	@InjectMocks
	private AccountServiceImpl accountService;

	private Account sourceAccount;
	private Account targetAccount;

	@BeforeEach
	void setUp() {
		// Prepare clean in-memory account objects before each test runs
		sourceAccount = new Account();
		sourceAccount.setId(12345L);
		sourceAccount.setAccountHolderName("Smit");
		sourceAccount.deposit(1000.0); // Encapsulated baseline balance

		targetAccount = new Account();
		targetAccount.setId(67890L);
		targetAccount.setAccountHolderName("Ranu");
		targetAccount.deposit(500.0);
	}

	@Test
	void testTransfer_SufficientFunds_ShouldSucceedAndLogToOutbox() {
		// 1. Arrange: Tell Mockito how to behave when the service calls the database repositories
		TransferFundDto transferDto = new TransferFundDto(12345L, 67890L, 200.0);
		when(accountRepository.findById(12345L)).thenReturn(Optional.of(sourceAccount));
		when(accountRepository.findById(67890L)).thenReturn(Optional.of(targetAccount));

		// 2. Act: Trigger the method under test
		assertDoesNotThrow(() -> accountService.transferFunds(transferDto));

		// 3. Assert: Verify the encapsulated arithmetic updated states correctly
		assertEquals(800.0, sourceAccount.getBalance());  // 1000 - 200
		assertEquals(700.0, targetAccount.getBalance());  // 500 + 200

		// Confirm that the data layers and outbox entries are committed
		verify(accountRepository, times(1)).findById(12345L);
		verify(accountRepository, times(1)).findById(67890L);
		verify(outboxRepository, times(2)).save(any());
	}

	@Test
	void testTransfer_InsufficientFunds_ShouldThrowExceptionAndPreventOutboxLog() {
		// 1. Arrange: Attempt to move more money than the source account holds (5000.0 vs 1000.0)
		TransferFundDto transferDto = new TransferFundDto(12345L, 67890L, 5000.0);
		when(accountRepository.findById(12345L)).thenReturn(Optional.of(sourceAccount));

		// 2. Act & Assert: Verify the transaction system halts immediately with an exception
		assertThrows(AccountException.class, () -> accountService.transferFunds(transferDto));

		// Ensure balances were untouched by the failure
		assertEquals(1000.0, sourceAccount.getBalance());

		// CRITICAL: Verify that NO database saves or outbox updates occurred
		verify(accountRepository, never()).save(any());
		verify(outboxRepository, never()).save(any());
	}
}
