package com.rodrigs.finance_manager_api.transaction.controller;

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
class TransactionControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateFindUpdateAndDeleteTransactionForAuthenticatedUser() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal");
        String categoryId = createCategory(token, "Alimentação", "EXPENSE");
        LocalDate occurredOn = LocalDate.now().minusDays(1);

        String transactionId = createTransaction(
                token,
                accountId,
                categoryId,
                "EXPENSE",
                "100.00",
                occurredOn,
                "Mercado"
        );

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.type").value("EXPENSE"))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.description").value("Mercado"));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                "EXPENSE",
                                "150.75",
                                occurredOn,
                                "Mercado atualizado"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transactionId))
                .andExpect(jsonPath("$.amount").value(150.75))
                .andExpect(jsonPath("$.description").value("Mercado atualizado"));

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
    }

    @Test
    void shouldListTransactionsWithPaginationAndFilters() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal");
        String categoryId = createCategory(token, "Alimentação", "EXPENSE");
        LocalDate firstDate = LocalDate.now().minusDays(4);
        LocalDate secondDate = LocalDate.now().minusDays(2);

        createTransaction(token, accountId, categoryId, "EXPENSE", "50.00", firstDate, "Primeira");
        createTransaction(token, accountId, categoryId, "EXPENSE", "75.00", secondDate, "Segunda");

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "EXPENSE")
                        .param("startDate", firstDate.toString())
                        .param("endDate", secondDate.toString())
                        .param("accountId", accountId)
                        .param("categoryId", categoryId)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Segunda"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    void shouldFilterTransactionsByType() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal");
        String expenseCategoryId = createCategory(token, "Alimentação", "EXPENSE");
        String incomeCategoryId = createCategory(token, "Salário", "INCOME");
        LocalDate occurredOn = LocalDate.now().minusDays(1);

        createTransaction(token, accountId, expenseCategoryId, "EXPENSE", "50.00", occurredOn, "Despesa");
        createTransaction(token, accountId, incomeCategoryId, "INCOME", "500.00", occurredOn, "Receita");

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("type", "INCOME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].type").value("INCOME"))
                .andExpect(jsonPath("$.content[0].description").value("Receita"));
    }

    @Test
    void shouldHideTransactionsFromAnotherAuthenticatedUser() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherUserToken = registerAndLogin(uniqueEmail());
        String accountId = createAccount(ownerToken, "Conta privada");
        String categoryId = createCategory(ownerToken, "Privada", "EXPENSE");
        String transactionId = createTransaction(
                ownerToken,
                accountId,
                categoryId,
                "EXPENSE",
                "100.00",
                LocalDate.now().minusDays(1),
                "Privada"
        );

        mockMvc.perform(get("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(put("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                "EXPENSE",
                                "999.00",
                                LocalDate.now().minusDays(1),
                                "Tentativa"
                        )))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/transactions/{transactionId}", transactionId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectTransactionsUsingAccountOrCategoryFromAnotherUser() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherUserToken = registerAndLogin(uniqueEmail());
        String ownerAccountId = createAccount(ownerToken, "Conta do proprietário");
        String ownerCategoryId = createCategory(ownerToken, "Categoria do proprietário", "EXPENSE");
        String otherAccountId = createAccount(otherUserToken, "Conta de outro usuário");
        String otherCategoryId = createCategory(otherUserToken, "Categoria de outro usuário", "EXPENSE");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                otherAccountId,
                                ownerCategoryId,
                                "EXPENSE",
                                "10.00",
                                LocalDate.now().minusDays(1),
                                "Categoria de outro usuário"
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                ownerAccountId,
                                otherCategoryId,
                                "EXPENSE",
                                "10.00",
                                LocalDate.now().minusDays(1),
                                "Conta de outro usuário"
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINANCIAL_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void shouldFilterTransactionsIndependentlyByAccountCategoryAndDateRange() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String firstAccountId = createAccount(token, "Conta principal");
        String secondAccountId = createAccount(token, "Conta secundária");
        String categoryId = createCategory(token, "Alimentação", "EXPENSE");
        LocalDate firstDate = LocalDate.now().minusDays(5);
        LocalDate secondDate = LocalDate.now().minusDays(3);

        createTransaction(token, firstAccountId, categoryId, "EXPENSE", "50.00", firstDate, "Primeira");
        createTransaction(token, secondAccountId, categoryId, "EXPENSE", "75.00", secondDate, "Segunda");

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("accountId", secondAccountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Segunda"));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("categoryId", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", firstDate.toString())
                        .param("endDate", firstDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].description").value("Primeira"));
    }

    @Test
    void shouldRejectInvalidTransactionRequests() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal");
        String categoryId = createCategory(token, "Alimentação", "EXPENSE");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                "EXPENSE",
                                "0.00",
                                LocalDate.now().minusDays(1),
                                "Inválida"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                "EXPENSE",
                                "10.00",
                                LocalDate.now().plusDays(1),
                                "Futura"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectIncompatibleTypeAndInvalidDateRange() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal");
        String expenseCategoryId = createCategory(token, "Alimentação", "EXPENSE");

        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                expenseCategoryId,
                                "INCOME",
                                "10.00",
                                LocalDate.now().minusDays(1),
                                "Tipo incompatível"
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSACTION_TYPE_MISMATCH"));

        mockMvc.perform(get("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-08-31")
                        .param("endDate", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_DATE_RANGE"));
    }

    @Test
    void shouldRejectTransactionRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String createAccount(String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(name)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createCategory(String token, String name, String transactionType) throws Exception {
        String response = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson(name, transactionType)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
    }

    private String createTransaction(
            String token,
            String accountId,
            String categoryId,
            String type,
            String amount,
            LocalDate occurredOn,
            String description
    ) throws Exception {
        String response = mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                type,
                                amount,
                                occurredOn,
                                description
                        )))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return JsonPath.read(response, "$.id");
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

    private String accountJson(String name) {
        return """
                {
                  "name": "%s",
                  "type": "CHECKING",
                  "initialBalance": 1000.00
                }
                """.formatted(name);
    }

    private String categoryJson(String name, String transactionType) {
        return """
                {
                  "name": "%s",
                  "transactionType": "%s"
                }
                """.formatted(name, transactionType);
    }

    private String transactionJson(
            String accountId,
            String categoryId,
            String type,
            String amount,
            LocalDate occurredOn,
            String description
    ) {
        return """
                {
                  "accountId": "%s",
                  "categoryId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "occurredOn": "%s",
                  "description": "%s"
                }
                """.formatted(accountId, categoryId, type, amount, occurredOn, description);
    }

    private String uniqueEmail() {
        return "transaction-%s@email.com".formatted(UUID.randomUUID());
    }
}
