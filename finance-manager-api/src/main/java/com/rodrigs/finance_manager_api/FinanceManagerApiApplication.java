package com.rodrigs.finance_manager_api;

import com.rodrigs.finance_manager_api.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@EnableConfigurationProperties(JwtProperties.class)
@SpringBootApplication
public class FinanceManagerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceManagerApiApplication.class, args);

	}

}
