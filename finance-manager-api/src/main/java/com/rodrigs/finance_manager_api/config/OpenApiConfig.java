package com.rodrigs.finance_manager_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String API_V1_PATH = "/api/v1";

	@Bean
	public OpenAPI financeManagerOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Finance Manager API")
						.description("API for managing personal finances.")
						.version("v1"));
	}

	@Bean
	public GroupedOpenApi financeManagerApiV1() {
		return GroupedOpenApi.builder()
				.group("v1")
				.pathsToMatch(API_V1_PATH + "/**")
				.build();
	}
}
