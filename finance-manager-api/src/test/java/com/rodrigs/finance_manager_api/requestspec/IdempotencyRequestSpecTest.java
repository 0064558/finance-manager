package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Proteções da chave de idempotência por HTTP")
class IdempotencyRequestSpecTest extends HttpRequestSpec {
    @Test
    @DisplayName("it quando reutiliza uma chave com outro valor, então rejeita sem alterar o lançamento original")
    void changedPayload() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Despesa", "EXPENSE");
        var key = UUID.randomUUID().toString();
        var first = requestWithKey("POST", TRANSACTIONS, user.token(),
                transactionBody(account, category, "EXPENSE", "10", LocalDate.now()), key);
        first.status(201);
        var conflict = requestWithKey("POST", TRANSACTIONS, user.token(),
                transactionBody(account, category, "EXPENSE", "20", LocalDate.now()), key);
        conflict.status(409);
        assertThat(conflict.text("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
        request("GET", TRANSACTIONS + "/" + first.text("id"), user.token(), null).money("amount", "10");
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "90");
    }

    @Test
    @DisplayName("it quando uma tentativa falha, então desfaz também a reserva da chave e permite corrigir os dados")
    void failedCreationRollsBackKey() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Despesa", "EXPENSE");
        var key = UUID.randomUUID().toString();
        requestWithKey("POST", TRANSACTIONS, user.token(),
                transactionBody(account, category, "INCOME", "10", LocalDate.now()), key).status(409);
        requestWithKey("POST", TRANSACTIONS, user.token(),
                transactionBody(account, category, "EXPENSE", "10", LocalDate.now()), key).status(201);
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).hasSize(1);
        balances(user).money("totalBalance", "90");
    }

    @Test
    @DisplayName("it quando o reenvio chega depois da exclusão, então devolve a resposta original sem recriar a despesa")
    void deletedTransactionIsNotRecreated() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Despesa", "EXPENSE");
        var body = transactionBody(account, category, "EXPENSE", "10", LocalDate.now());
        var key = UUID.randomUUID().toString();
        var first = requestWithKey("POST", TRANSACTIONS, user.token(), body, key);
        first.status(201);
        request("DELETE", TRANSACTIONS + "/" + first.text("id"), user.token(), null).status(204);
        var replay = requestWithKey("POST", TRANSACTIONS, user.token(), body, key);
        replay.status(201);
        assertThat(JSON.readTree(replay.body())).isEqualTo(JSON.readTree(first.body()));
        request("GET", TRANSACTIONS + "/" + first.text("id"), user.token(), null).status(404);
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).isEmpty();
        balances(user).money("totalBalance", "100");
    }

    @Test
    @DisplayName("it quando a chave está vazia ou excede 200 caracteres, então retorna 400 sem efeito financeiro")
    void invalidKeys() throws Exception {
        var user = user();
        var account = account(user, "Conta", "100");
        var category = category(user, "Despesa", "EXPENSE");
        var body = transactionBody(account, category, "EXPENSE", "10", LocalDate.now());
        for (var key : new String[]{"", "k".repeat(201)}) {
            requestWithKey("POST", TRANSACTIONS, user.token(), body, key).status(400);
        }
        assertThat(request("GET", TRANSACTIONS, user.token(), null).list("content")).isEmpty();
        balances(user).money("totalBalance", "100");
    }

    @Test
    @DisplayName("it quando o navegador pede autorização CORS para Idempotency-Key, então permite o envio pela origem do app")
    void browserPreflight() throws Exception {
        rawRequest("OPTIONS", TRANSACTIONS, null, null, Map.of(
                "Origin", "http://localhost:4200",
                "Access-Control-Request-Method", "POST",
                "Access-Control-Request-Headers", "authorization,content-type,idempotency-key")).status(200);
    }
}
