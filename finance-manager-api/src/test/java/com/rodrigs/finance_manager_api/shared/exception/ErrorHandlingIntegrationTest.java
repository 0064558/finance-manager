package com.rodrigs.finance_manager_api.shared.exception;

import com.jayway.jsonpath.JsonPath;
import com.rodrigs.finance_manager_api.FinanceManagerApiApplication;
import com.rodrigs.finance_manager_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(classes = FinanceManagerApiApplication.class)
@AutoConfigureMockMvc
class ErrorHandlingIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnStandardProblemDetailForBodyValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "email": "email-invalido",
                                  "password": "somente-letras"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.fields").isArray())
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void shouldReturnValidationProblemForInvalidQueryParameter() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("startDate", "not-a-date")
                        .queryParam("endDate", "2026-08-31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields[0].field").value("startDate"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void shouldReturnValidationProblemForMissingRequiredQueryParameter() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .queryParam("startDate", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.fields[0].field").value("endDate"));
    }

    @Test
    void shouldReturnValidationProblemForInvalidPathUuid() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(get("/api/v1/financial-accounts/not-a-uuid")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.fields[0].field").value("accountId"));
    }

    @Test
    void shouldReturnSafeProblemForMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST_BODY"))
                .andExpect(jsonPath("$.detail").value("The request body is missing or has an invalid format."))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    private String registerAndLogin() throws Exception {
        String email = "error-%s@email.com".formatted(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Rodrigo",
                                  "email": "%s",
                                  "password": "senha1234"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated());

        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "senha1234"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.accessToken");
    }
}
