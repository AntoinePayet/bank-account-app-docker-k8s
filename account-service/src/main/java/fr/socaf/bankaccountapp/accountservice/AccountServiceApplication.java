package fr.socaf.bankaccountapp.accountservice;

import fr.socaf.bankaccountapp.accountservice.entities.BankAccount;
import fr.socaf.bankaccountapp.accountservice.enums.AccountType;
import fr.socaf.bankaccountapp.accountservice.repository.BankAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootApplication
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(BankAccountRepository accountRepository) {
        return args -> {
            BankAccount bankAccount1 = BankAccount.builder()
                    .accountId(UUID.randomUUID().toString())
                    .currency("EUR")
                    .balance(98000)
                    .createAt(LocalDateTime.now())
                    .type(AccountType.CURRENT_ACCOUNT)
                    .customerId(Long.valueOf(1))
                    .build();
            BankAccount bankAccount2 = BankAccount.builder()
                    .accountId(UUID.randomUUID().toString())
                    .currency("EUR")
                    .balance(54612)
                    .createAt(LocalDateTime.now())
                    .type(AccountType.SAVINGS_ACCOUNT)
                    .customerId(Long.valueOf(2))
                    .build();
            accountRepository.save(bankAccount1);
            accountRepository.save(bankAccount2);
        };
    }

}
