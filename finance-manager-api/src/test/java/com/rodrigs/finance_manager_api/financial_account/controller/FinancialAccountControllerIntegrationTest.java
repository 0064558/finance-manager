package com.rodrigs.finance_manager_api.financial_account.controller;

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

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(classes = FinanceManagerApiApplication.class)
@AutoConfigureMockMvc
class FinancialAccountControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAndListAccountsForAuthenticatedUser() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("Conta principal", "CHECKING", "100.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Conta principal"))
                .andExpect(jsonPath("$.type").value("CHECKING"))
                .andExpect(jsonPath("$.initialBalance").value(100.00));

        mockMvc.perform(get("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Conta principal"));
    }

    @Test
    void shouldUpdateAndDeleteAccountOwnedByAuthenticatedUser() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token);

        mockMvc.perform(put("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("Conta atualizada", "SAVINGS", "150.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Conta atualizada"))
                .andExpect(jsonPath("$.type").value("SAVINGS"));

        mockMvc.perform(delete("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINANCIAL_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldHideAccountFromAnotherAuthenticatedUser() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherUserToken = registerAndLogin(uniqueEmail());
        String accountId = createAccount(ownerToken);

        mockMvc.perform(get("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINANCIAL_ACCOUNT_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("Tentativa", "CASH", "999.00")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectInvalidAccountRequest() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("", "CASH", "100.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldProtectAccountHistoryFromBalanceChangesAndDeletion() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token);
        String categoryId = createCategory(token);

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(accountId, categoryId)))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("Conta atualizada", "CHECKING", "200.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCIAL_ACCOUNT_HAS_TRANSACTIONS"));

        mockMvc.perform(delete("/api/v1/financial-accounts/{accountId}", accountId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCIAL_ACCOUNT_HAS_TRANSACTIONS"));
    }

    private String createAccount(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson("Conta de teste", "CASH", "100.00")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createCategory(String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alimentação",
                                  "transactionType": "EXPENSE"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String transactionJson(String accountId, String categoryId) {
        return """
                {
                  "accountId": "%s",
                  "categoryId": "%s",
                  "type": "EXPENSE",
                  "amount": 25.00,
                  "occurredOn": "%s",
                  "description": "Compra"
                }
                """.formatted(accountId, categoryId, LocalDate.now().minusDays(1));
    }

    private String registerAndLogin(String email) throws Exception {
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

        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
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

        return JsonPath.read(loginResponse, "$.accessToken");
    }

    private String accountJson(String name, String type, String initialBalance) {
        return """
                {
                  "name": "%s",
                  "type": "%s",
                  "initialBalance": %s
                }
                """.formatted(name, type, initialBalance);
    }

    private String uniqueEmail() {
        return "account-%s@email.com".formatted(UUID.randomUUID());
    }
}
