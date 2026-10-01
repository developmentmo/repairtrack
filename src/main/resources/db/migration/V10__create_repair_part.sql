CREATE TABLE repair_part (
    id              UUID         NOT NULL,
    repair_event_id UUID         NOT NULL,
    part_number     VARCHAR(100),
    brand           VARCHAR(100),
    description     VARCHAR(500) NOT NULL,
    quantity        INTEGER      NOT NULL,
    added_by        UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_repair_part PRIMARY KEY (id),
    CONSTRAINT fk_repair_part_repair_event_id FOREIGN KEY (repair_event_id) REFERENCES repair_event (id),
    CONSTRAINT fk_repair_part_added_by FOREIGN KEY (added_by) REFERENCES app_user (id),
    CONSTRAINT ck_repair_part_quantity CHECK (quantity BETWEEN 1 AND 999)
);

CREATE INDEX ix_repair_part_repair_event_id ON repair_part (repair_event_id);
