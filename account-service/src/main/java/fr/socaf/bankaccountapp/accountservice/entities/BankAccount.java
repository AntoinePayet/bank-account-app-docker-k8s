package fr.socaf.bankaccountapp.accountservice.entities;

import fr.socaf.bankaccountapp.accountservice.enums.AccountType;
import fr.socaf.bankaccountapp.accountservice.model.Customer;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter @Setter @ToString @NoArgsConstructor @AllArgsConstructor @Builder
public class BankAccount {
    @Id
    private String accountId;
    private double balance;
    private LocalDateTime createAt;
    private String currency;
    @Enumerated(EnumType.STRING)
    private AccountType type;
    @Transient
    private Customer customer;
    private Long customerId;

}
