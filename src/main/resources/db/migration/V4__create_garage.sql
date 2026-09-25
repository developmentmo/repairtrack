-- Garages (business locations). kvk_number is intentionally NOT unique: one KvK registration
-- can have several branches (vestigingen). Duplicate or fraudulent registrations are caught
-- by the manual verification step.

CREATE TABLE garage (
    id                      UUID         NOT NULL,
    name                    VARCHAR(150) NOT NULL,
    kvk_number              VARCHAR(8)   NOT NULL,
    address                 VARCHAR(200) NOT NULL,
    postal_code             VARCHAR(7)   NOT NULL,
    city                    VARCHAR(100) NOT NULL,
    phone                   VARCHAR(30),
    email                   VARCHAR(254),
    verification_status     VARCHAR(20)  NOT NULL,
    verification_changed_at TIMESTAMPTZ  NOT NULL,
    verification_changed_by UUID         NOT NULL,
    verification_note       VARCHAR(500),
    created_by              UUID         NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    version                 BIGINT       NOT NULL,
    CONSTRAINT pk_garage PRIMARY KEY (id),
    CONSTRAINT fk_garage_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_garage_verification_changed_by FOREIGN KEY (verification_changed_by) REFERENCES app_user (id),
    CONSTRAINT ck_garage_kvk_number CHECK (kvk_number ~ '^[0-9]{8}$'),
    CONSTRAINT ck_garage_postal_code CHECK (postal_code ~ '^[1-9][0-9]{3} [A-Z]{2}$'),
    CONSTRAINT ck_garage_verification_status
        CHECK (verification_status IN ('UNVERIFIED', 'PENDING', 'VERIFIED', 'SUSPENDED'))
);

CREATE INDEX ix_garage_kvk_number ON garage (kvk_number);
-- admin review queue: "all PENDING garages"
CREATE INDEX ix_garage_verification_status ON garage (verification_status);
