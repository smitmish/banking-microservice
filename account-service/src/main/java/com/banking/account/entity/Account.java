package com.banking.account.entity;

import com.banking.account.exception.AccountException;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "accounts")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "account_type", discriminatorType = DiscriminatorType.STRING)

//Upgrade: Marked as sealed and restricted to our exact domain subclasses
public abstract sealed class Account permits SavingsAccount, BusinessAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    protected double balance;

    // Getters, setters, and deposit() remain identical...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountHolderName() { return accountHolderName; }
    public void setAccountHolderName(String accountHolderName) { this.accountHolderName = accountHolderName; }
    public double getBalance() { return balance; }

    public void deposit(double amount) {
        if (amount > 0) {
            this.balance += amount;
        } else {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }
    }

    public abstract void verifyWithdrawalLimit(double amount) throws AccountException;
}