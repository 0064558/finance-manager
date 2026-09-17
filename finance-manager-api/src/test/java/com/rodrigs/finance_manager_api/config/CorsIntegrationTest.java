package com.rodrigs.finance_manager_api.config;

import com.rodrigs.finance_manager_api.FinanceManagerApiApplication;
import com.rodrigs.finance_manager_api.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(
        classes = FinanceManagerApiApplication.class,
        properties = "app.cors.allowed-origins=https://frontend.example.com"
)
@AutoConfigureMockMvc
class CorsIntegrationTest extends PostgresIntegrationTest {

    private static final String ALLOWED_ORIGIN = "https://frontend.example.com";

    @Autowired
    private MockMvc mockMvc;

    // Teste para verificar se uma requisição preflight (OPTIONS) de um domínio permitido é aceita e retorna os cabeçalhos CORS corretos
    @Test
    void shouldAllowPreflightRequestFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/categories")
                        .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN) // Adiciona os cabeçalhos necessários para simular uma requisição preflight CORS
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET") // Indica o método HTTP que será usado na requisição real
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")) // Indica os cabeçalhos que serão usados na requisição real
                .andExpect(status().isOk()) // Verifica se a resposta tem status 200 OK
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN)) // Verifica se o cabeçalho Access-Control-Allow-Origin está presente e correto
                // Verifica se o cabeçalho Access-Control-Allow-Methods contém o método GET
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsString("GET")
                ))
                // Verifica se o cabeçalho Access-Control-Allow-Headers contém o cabeçalho Authorization
                .andExpect(header().string(
                        HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString("Authorization")
                ));
    }

    // Teste para verificar se uma requisição preflight (OPTIONS) de um domínio não permitido é rejeitada e não retorna os cabeçalhos CORS
    @Test
    void shouldRejectPreflightRequestFromUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/categories") // Simula uma requisição preflight CORS para o endpoint /api/v1/categories
                        .header(HttpHeaders.ORIGIN, "https://unknown.example.com") // Adiciona o cabeçalho Origin com um domínio não permitido
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET") // Adiciona o cabeçalho Access-Control-Request-Method indicando o método HTTP que será usado na requisição real
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization")) // Adiciona o cabeçalho Access-Control-Request-Headers indicando os cabeçalhos que serão usados na requisição real
                .andExpect(status().isForbidden()) // Verifica se a resposta tem status 403 Forbidden, indicando que a requisição foi rejeitada
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)); // Verifica se o cabeçalho Access-Control-Allow-Origin não está presente na resposta, indicando que a requisição foi rejeitada
    }
}
