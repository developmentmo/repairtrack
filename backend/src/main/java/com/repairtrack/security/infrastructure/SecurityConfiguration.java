package com.repairtrack.security.infrastructure;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.repairtrack.common.ratelimit.FixedWindowRateLimiter;
import com.repairtrack.security.application.LoginSessionService;
import com.repairtrack.security.application.LoginThrottle;

/**
 * HTTP security: stateless JWT bearer authentication, deny-by-default.
 * <p>
 * This layer only decides "authenticated or not" (plus a few public endpoints). Business
 * authorization ("may this user modify this vehicle?") is done explicitly in application
 * services, never by URL patterns.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CorsProperties.class, RateLimitProperties.class})
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            UserRepository userRepository,
                                            LoginSessionService loginSessionService,
                                            SecurityErrorHandler securityErrorHandler,
                                            RateLimitProperties rateLimits,
                                            Clock clock) throws Exception {
        var authenticatedUserConverter = new AuthenticatedUserConverter(userRepository, loginSessionService);
        http
                // No cookies/sessions are used for authentication, so CSRF protection does not apply.
                .csrf(csrf -> csrf.disable())
                // Browser clients (Flutter web) from the configured origins only; uses corsConfigurationSource().
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password").permitAll()
                        // Public vehicle history (Phase 7) lives exclusively under this prefix.
                        .requestMatchers(HttpMethod.GET, "/api/v1/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // Boot's error dispatch must not turn real errors into 401s.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticatedUserConverter))
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler))
                // Share tokens are in URLs: never leak them to other sites through the Referer header.
                .headers(headers -> headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorHandler)
                        .accessDeniedHandler(securityErrorHandler));
        if (rateLimits.enabled()) {
            http.addFilterBefore(new RateLimitFilter(rateLimits, clock, securityErrorHandler),
                    BearerTokenAuthenticationFilter.class);
        }
        return http.build();
    }

    /**
     * CORS for the web app. Bearer tokens travel in the Authorization header (no cookies), so credentials are not
     * allowed. Preflight answers are cached by browsers for an hour.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        var configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    LoginThrottle loginThrottle(RateLimitProperties rateLimits, Clock clock) {
        var limit = rateLimits.loginFailuresPerAccount();
        return new LoginThrottle(new FixedWindowRateLimiter(limit.requests(), limit.per(), clock));
    }

    /**
     * Delegating encoder: new hashes use the current default (BCrypt), stored hashes carry their
     * algorithm prefix (e.g. {bcrypt}) so the algorithm can be upgraded (e.g. to Argon2) without
     * invalidating existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
