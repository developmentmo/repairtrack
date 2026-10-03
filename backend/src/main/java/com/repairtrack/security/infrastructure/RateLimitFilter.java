package com.repairtrack.security.infrastructure;

import java.io.IOException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.repairtrack.common.ratelimit.FixedWindowRateLimiter;
import com.repairtrack.common.ratelimit.FixedWindowRateLimiter.Decision;
import com.repairtrack.security.infrastructure.RateLimitProperties.Limit;

/**
 * Per-IP limits on endpoints that can be abused without an account: login, register, refresh, claim (VIN
 * guessing) and the public share-link report (scraping). Over the limit: {@code 429 RATE_LIMITED} with
 * {@code Retry-After}. The client IP is {@link HttpServletRequest#getRemoteAddr()}; behind a reverse proxy set
 * {@code FORWARD_HEADERS_STRATEGY=framework} so it is the real client, not the proxy.
 */
final class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern CLAIM = Pattern.compile("^/api/v1/vehicles/[^/]+/claim$");

    enum Rule {
        LOGIN("POST", path -> path.equals("/api/v1/auth/login")),
        REGISTER("POST", path -> path.equals("/api/v1/auth/register")),
        REFRESH("POST", path -> path.equals("/api/v1/auth/refresh")),
        CLAIM_VEHICLE("POST", path -> CLAIM.matcher(path).matches()),
        PUBLIC_REPORT("GET", path -> path.startsWith("/api/v1/public/"));

        private final String method;
        private final Predicate<String> path;

        Rule(String method, Predicate<String> path) {
            this.method = method;
            this.path = path;
        }

        boolean matches(HttpServletRequest request) {
            return method.equals(request.getMethod()) && path.test(request.getRequestURI());
        }
    }

    private final Map<Rule, FixedWindowRateLimiter> limiters = new EnumMap<>(Rule.class);
    private final SecurityErrorHandler errors;

    RateLimitFilter(RateLimitProperties properties, Clock clock, SecurityErrorHandler errors) {
        this.errors = errors;
        limiters.put(Rule.LOGIN, limiter(properties.login(), clock));
        limiters.put(Rule.REGISTER, limiter(properties.register(), clock));
        limiters.put(Rule.REFRESH, limiter(properties.refresh(), clock));
        limiters.put(Rule.CLAIM_VEHICLE, limiter(properties.claim(), clock));
        limiters.put(Rule.PUBLIC_REPORT, limiter(properties.publicReport(), clock));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        for (Rule rule : Rule.values()) {
            if (rule.matches(request)) {
                Decision decision = limiters.get(rule).tryAcquire(request.getRemoteAddr());
                if (!decision.allowed()) {
                    errors.tooManyRequests(request, response, decision.retryAfter());
                    return;
                }
                break;
            }
        }
        chain.doFilter(request, response);
    }

    private static FixedWindowRateLimiter limiter(Limit limit, Clock clock) {
        return new FixedWindowRateLimiter(limit.requests(), limit.per(), clock);
    }
}
