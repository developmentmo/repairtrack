package com.repairtrack.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.audit.application.AuditQueryService;
import com.repairtrack.audit.domain.AuditAction;
import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.audit.domain.AuditEvent;
import com.repairtrack.security.AuthenticatedUser;

@RestController
class AuditController {

    private final AuditQueryService queryService;
    private final JsonMapper jsonMapper;

    AuditController(AuditQueryService queryService, JsonMapper jsonMapper) {
        this.queryService = queryService;
        this.jsonMapper = jsonMapper;
    }

    /** {@code GET /api/v1/audit-events?entityType=REPAIR_EVENT&entityId=...} (SYSTEM_ADMIN). */
    @GetMapping("/api/v1/audit-events")
    List<AuditEventResponse> forEntity(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @RequestParam("entityType") AuditEntityType entityType,
                                       @RequestParam("entityId") UUID entityId) {
        return queryService.forEntity(actor, entityType, entityId).stream().map(this::toResponse).toList();
    }

    private AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getEntityType(), event.getEntityId(), event.getAction(),
                event.getActorId(), tree(event.getOldValue()), tree(event.getNewValue()), event.getCreatedAt());
    }

    private JsonNode tree(String json) {
        return json == null ? null : jsonMapper.readTree(json);
    }

    public record AuditEventResponse(UUID id, AuditEntityType entityType, UUID entityId, AuditAction action,
                                     UUID actorId, JsonNode oldValue, JsonNode newValue, Instant createdAt) {
    }
}
