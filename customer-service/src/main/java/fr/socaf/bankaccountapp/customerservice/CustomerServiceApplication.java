package fr.socaf.bankaccountapp.customerservice;

import fr.socaf.bankaccountapp.customerservice.config.GlobalConfig;
import fr.socaf.bankaccountapp.customerservice.entities.Customer;
import fr.socaf.bankaccountapp.customerservice.repository.CustomerRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

@SpringBootApplication
@EnableConfigurationProperties(GlobalConfig.class)
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner commandLineRunner(CustomerRepository customerRepository) {
        return args -> {
            List<Customer> customerList = List.of(
                    Customer.builder()
                            .firstName("Antoine")
                            .lastName("Payet")
                            .email("antoine@gmail.com")
                            .build(),
                    Customer.builder()
                            .firstName("Gilou")
                            .lastName("Payet")
                            .email("gilles@gmail.com")
                            .build()
            );
            customerRepository.saveAll(customerList);
        };
    }

}
