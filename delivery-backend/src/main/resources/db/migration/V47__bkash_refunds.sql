-- Money sent back to a customer's bKash wallet (bKash Refund API), from an
-- order page or when a return is marked refunded. Failed tries are kept too,
-- so staff can see what bKash answered; only completed ones count as refunded.
CREATE TABLE IF NOT EXISTS payment_refund (
    id                UUID PRIMARY KEY,
    payment_id        UUID NOT NULL REFERENCES payment (id),
    order_id          UUID NOT NULL,
    return_request_id UUID,
    amount            NUMERIC(12, 2) NOT NULL,
    reason            VARCHAR(255) NOT NULL,
    status            VARCHAR(32) NOT NULL,
    refund_trx_id     VARCHAR(128),
    message           VARCHAR(500),
    requested_by      VARCHAR(200),
    created_at        TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_payment_refund_order ON payment_refund (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_refund_payment ON payment_refund (payment_id);
