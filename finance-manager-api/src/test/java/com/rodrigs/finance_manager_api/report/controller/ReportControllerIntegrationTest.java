package com.rodrigs.finance_manager_api.report.controller;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(classes = FinanceManagerApiApplication.class)
@AutoConfigureMockMvc
class ReportControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnSummaryForInclusivePeriod() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountId = createAccount(token, "Conta principal", "1000.00");
        String incomeCategoryId = createCategory(token, "Salário", "INCOME");
        String expenseCategoryId = createCategory(token, "Alimentação", "EXPENSE");
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(2);

        createTransaction(token, accountId, incomeCategoryId, "INCOME", "1600.00", startDate);
        createTransaction(token, accountId, expenseCategoryId, "EXPENSE", "100.00", endDate);
        createTransaction(token, accountId, expenseCategoryId, "EXPENSE", "999.00", startDate.minusDays(1));

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value(startDate.toString()))
                .andExpect(jsonPath("$.endDate").value(endDate.toString()))
                .andExpect(jsonPath("$.totalIncome").value(1600.00))
                .andExpect(jsonPath("$.totalExpense").value(100.00))
                .andExpect(jsonPath("$.netBalance").value(1500.00));
    }

    @Test
    void shouldReturnZeroSummaryWhenPeriodHasNoTransactions() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(2);

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", startDate.toString())
                        .param("endDate", endDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0.00))
                .andExpect(jsonPath("$.totalExpense").value(0.00))
                .andExpect(jsonPath("$.netBalance").value(0.00));
    }

    @Test
    void shouldReturnCurrentBalancesIncludingAccountWithoutTransactions() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String accountWithTransactions = createAccount(token, "Bradesco", "500.00");
        createAccount(token, "Nubank", "777.77");
        String incomeCategoryId = createCategory(token, "Salário", "INCOME");
        String expenseCategoryId = createCategory(token, "Alimentação", "EXPENSE");
        LocalDate occurredOn = LocalDate.now().minusDays(1);

        createTransaction(token, accountWithTransactions, incomeCategoryId, "INCOME", "1600.00", occurredOn);
        createTransaction(token, accountWithTransactions, expenseCategoryId, "EXPENSE", "100.00", occurredOn);

        mockMvc.perform(get("/api/v1/reports/balances")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(2777.77))
                .andExpect(jsonPath("$.accounts.length()").value(2))
                .andExpect(jsonPath("$.accounts[0].accountName").value("Bradesco"))
                .andExpect(jsonPath("$.accounts[0].balance").value(2000.00))
                .andExpect(jsonPath("$.accounts[1].accountName").value("Nubank"))
                .andExpect(jsonPath("$.accounts[1].balance").value(777.77));
    }

    @Test
    void shouldNotExposeBalancesFromAnotherUser() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherUserToken = registerAndLogin(uniqueEmail());

        createAccount(ownerToken, "Conta privada", "1000.00");

        mockMvc.perform(get("/api/v1/reports/balances")
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBalance").value(0.00))
                .andExpect(jsonPath("$.accounts.length()").value(0));
    }

    @Test
    void shouldRejectInvalidSummaryDateRange() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        mockMvc.perform(get("/api/v1/reports/summary")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2026-08-31")
                        .param("endDate", "2026-08-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSACTION_DATE_RANGE"));
    }

    @Test
    void shouldRejectReportRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/reports/summary")
                        .param("startDate", "2026-08-01")
                        .param("endDate", "2026-08-31"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reports/balances"))
                .andExpect(status().isUnauthorized());
    }

    private String createAccount(String token, String name, String initialBalance) throws Exception {
        String response = mockMvc.perform(post("/api/v1/financial-accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accountJson(name, initialBalance)))
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

    private void createTransaction(
            String token,
            String accountId,
            String categoryId,
            String type,
            String amount,
            LocalDate occurredOn
    ) throws Exception {
        mockMvc.perform(post("/api/v1/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transactionJson(
                                accountId,
                                categoryId,
                                type,
                                amount,
                                occurredOn
                        )))
                .andExpect(status().isCreated());
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

    private String accountJson(String name, String initialBalance) {
        return """
                {
                  "name": "%s",
                  "type": "CHECKING",
                  "initialBalance": %s
                }
                """.formatted(name, initialBalance);
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
            LocalDate occurredOn
    ) {
        return """
                {
                  "accountId": "%s",
                  "categoryId": "%s",
                  "type": "%s",
                  "amount": %s,
                  "occurredOn": "%s",
                  "description": "Teste"
                }
                """.formatted(accountId, categoryId, type, amount, occurredOn);
    }

    private String uniqueEmail() {
        return "report-%s@email.com".formatted(UUID.randomUUID());
    }
}
