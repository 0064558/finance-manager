package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import com.rodrigs.finance_manager_api.transfer.dto.CreateAccountTransferRequestDTO;
import com.rodrigs.finance_manager_api.transfer.service.AccountTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountTransfersRequestSpecTest extends HttpRequestSpec {
    private static final String TRANSFERS = "/api/v1/transfers";

    @Autowired
    private AccountTransferService transferService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void transfersOnlyChangeIndividualBalancesAndAppearInPrivateHistory() throws Exception {
        var owner = user();
        var other = user();
        String source = account(owner, "Origem", "100");
        String destination = account(owner, "Destino", "20");
        String income = category(owner, "Salário", "INCOME");
        String expense = category(owner, "Mercado", "EXPENSE");
        LocalDate date = LocalDate.now().minusDays(1);
        transaction(owner, source, income, "INCOME", "30", date);
        transaction(owner, source, expense, "EXPENSE", "10", date);

        var transfer = request("POST", TRANSFERS, owner.token(), body(source, destination, "25.50", date));
        transfer.status(201);
        assertThat(transfer.text("sourceAccountId")).isEqualTo(source);
        assertThat(transfer.text("destinationAccountId")).isEqualTo(destination);
        assertThat(transfer.text("occurredOn")).isEqualTo(date.toString());
        assertThat(transfer.text("description")).isEqualTo("Reserva");
        transfer.money("amount", "25.50");

        request("POST", TRANSFERS, owner.token(), body(destination, source, "5", date.plusDays(1))).status(201);
        assertBalances(owner, source, "99.50", destination, "40.50", "140");

        String range = period(date, date.plusDays(1));
        var summary = request("GET", REPORTS + "/summary" + range, owner.token(), null);
        summary.status(200);
        summary.money("totalIncome", "30");
        summary.money("totalExpense", "10");
        summary.money("netBalance", "20");
        var cashFlow = request("GET", REPORTS + "/cash-flow" + range, owner.token(), null);
        cashFlow.status(200);
        cashFlow.money("points.0.totalIncome", "30");
        cashFlow.money("points.0.totalExpense", "10");
        cashFlow.money("points.1.totalIncome", "0");
        cashFlow.money("points.1.totalExpense", "0");
        var byCategory = request("GET", REPORTS + "/expenses-by-category" + range, owner.token(), null);
        byCategory.status(200);
        byCategory.money("totalExpense", "10");
        assertThat(request("GET", TRANSACTIONS, owner.token(), null).list("content")).hasSize(2);
        var csv = request("GET", REPORTS + "/export" + range + "&format=CSV", owner.token(), null);
        csv.status(200);
        assertThat(csv.body()).contains(";Receita;Origem;Salário;30,00", ";Despesa;Origem;Mercado;10,00")
                .doesNotContain("Reserva", "25,50");

        var history = request("GET", TRANSFERS + "?page=1&size=1", owner.token(), null);
        history.status(200);
        assertThat(history.value("totalElements")).isEqualTo(2);
        assertThat(history.text("content.0.id")).isEqualTo(transfer.text("id"));
        assertThat(request("GET", TRANSFERS, other.token(), null).list("content")).isEmpty();
    }

    @Test
    void rejectsInvalidAndForeignAccountsWithoutChangingEitherBalance() throws Exception {
        var owner = user();
        var other = user();
        String source = account(owner, "Origem", "100");
        String destination = account(owner, "Destino", "20");
        String foreign = account(other, "Conta de outro usuário", "500");
        LocalDate date = LocalDate.now().minusDays(1);
        var valid = body(source, destination, "10", date);
        request("POST", TRANSFERS, null, valid).status(401);
        request("GET", TRANSFERS, null, null).status(401);
        var same = request("POST", TRANSFERS, owner.token(), body(source, source, "10", date));
        same.status(400);
        assertThat(same.text("code")).isEqualTo("SAME_TRANSFER_ACCOUNT");
        for (var invalid : List.of(
                body(source, foreign, "10", date),
                body(foreign, destination, "10", date),
                body(source, UUID.randomUUID().toString(), "10", date))) {
            var reply = request("POST", TRANSFERS, owner.token(), invalid);
            reply.status(404);
            assertThat(reply.text("code")).isEqualTo("FINANCIAL_ACCOUNT_NOT_FOUND");
        }
        for (String amount : List.of("0", "-1", "0.001", "100000000000000000")) {
            request("POST", TRANSFERS, owner.token(), body(source, destination, amount, date)).status(400);
        }
        request("POST", TRANSFERS, owner.token(), body(source, destination, "10", LocalDate.now().plusDays(1))).status(400);
        requestWithKey("POST", TRANSFERS, owner.token(), valid, "").status(400);

        assertBalances(owner, source, "100", destination, "20", "120");
        balances(other).money("totalBalance", "500");
        assertThat(request("GET", TRANSFERS, owner.token(), null).list("content")).isEmpty();
    }

    @Test
    void rollsBackBothBalanceEffectsAndAllowsAReplayAfterRollback() throws Exception {
        var owner = user();
        String source = account(owner, "Origem", "100");
        String destination = account(owner, "Destino", "20");
        LocalDate date = LocalDate.now().minusDays(1);
        String key = UUID.randomUUID().toString();
        var transfer = new CreateAccountTransferRequestDTO(UUID.fromString(source), UUID.fromString(destination),
                new BigDecimal("30"), date, null);

        transactionTemplate.executeWithoutResult(status -> {
            var result = transferService.create(UUID.fromString(owner.id()), transfer, key);
            assertThat(result.id()).isNotNull();
            status.setRollbackOnly();
        });

        assertBalances(owner, source, "100", destination, "20", "120");
        assertThat(request("GET", TRANSFERS, owner.token(), null).list("content")).isEmpty();
        requestWithKey("POST", TRANSFERS, owner.token(), body(source, destination, "30", date), key).status(201);
        assertBalances(owner, source, "70", destination, "50", "120");
    }

    @Test
    void handlesConcurrentReplaysAndTransfersInOppositeDirections() throws Exception {
        var owner = user();
        String source = account(owner, "Origem", "100");
        String destination = account(owner, "Destino", "20");
        LocalDate date = LocalDate.now().minusDays(1);
        String key = UUID.randomUUID().toString();
        var forward = body(source, destination, "12.34", date);
        var reverse = body(destination, source, "2.34", date);
        var replies = simultaneously(
                () -> requestWithKey("POST", TRANSFERS, owner.token(), forward, key),
                () -> requestWithKey("POST", TRANSFERS, owner.token(), forward, key),
                () -> request("POST", TRANSFERS, owner.token(), reverse));
        replies.forEach(reply -> reply.status(201));
        assertThat(JSON.readTree(replies.get(0).body())).isEqualTo(JSON.readTree(replies.get(1).body()));
        var conflict = requestWithKey("POST", TRANSFERS, owner.token(), reverse, key);
        conflict.status(409);
        assertThat(conflict.text("code")).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertBalances(owner, source, "90", destination, "30", "120");
        assertThat(request("GET", TRANSFERS, owner.token(), null).list("content")).hasSize(2);
    }

    @Test
    void preservesInitialBalancesAndProtectsAccountsWithTransferHistory() throws Exception {
        var owner = user();
        String source = account(owner, "Origem", "10");
        String destination = account(owner, "Destino", "20");
        LocalDate date = LocalDate.now().minusDays(1);
        request("POST", TRANSFERS, owner.token(), body(source, destination, "30", date)).status(201);
        assertBalances(owner, source, "-20", destination, "50", "30");

        for (String id : List.of(source, destination)) {
            String path = ACCOUNTS + "/" + id;
            String originalBalance = id.equals(source) ? "10" : "20";
            request("GET", path, owner.token(), null).money("initialBalance", originalBalance);
            var changed = request("PUT", path, owner.token(), accountBody("Renomeada", "CASH", "0"));
            changed.status(409);
            assertThat(changed.text("code")).isEqualTo("FINANCIAL_ACCOUNT_HAS_TRANSACTIONS");
            var deleted = request("DELETE", path, owner.token(), null);
            deleted.status(409);
            assertThat(deleted.text("code")).isEqualTo("FINANCIAL_ACCOUNT_HAS_TRANSACTIONS");
        }
        request("PUT", ACCOUNTS + "/" + source, owner.token(), accountBody("Renomeada", "CASH", "10")).status(200);
        assertBalances(owner, source, "-20", destination, "50", "30");
    }

    private Map<String, Object> body(String source, String destination, String amount, LocalDate occurredOn) {
        return Map.of("sourceAccountId", source, "destinationAccountId", destination,
                "amount", amount, "occurredOn", occurredOn.toString(), "description", "  Reserva  ");
    }

    private void assertBalances(UserSession owner, String source, String sourceBalance,
                                String destination, String destinationBalance, String total) throws Exception {
        var result = balances(owner);
        result.money("totalBalance", total);
        assertThat(result.list("accounts")).hasSize(2);
        assertThat(result.text("accounts.0.accountId")).isEqualTo(source);
        result.money("accounts.0.balance", sourceBalance);
        assertThat(result.text("accounts.1.accountId")).isEqualTo(destination);
        result.money("accounts.1.balance", destinationBalance);
    }
}
