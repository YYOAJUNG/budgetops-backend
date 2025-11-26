package com.budgetops.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"com.budgetops.backend"})
@EnableJpaAuditing
public class BudgetopsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BudgetopsBackendApplication.class, args);
	}

}
