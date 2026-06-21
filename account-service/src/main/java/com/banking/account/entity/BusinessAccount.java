package com.banking.account.entity;

import com.banking.account.exception.AccountException;
import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;

@Entity
@DiscriminatorValue("BUSINESS")
//Marked final to complete the sealed class contract safely
public final class BusinessAccount extends Account {

    private double overdraftLimit = 10000.0;

    public double getOverdraftLimit() { return overdraftLimit; }
    public void setOverdraftLimit(double overdraftLimit) { this.overdraftLimit = overdraftLimit; }

    @Override
    public void verifyWithdrawalLimit(double amount) throws AccountException {
        if ((this.balance - amount) < -overdraftLimit) {
            throw new AccountException("Transaction rejected: Request exceeds the certified business overdraft limit.");
        }
    }
}