-- Append-only audit trail. No foreign keys: audit entries must outlive anything they refer to.

CREATE TABLE audit_event (
    id              UUID        NOT NULL,
    -- strict insertion order; several entries of one transaction share the same created_at
    sequence_number BIGINT      GENERATED ALWAYS AS IDENTITY,
    entity_type     VARCHAR(50) NOT NULL,
    entity_id       UUID        NOT NULL,
    action          VARCHAR(60) NOT NULL,
    actor_id        UUID,
    old_value       JSONB,
    new_value       JSONB,
    created_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_audit_event PRIMARY KEY (id)
);

CREATE INDEX ix_audit_event_entity ON audit_event (entity_type, entity_id, sequence_number);
CREATE INDEX ix_audit_event_actor_id ON audit_event (actor_id);

-- Immutability enforced by the database, not only by application code.
CREATE FUNCTION audit_event_is_append_only() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    RAISE EXCEPTION 'audit_event is append-only (% not allowed)', TG_OP;
END;
$$;

CREATE TRIGGER trg_audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION audit_event_is_append_only();
