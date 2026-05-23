package com.banking.account.mapper;

import com.banking.account.dto.AccountDto;
import com.banking.account.entity.Account;

public class AccountMapper {
    public static Account maptoAccount(AccountDto accountDto) {
        return new Account(accountDto.id(), accountDto.accountHolderName(), accountDto.balance());
    }

    public static AccountDto maptoAccountDto(Account account) {
        return new AccountDto(account.getId(), account.getAccountHolderName(), account.getBalance());
    }
}