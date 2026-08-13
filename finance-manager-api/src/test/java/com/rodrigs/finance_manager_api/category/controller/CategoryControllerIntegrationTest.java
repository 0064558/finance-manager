package com.rodrigs.finance_manager_api.category.controller;

import com.jayway.jsonpath.JsonPath;
import com.rodrigs.finance_manager_api.FinanceManagerApiApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
class CategoryControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateListAndFindCategoryForAuthenticatedUser() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        String categoryId = createCategory(token, "Alimentação", "EXPENSE");

        mockMvc.perform(get("/api/v1/categories")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(categoryId))
                .andExpect(jsonPath("$[0].name").value("Alimentação"))
                .andExpect(jsonPath("$[0].transactionType").value("EXPENSE"));

        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Alimentação"))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"));
    }

    @Test
    void shouldUpdateAndDeleteCategoryOwnedByAuthenticatedUser() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        String categoryId = createCategory(token, "Transporte", "EXPENSE");

        mockMvc.perform(put("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Transporte atualizado", "EXPENSE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId))
                .andExpect(jsonPath("$.name").value("Transporte atualizado"))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"));

        mockMvc.perform(delete("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void shouldHideCategoryFromAnotherAuthenticatedUser() throws Exception {
        String ownerToken = registerAndLogin(uniqueEmail());
        String otherUserToken = registerAndLogin(uniqueEmail());
        String categoryId = createCategory(ownerToken, "Privada", "EXPENSE");

        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + otherUserToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Tentativa", "EXPENSE")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/categories/{categoryId}", categoryId)
                        .header("Authorization", "Bearer " + otherUserToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectDuplicateCategoryForSameUserAndType() throws Exception {
        String token = registerAndLogin(uniqueEmail());
        createCategory(token, "Alimentação", "EXPENSE");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("  alimentação  ", "EXPENSE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));
    }

    @Test
    void shouldAllowSameCategoryNameForDifferentTransactionTypes() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        createCategory(token, "Salário", "INCOME");

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Salário", "EXPENSE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Salário"))
                .andExpect(jsonPath("$.transactionType").value("EXPENSE"));
    }

    @Test
    void shouldRejectInvalidCategoryRequest() throws Exception {
        String token = registerAndLogin(uniqueEmail());

        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("", "EXPENSE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldRejectCategoryRequestsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(categoryJson("Sem token", "EXPENSE")))
                .andExpect(status().isUnauthorized());
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

    private String categoryJson(String name, String transactionType) {
        return """
                {
                  "name": "%s",
                  "transactionType": "%s"
                }
                """.formatted(name, transactionType);
    }

    private String uniqueEmail() {
        return "category-%s@email.com".formatted(UUID.randomUUID());
    }
}
