-- ─── V13: Budget Tracker ────────────────────────────────────────────────────
-- One budget envelope per event (organiser sets the ceiling).
-- Many line items per budget (planned + actual expenses per category).
-- Revenue is always computed live from the bookings table — never stored here.

CREATE TABLE event_budgets (
    id           uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id     uuid           NOT NULL UNIQUE REFERENCES events (id),
    total_budget numeric(14, 2) NOT NULL,
    notes        text,
    created_by   varchar(12)    NOT NULL REFERENCES users (id),
    created_at   timestamp(6)   NOT NULL DEFAULT now(),
    updated_at   timestamp(6)   NOT NULL DEFAULT now()
);

CREATE TABLE budget_line_items (
    id             uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id      uuid           NOT NULL REFERENCES event_budgets (id) ON DELETE CASCADE,
    category       varchar(30)    NOT NULL,   -- VENUE | CATERING | AV | MARKETING | SECURITY | STAFFING | OTHER
    description    varchar(500)   NOT NULL,
    planned_amount numeric(14, 2) NOT NULL,
    actual_amount  numeric(14, 2),            -- null until expense is paid/confirmed
    status         varchar(20)    NOT NULL DEFAULT 'PLANNED',  -- PLANNED | PAID
    paid_at        timestamp(6),
    created_by     varchar(12)    NOT NULL REFERENCES users (id),
    created_at     timestamp(6)   NOT NULL DEFAULT now(),
    updated_at     timestamp(6)   NOT NULL DEFAULT now()
);

CREATE INDEX idx_budget_line_items_budget_id ON budget_line_items (budget_id);
