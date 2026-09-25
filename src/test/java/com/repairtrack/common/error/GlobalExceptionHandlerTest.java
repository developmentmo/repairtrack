package com.repairtrack.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-20T10:30:00Z");

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler(fixedClock))
                .build();
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingInternalMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.timestamp").value("2026-09-20T10:30:00Z"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.path").value("/test/boom"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void invalidPathVariableTypeReturns400() throws Exception {
        mockMvc.perform(get("/test/items/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.message").value("Parameter 'id' has an invalid value."));
    }

    @Test
    void missingRequestParameterReturns400() throws Exception {
        mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
    }

    @Test
    void unsupportedMethodReturns405WithAllowHeader() throws Exception {
        mockMvc.perform(delete("/test/boom"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void applicationExceptionUsesItsCodeMessageAndCategoryStatus() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("THING_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.message").value("That thing already exists."));
    }

    @Test
    void businessRuleViolationMapsTo422() throws Exception {
        mockMvc.perform(get("/test/rule"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("RULE_BROKEN"));
    }

    @Test
    void accessDeniedFromMethodSecurityMapsTo403() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void everyErrorCategoryHasAStatus() {
        for (ErrorCategory category : ErrorCategory.values()) {
            org.assertj.core.api.Assertions.assertThat(GlobalExceptionHandler.statusOf(category)).isNotNull();
        }
    }

    static class TestException extends ApplicationException {
        TestException(ErrorCategory category, String code, String message) {
            super(category, code, message);
        }
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/conflict")
        String conflict() {
            throw new TestException(ErrorCategory.CONFLICT, "THING_ALREADY_EXISTS", "That thing already exists.");
        }

        @GetMapping("/test/rule")
        String rule() {
            throw new TestException(ErrorCategory.BUSINESS_RULE_VIOLATION, "RULE_BROKEN", "Rule broken.");
        }

        @GetMapping("/test/denied")
        String denied() {
            throw new AccessDeniedException("nope");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new IllegalStateException("secret internal detail");
        }

        @PostMapping("/test/echo")
        Payload echo(@RequestBody Payload payload) {
            return payload;
        }

        @GetMapping("/test/items/{id}")
        String item(@PathVariable("id") long id) {
            return "item " + id;
        }

        @GetMapping("/test/search")
        String search(@RequestParam("q") String query) {
            return query;
        }
    }

    record Payload(String value) {
    }
}
