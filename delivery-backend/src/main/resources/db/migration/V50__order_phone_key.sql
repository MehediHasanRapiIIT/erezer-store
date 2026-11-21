-- Customer history on the admin order list: every order from the same phone
-- number, however it was typed. "01712-345678", "+8801712345678" and
-- "01712345678" all become the key "1712345678" (the last 10 digits).

-- Older orders from logged-in customers may have no phone of their own: take
-- it from their profile, as new orders already do when they are placed.
UPDATE orders o
   SET customer_phone = u.phone_number
  FROM users u
 WHERE o.client_id = u.id
   AND (o.customer_phone IS NULL OR btrim(o.customer_phone) = '')
   AND u.phone_number IS NOT NULL AND btrim(u.phone_number) <> '';

ALTER TABLE orders ADD COLUMN phone_key VARCHAR(10)
    GENERATED ALWAYS AS (right(regexp_replace(coalesce(customer_phone, ''), '[^0-9]', '', 'g'), 10)) STORED;

CREATE INDEX idx_orders_phone_key ON orders (phone_key) WHERE deleted = false;
