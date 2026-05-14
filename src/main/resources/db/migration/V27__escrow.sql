CREATE TABLE escrow_accounts (
    id                UUID         NOT NULL PRIMARY KEY,
    contract_id       UUID         NOT NULL UNIQUE REFERENCES vendor_contracts(id),
    total_amount      NUMERIC(14,2) NOT NULL,
    released_amount   NUMERIC(14,2) NOT NULL DEFAULT 0,
    status            VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    funded_at         TIMESTAMP,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE escrow_milestones (
    id              UUID         NOT NULL PRIMARY KEY,
    escrow_id       UUID         NOT NULL REFERENCES escrow_accounts(id),
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    amount          NUMERIC(14,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    display_order   INT          NOT NULL DEFAULT 0,
    approved_at     TIMESTAMP,
    released_at     TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_em_escrow_id ON escrow_milestones (escrow_id);
