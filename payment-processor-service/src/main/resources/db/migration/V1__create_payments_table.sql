CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE payments (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key         VARCHAR(255)    UNIQUE NOT NULL,
    amount                  NUMERIC(19,4)   NOT NULL CHECK (amount > 0),
    currency                VARCHAR(3)      NOT NULL,
    payment_method          VARCHAR(20)     NOT NULL,
    status                  VARCHAR(30)     NOT NULL,
    customer_id             VARCHAR(255)    NOT NULL,
    correlation_id          VARCHAR(255),
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
