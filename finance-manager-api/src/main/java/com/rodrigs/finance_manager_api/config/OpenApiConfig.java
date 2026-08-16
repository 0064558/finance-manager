package com.rodrigs.finance_manager_api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
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
						.addSchemas("ProblemDetail", problemDetailSchema())
						.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
								.name(BEARER_AUTH)
								.type(SecurityScheme.Type.HTTP)
								.scheme("bearer")
								.bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

	private Schema<?> problemDetailSchema() {
		return new ObjectSchema()
				.description("Formato padrão de erro da API")
				.addProperty("type", new StringSchema().format("uri"))
				.addProperty("title", new StringSchema())
				.addProperty("status", new IntegerSchema().format("int32"))
				.addProperty("detail", new StringSchema())
				.addProperty("instance", new StringSchema().format("uri"))
				.addProperty("code", new StringSchema().example("VALIDATION_FAILED"))
				.addProperty("timestamp", new StringSchema().format("date-time"))
				.addProperty("traceId", new StringSchema().format("uuid"))
				.addProperty("fields", new ArraySchema().items(new ObjectSchema()
						.addProperty("field", new StringSchema())
						.addProperty("message", new StringSchema())));
	}

	@Bean
	public GroupedOpenApi financeManagerApiV1() {
		return GroupedOpenApi.builder()
				.group("v1")
				.pathsToMatch(API_V1_PATH + "/**")
				.build();
	}
}
