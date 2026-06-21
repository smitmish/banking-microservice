package com.banking.account.entity;

import com.banking.account.exception.AccountException;
import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

@Entity
@DiscriminatorValue("SAVINGS")
//Marked final to complete the sealed class contract safely
public final class SavingsAccount extends Account {

    private static final double MIN_BALANCE = 500.0;

    @Override
    public void verifyWithdrawalLimit(double amount) throws AccountException {
        if ((this.balance - amount) < MIN_BALANCE) {
            throw new AccountException("Savings account balance cannot fall below the required minimum of " + MIN_BALANCE);
        }
    }
}