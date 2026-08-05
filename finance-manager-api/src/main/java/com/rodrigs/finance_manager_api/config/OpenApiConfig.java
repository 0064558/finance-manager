package com.rodrigs.finance_manager_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	private static final String API_V1_PATH = "/api/v1";
	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	public OpenAPI financeManagerOpenAPI() {
		return new OpenAPI()
				.info(new Info()
						.title("Finance Manager API")
						.description("API for managing personal finances.")
						.version("v1"))
				.components(new Components()
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.name(BEARER_AUTH)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

	@Bean
	public GroupedOpenApi financeManagerApiV1() {
		return GroupedOpenApi.builder()
				.group("v1")
				.pathsToMatch(API_V1_PATH + "/**")
				.build();
	}
}