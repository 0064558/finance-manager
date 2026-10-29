package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Cadastro, login e identidade por HTTP")
class AuthRequestSpecTest extends HttpRequestSpec {
    private static final String TEST_SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    @Autowired JdbcTemplate database;
    @Autowired PasswordEncoder passwords;

    @Test
    @DisplayName("it quando cadastra dados válidos, então normaliza, guarda BCrypt e inicia onboarding zero")
    void registration() throws Exception {
        var reply = request("POST", AUTH + "/register", null,
                registration("  Pessoa Nova  ", "PESSOA@example.test", PASSWORD));
        reply.status(201);
        assertThat(reply.text("name")).isEqualTo("Pessoa Nova");
        assertThat(reply.text("email")).isEqualTo("pessoa@example.test");
        assertThat(reply.value("onboardingVersion")).isEqualTo(0);
        assertThat(reply.value("password")).isNull();
        assertThat(reply.value("passwordHash")).isNull();
        String hash = database.queryForObject("SELECT password_hash FROM users WHERE id = ?::uuid", String.class, reply.text("id"));
        assertThat(hash).isNotEqualTo(PASSWORD);
        assertThat(passwords.matches(PASSWORD, hash)).isTrue();
        var login = request("POST", AUTH + "/login", null, credentials("pessoa@example.test", PASSWORD));
        login.status(200);
        assertThat(login.value("user.passwordHash")).isNull();
        var me = request("GET", AUTH + "/me", login.text("accessToken"), null);
        me.status(200);
        assertThat(me.text("id")).isEqualTo(reply.text("id"));
    }

    static Stream<Map<String, Object>> invalidRegistrations() {
        return Stream.of(
                Map.of("name", "A", "email", "valid@example.test", "password", PASSWORD),
                Map.of("name", " ".repeat(4), "email", "valid@example.test", "password", PASSWORD),
                Map.of("name", "A".repeat(101), "email", "valid@example.test", "password", PASSWORD),
                Map.of("name", "Pessoa", "email", "invalido", "password", PASSWORD),
                Map.of("name", "Pessoa", "email", "valid@example.test", "password", "senha12"),
                Map.of("name", "Pessoa", "email", "valid@example.test", "password", "a".repeat(72) + "1"),
                Map.of("name", "Pessoa", "email", "valid@example.test", "password", "abcdefgh"),
                Map.of("name", "Pessoa", "email", "valid@example.test", "password", "12345678"),
                Map.of());
    }

    @ParameterizedTest
    @MethodSource("invalidRegistrations")
    @DisplayName("it quando o cadastro é inválido ou incompleto, então retorna 400 e não persiste usuário")
    void invalidRegistration(Map<String, Object> body) throws Exception {
        request("POST", AUTH + "/register", null, body).status(400);
        assertThat(database.queryForObject("SELECT count(*) FROM users", Integer.class)).isZero();
    }

    @Test
    @DisplayName("it quando nome e senha estão nos limites, então aceita sem truncar")
    void registrationBoundaries() throws Exception {
        for (var pair : List.of(new String[]{"Ab", "senha123"}, new String[]{"N".repeat(100), "a".repeat(71) + "1"})) {
            String email = UUID.randomUUID() + "@example.test";
            request("POST", AUTH + "/register", null, registration(pair[0], email, pair[1])).status(201);
            request("POST", AUTH + "/login", null, credentials(email, pair[1])).status(200);
        }
    }

