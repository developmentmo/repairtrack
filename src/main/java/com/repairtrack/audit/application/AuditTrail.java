package com.repairtrack.audit.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.repairtrack.audit.domain.AuditAction;
import com.repairtrack.audit.domain.AuditEntityType;
import com.repairtrack.audit.domain.AuditEvent;
import com.repairtrack.audit.infrastructure.AuditEventRepository;

/** Writes audit entries inside the caller's transaction. */
@Component
class AuditTrail {

    private final AuditEventRepository repository;
    private final JsonMapper jsonMapper;

    AuditTrail(AuditEventRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
    }

    void record(AuditEntityType type, UUID entityId, AuditAction action, UUID actorId,
                Map<String, Object> oldValue, Map<String, Object> newValue, Instant at) {
        repository.save(AuditEvent.of(type, entityId, action, actorId, json(oldValue), json(newValue), at));
    }

    /** Ordered map that, unlike Map.of, accepts null values: {@code values("a", 1, "b", null)}. */
    static Map<String, Object> values(Object... keysAndValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return map;
    }

    private String json(Map<String, Object> value) {
        return value == null ? null : jsonMapper.writeValueAsString(value);
    }
}
