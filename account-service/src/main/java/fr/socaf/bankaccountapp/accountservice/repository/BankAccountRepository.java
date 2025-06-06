package fr.socaf.bankaccountapp.accountservice.repository;


import fr.socaf.bankaccountapp.accountservice.entities.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
    
}
