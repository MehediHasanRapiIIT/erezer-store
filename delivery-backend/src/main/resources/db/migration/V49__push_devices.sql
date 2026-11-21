-- Phones running the Erezer app that can get order notifications (Firebase).
-- A phone belongs to a customer while they are logged in on it; a guest's
-- phone is remembered on the order it placed (orders.push_token).
CREATE TABLE push_device (
    token       VARCHAR(512) PRIMARY KEY,
    user_id     UUID         NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT now()
);
CREATE INDEX idx_push_device_user ON push_device (user_id);

ALTER TABLE orders ADD COLUMN push_token VARCHAR(512);
