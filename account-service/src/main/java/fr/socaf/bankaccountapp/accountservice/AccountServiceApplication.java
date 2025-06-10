package fr.socaf.bankaccountapp.accountservice;

import fr.socaf.bankaccountapp.accountservice.clients.CustomerRestClient;
import fr.socaf.bankaccountapp.accountservice.entities.BankAccount;
import fr.socaf.bankaccountapp.accountservice.enums.AccountType;
import fr.socaf.bankaccountapp.accountservice.repository.BankAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;

import java.time.LocalDateTime;
import java.util.UUID;

@SpringBootApplication
@EnableFeignClients
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(BankAccountRepository accountRepository, CustomerRestClient customerRestClient) {
        return args -> {
            customerRestClient.allCustomers().forEach(c -> {
                BankAccount bankAccount1 = BankAccount.builder()
                        .accountId(UUID.randomUUID().toString())
                        .currency("EUR")
                        .balance(Math.random() * 80000)
                        .createAt(LocalDateTime.now())
                        .type(AccountType.CURRENT_ACCOUNT)
                        .customerId(c.getId())
                        .build();
                BankAccount bankAccount2 = BankAccount.builder()
                        .accountId(UUID.randomUUID().toString())
                        .currency("EUR")
                        .balance(Math.random() * 65432)
                        .createAt(LocalDateTime.now())
                        .type(AccountType.SAVINGS_ACCOUNT)
                        .customerId(c.getId())
                        .build();
                accountRepository.save(bankAccount1);
                accountRepository.save(bankAccount2);
            });
        };
    }
}