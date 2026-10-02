-- One row per corrected field: original value, corrected value, reason, who, on behalf of which garage, when.

CREATE TABLE repair_correction (
    id                     UUID          NOT NULL,
    repair_event_id        UUID          NOT NULL,
    field                  VARCHAR(30)   NOT NULL,
    old_value              VARCHAR(5000),
    new_value              VARCHAR(5000),
    reason                 VARCHAR(500)  NOT NULL,
    corrected_by           UUID          NOT NULL,
    corrected_by_garage_id UUID,
    created_at             TIMESTAMPTZ   NOT NULL,
    CONSTRAINT pk_repair_correction PRIMARY KEY (id),
    CONSTRAINT fk_repair_correction_repair_event_id FOREIGN KEY (repair_event_id) REFERENCES repair_event (id),
    CONSTRAINT fk_repair_correction_corrected_by FOREIGN KEY (corrected_by) REFERENCES app_user (id),
    CONSTRAINT fk_repair_correction_corrected_by_garage_id FOREIGN KEY (corrected_by_garage_id) REFERENCES garage (id),
    CONSTRAINT ck_repair_correction_field
        CHECK (field IN ('EVENT_TYPE', 'EVENT_DATE', 'MILEAGE', 'TITLE', 'DESCRIPTION'))
);

CREATE INDEX ix_repair_correction_repair_event_id ON repair_correction (repair_event_id);
