package com.banking.account.entity;

import com.banking.account.exception.AccountException;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "accounts")
@Entity
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_holder_name")
    private String accountHolderName;

    private double balance;

    // The only way to change the balance is through strict business validation rules
    public void deposit(double amount) {
        if (amount > 0) {
            this.balance += amount;
        } else {
            throw new AccountException("Deposit amount must be positive");
        }
    }


}