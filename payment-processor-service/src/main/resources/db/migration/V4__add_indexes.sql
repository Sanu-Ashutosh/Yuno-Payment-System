CREATE INDEX CONCURRENTLY idx_payments_status
    ON payments(status);

CREATE INDEX CONCURRENTLY idx_payments_payment_method
    ON payments(payment_method);

CREATE INDEX CONCURRENTLY idx_payments_customer_id
    ON payments(customer_id);

CREATE INDEX CONCURRENTLY idx_payments_created_at
    ON payments(created_at DESC);

CREATE INDEX CONCURRENTLY idx_payment_events_payment_id
    ON payment_events(payment_id);

CREATE INDEX CONCURRENTLY idx_payment_events_created_at
    ON payment_events(created_at DESC);