    @Test
    @DisplayName("it quando repete cadastro ou cadastra simultaneamente, então só existe um usuário por e-mail")
    void duplicateRegistration() throws Exception {
        var body = registration("Pessoa", "duplicate@example.test", PASSWORD);
        var replies = simultaneously(() -> request("POST", AUTH + "/register", null, body),
                () -> request("POST", AUTH + "/register", null, body));
        assertThat(replies).extracting(Reply::code).containsExactlyInAnyOrder(201, 409);
        request("POST", AUTH + "/register", null, registration("Outro", "DUPLICATE@example.test", "outra1234")).status(409);
        assertThat(database.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
        request("POST", AUTH + "/login", null, credentials("duplicate@example.test", PASSWORD)).status(200);
        request("POST", AUTH + "/login", null, credentials("duplicate@example.test", "outra1234")).status(401);
    }

    @Test
    @DisplayName("it quando senha é incorreta ou e-mail inexiste, então usa o mesmo erro público sem emitir token")
    void invalidCredentials() throws Exception {
        var user = user();
        var wrong = request("POST", AUTH + "/login", null, credentials(user.email(), "errada123"));
        var unknown = request("POST", AUTH + "/login", null, credentials("unknown@example.test", "errada123"));
        wrong.status(401);
        unknown.status(401);
        for (String field : List.of("code", "title", "detail")) assertThat(wrong.value(field)).isEqualTo(unknown.value(field));
        assertThat(wrong.value("accessToken")).isNull();
        request("POST", AUTH + "/login", null, Map.of()).status(400);
        request("POST", AUTH + "/login", null, credentials("invalido", PASSWORD)).status(400);
    }

    @Test
    @DisplayName("it quando dois logins válidos chegam juntos, então ambos autenticam sem duplicar cadastro")
    void concurrentLogins() throws Exception {
        var user = user();
        var replies = simultaneously(
                () -> request("POST", AUTH + "/login", null, credentials(user.email().toUpperCase(), PASSWORD)),
                () -> request("POST", AUTH + "/login", null, credentials(user.email(), PASSWORD)));
        for (var reply : replies) {
            reply.status(200);
            var me = request("GET", AUTH + "/me", reply.text("accessToken"), null);
            me.status(200);
            assertThat(me.text("id")).isEqualTo(user.id());
        }
        assertThat(database.queryForObject("SELECT count(*) FROM users", Integer.class)).isEqualTo(1);
    }

    static Stream<String> invalidTokens() {
        return Stream.of("ABSENT", "token-invalido", signedToken(TEST_SECRET, UUID.randomUUID().toString(), -60),
                signedToken("f".repeat(64), UUID.randomUUID().toString(), 3600),
                signedToken(TEST_SECRET, UUID.randomUUID().toString(), 3600));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    @DisplayName("it quando o token é ausente, inválido, expirado ou de usuário inexistente, então toda rota protegida retorna 401")
    void protectedRoutes(String token) throws Exception {
        String id = UUID.randomUUID().toString();
        String bearer = token.equals("ABSENT") ? null : token;
        for (String base : List.of(ACCOUNTS, CATEGORIES, TRANSACTIONS)) {
            for (String method : List.of("GET", "POST")) request(method, base, bearer, method.equals("POST") ? Map.of() : null).status(401);
            for (String method : List.of("GET", "PUT", "DELETE")) request(method, base + "/" + id, bearer, method.equals("PUT") ? Map.of() : null).status(401);
        }
        request("GET", AUTH + "/me", bearer, null).status(401);
        request("PATCH", AUTH + "/me/onboarding", bearer, Map.of("onboardingVersion", 1)).status(401);
        for (String report : List.of("summary", "balances", "cash-flow", "expenses-by-category", "category-breakdown")) {
            request("GET", REPORTS + "/" + report + period(java.time.LocalDate.now().minusDays(1), java.time.LocalDate.now()) + "&type=EXPENSE", bearer, null).status(401);
        }
    }

    @Test
    @DisplayName("it quando A e B consultam juntos tentando informar userId alheio, então cada identidade continua isolada")
    void identityIsolation() throws Exception {
        var a = user();
        var b = user();
        var replies = simultaneously(() -> request("GET", AUTH + "/me?userId=" + b.id(), a.token(), null),
                () -> request("GET", AUTH + "/me?userId=" + a.id(), b.token(), null));
        replies.forEach(r -> r.status(200));
        assertThat(replies.get(0).text("id")).isEqualTo(a.id());
        assertThat(replies.get(1).text("id")).isEqualTo(b.id());
        var malicious = new java.util.HashMap<>(registration("Atacante", "attack@example.test", PASSWORD));
        malicious.put("id", b.id());
        malicious.put("onboardingVersion", 1);
        var registered = request("POST", AUTH + "/register", null, malicious);
        registered.status(201);
        assertThat(registered.text("id")).isNotEqualTo(b.id());
        assertThat(registered.value("onboardingVersion")).isEqualTo(0);
        assertThat(request("GET", AUTH + "/me", b.token(), null).text("email")).isEqualTo(b.email());
    }

    private static String signedToken(String secret, String subject, long expiresIn) {
        return Jwts.builder().issuer("finance-manager-api-test").subject(subject)
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().plusSeconds(expiresIn)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }
}
