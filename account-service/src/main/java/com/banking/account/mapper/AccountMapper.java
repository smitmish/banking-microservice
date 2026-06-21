package com.banking.account.mapper;

import com.banking.account.dto.AccountDto;
import com.banking.account.entity.Account;
import com.banking.account.entity.BusinessAccount;
import com.banking.account.entity.SavingsAccount;
import com.banking.account.exception.AccountException;

public class AccountMapper {
    public static Account maptoAccount(AccountDto accountDto) {
        if (accountDto == null) {
            return null;
        }

        // 🌟 Use a Java switch expression to instantiate the correct sub-type
        Account account = switch (accountDto.accountType().toUpperCase()) {
            case "SAVINGS" -> new SavingsAccount();
            case "BUSINESS" -> new BusinessAccount();
            default -> throw new AccountException("Unknown account type: " + accountDto.accountType());
        };

        // Map common fields shared in the base abstract class
        account.setId(accountDto.id());
        account.setAccountHolderName(accountDto.accountHolderName());

        // Use deposit to safely set the balance domain state without bypassing guards
        account.deposit(accountDto.balance());

        return account;
    }

    public static AccountDto maptoAccountDto(Account account) {
        if (account == null) {
            return null;
        }

        // Determine the type string dynamically based on the instance type
        String type = account instanceof SavingsAccount ? "SAVINGS" : "BUSINESS";

        return new AccountDto(
                account.getId(),
                account.getAccountHolderName(),
                account.getBalance(),
                type
        );
    }
}