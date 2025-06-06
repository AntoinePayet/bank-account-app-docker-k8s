package fr.socaf.bankaccountapp.customerservice.repository;

import fr.socaf.bankaccountapp.customerservice.entities.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

}
