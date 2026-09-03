ALTER TABLE payments ADD COLUMN stripe_session_id VARCHAR(255);

CREATE UNIQUE INDEX idx_payments_stripe_session_id
    ON payments (stripe_session_id)
    WHERE stripe_session_id IS NOT NULL;
