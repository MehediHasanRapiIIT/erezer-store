-- A short number a customer can read, type and track an order by (EZ-482915):
-- "EZ-" and six random digits, unique. Existing orders get one here; new orders
-- get one when they are placed (OrderNumbers).
ALTER TABLE orders ADD COLUMN IF NOT EXISTS order_number VARCHAR(20);

DO $$
DECLARE
    r RECORD;
    candidate TEXT;
BEGIN
    FOR r IN SELECT id FROM orders WHERE order_number IS NULL ORDER BY created_at LOOP
        LOOP
            candidate := 'EZ-' || (100000 + floor(random() * 900000))::int::text;
            EXIT WHEN NOT EXISTS (SELECT 1 FROM orders WHERE order_number = candidate);
        END LOOP;
        UPDATE orders SET order_number = candidate WHERE id = r.id;
    END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_order_number ON orders (order_number);
