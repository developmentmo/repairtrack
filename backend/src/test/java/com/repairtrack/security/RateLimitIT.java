package com.repairtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.TestPropertySource;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.ApiTestClient;
import com.repairtrack.ApiTestClient.ApiResponse;
import com.repairtrack.IntegrationTest;

/**
 * Per-IP and per-account limits end-to-end. Runs in its own application context with the per-IP limits switched
 * on (they are off for the other integration tests, which all come from 127.0.0.1).
 */
@IntegrationTest
@TestPropertySource(properties = {
        "repairtrack.rate-limit.enabled=true",
        "repairtrack.rate-limit.login.requests=3",
        "repairtrack.rate-limit.login.per=1m",
        "repairtrack.rate-limit.login-failures-per-account.requests=2",
        "repairtrack.rate-limit.login-failures-per-account.per=15m"
})
class RateLimitIT {

    private static final String PASSWORD = "a long enough password";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void failedLoginsBlockTheAccountAndTooManyRequestsBlockTheIp() {
        ApiTestClient api = new ApiTestClient(port, jsonMapper);
        String email = "ratelimit-" + System.nanoTime() + "@example.com";
        assertThat(api.post("/api/v1/auth/register",
                Map.of("email", email, "password", PASSWORD, "firstName", "Rita", "lastName", "Tester")).status())
                .isEqualTo(201);

        assertThat(login(api, email, "wrong password 1").status()).isEqualTo(401);
        assertThat(login(api, email, "wrong password 2").status()).isEqualTo(401);

        // Per account: blocked even with the right password, with a hint when to retry.
        ApiResponse blockedAccount = login(api, email, PASSWORD);
        assertThat(blockedAccount.status()).isEqualTo(429);
        assertThat(blockedAccount.field("code")).isEqualTo("TOO_MANY_LOGIN_ATTEMPTS");
        assertThat(blockedAccount.headers().firstValue("Retry-After")).isPresent();

        // Per IP: the fourth login call within the minute is refused before it reaches the service.
        ApiResponse blockedIp = login(api, "someone-else@example.com", PASSWORD);
        assertThat(blockedIp.status()).isEqualTo(429);
        assertThat(blockedIp.field("code")).isEqualTo("RATE_LIMITED");
        assertThat(Long.parseLong(blockedIp.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 60L);
    }

    private static ApiResponse login(ApiTestClient api, String email, String password) {
        return api.post("/api/v1/auth/login", Map.of("email", email, "password", password));
    }
}
