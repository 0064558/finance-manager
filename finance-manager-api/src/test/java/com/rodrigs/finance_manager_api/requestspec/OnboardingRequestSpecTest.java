package com.rodrigs.finance_manager_api.requestspec;

import com.rodrigs.finance_manager_api.support.HttpRequestSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("describe Progresso de onboarding por HTTP")
class OnboardingRequestSpecTest extends HttpRequestSpec {
    @Test
    @DisplayName("it quando avança e repete a versão, inclusive simultaneamente, então persiste sem regredir")
    void advanceAndRepeat() throws Exception {
        var user = user();
        String route = AUTH + "/me/onboarding";
        var body = Map.of("onboardingVersion", 1);
        request("PATCH", route, user.token(), body).status(200);
        request("PATCH", route, user.token(), body).status(200);
        simultaneously(() -> request("PATCH", route, user.token(), body),
                () -> request("PATCH", route, user.token(), body)).forEach(r -> r.status(200));
        assertThat(request("GET", AUTH + "/me", user.token(), null).value("onboardingVersion")).isEqualTo(1);
        request("PATCH", route, user.token(), Map.of("onboardingVersion", 0)).status(409);
        assertThat(request("GET", AUTH + "/me", user.token(), null).value("onboardingVersion")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"onboardingVersion\":null}", "{\"onboardingVersion\":-1}",
            "{\"onboardingVersion\":2}", "{\"onboardingVersion\":0.5}"})
    @DisplayName("it quando a versão é ausente, negativa, futura ou fracionária, então retorna 400 sem alterar progresso")
    void invalidVersion(String body) throws Exception {
        var user = user();
        rawRequest("PATCH", AUTH + "/me/onboarding", user.token(), body).status(400);
        assertThat(request("GET", AUTH + "/me", user.token(), null).value("onboardingVersion")).isEqualTo(0);
    }

    @Test
    @DisplayName("it quando zero e um disputam uma atualização, então a versão final é um")
    void concurrentProgress() throws Exception {
        var user = user();
        var replies = simultaneously(
                () -> request("PATCH", AUTH + "/me/onboarding", user.token(), Map.of("onboardingVersion", 0)),
                () -> request("PATCH", AUTH + "/me/onboarding", user.token(), Map.of("onboardingVersion", 1)));
        replies.get(1).status(200);
        assertThat(replies.get(0).code()).isIn(200, 409);
        assertThat(request("GET", AUTH + "/me", user.token(), null).value("onboardingVersion")).isEqualTo(1);
    }

    @Test
    @DisplayName("it quando A tenta indicar B no corpo, então só o progresso de A muda")
    void ownerFromToken() throws Exception {
        var a = user();
        var b = user();
        request("PATCH", AUTH + "/me/onboarding", a.token(), Map.of("onboardingVersion", 1, "userId", b.id())).status(200);
        assertThat(request("GET", AUTH + "/me", a.token(), null).value("onboardingVersion")).isEqualTo(1);
        assertThat(request("GET", AUTH + "/me", b.token(), null).value("onboardingVersion")).isEqualTo(0);
    }
}
