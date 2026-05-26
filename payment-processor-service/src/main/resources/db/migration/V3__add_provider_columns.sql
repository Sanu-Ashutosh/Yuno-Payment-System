ALTER TABLE payments
    ADD COLUMN provider                 VARCHAR(20),
    ADD COLUMN provider_transaction_id  VARCHAR(255),
    ADD COLUMN failure_reason           TEXT,
    ADD COLUMN retry_count              INTEGER DEFAULT 0 NOT NULL,
    ADD COLUMN metadata                 JSONB;
