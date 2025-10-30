package com.budgetops.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.budgetops.backend"})
public class BudgetopsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BudgetopsBackendApplication.class, args);
	}

}
