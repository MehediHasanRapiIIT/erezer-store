-- ============================================================================
-- Demo order history for the admin reports, dashboard and analytics pages.
-- ============================================================================
-- Generates ~2,000 realistic Bangladeshi apparel orders from 1 July 2025
-- (start of FY 2025-26) up to today, so every report type has data:
--
--   * Dhaka-local ordering hours (lunch and late-evening peaks), Friday /
--     Saturday weekend pattern, a steady growth trend.
--   * Seasonality: Ramadan / Eid-ul-Fitr ramp, Pohela Boishakh, Eid-ul-Adha,
--     Durga Puja, 11.11 sale, winter-wear lift, and the dead days right after
--     each Eid when couriers are closed.
--   * Cash on delivery for most orders, then bKash, then card.
--   * Inside-Dhaka ৳60 / outside-Dhaka ৳120 shipping, free above ৳2,999,
--     coupons on a minority of orders.
--   * Realistic outcomes: ~88 % delivered, ~9 % cancelled, ~3 % returned for
--     settled orders, and a live mix of open statuses for the last week.
--   * 80 registered customers with Bangladeshi names who join over time, so
--     "new customers" and lifetime value mean something.
--
-- Every row is tagged with an @demo.erezer.local email, and the script deletes
-- those rows first, so it is idempotent. Timestamps are written as UTC
-- wall-clock (Dhaka − 6 h), the same representation the backend uses.
--
-- Run (from the repo root, stack up):
--   docker compose exec -T postgres psql -U postgres -d delivery_app_v1 \
--       < deploy/seed_demo_orders.sql
--
-- Remove again:
--   docker compose exec -T postgres psql -U postgres -d delivery_app_v1 \
--       -c "DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE customer_email LIKE '%@demo.erezer.local');" \
--       -c "DELETE FROM order_item WHERE order_id IN (SELECT id FROM orders WHERE customer_email LIKE '%@demo.erezer.local');" \
--       -c "DELETE FROM orders WHERE customer_email LIKE '%@demo.erezer.local';" \
--       -c "DELETE FROM users WHERE email LIKE '%@demo.erezer.local';"
-- ============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- Deterministic: the same history every time the script runs.
SELECT setseed(0.4242);

-- ── 1. wipe any previous demo history ───────────────────────────────────────
DELETE FROM order_status_history
 WHERE order_id IN (SELECT id FROM orders WHERE customer_email LIKE '%@demo.erezer.local');
DELETE FROM order_item
 WHERE order_id IN (SELECT id FROM orders WHERE customer_email LIKE '%@demo.erezer.local');
DELETE FROM orders WHERE customer_email LIKE '%@demo.erezer.local';
DELETE FROM users  WHERE email LIKE '%@demo.erezer.local';

-- ── 2. generate ─────────────────────────────────────────────────────────────
DO $$
DECLARE
    -- Dhaka is UTC+6 all year (no daylight saving).
    c_offset      CONSTANT interval := interval '6 hours';
    c_start       CONSTANT date := DATE '2025-07-01';
    c_customers   CONSTANT int  := 80;

    v_now_utc     timestamp := (now() AT TIME ZONE 'UTC');
    v_today_local date      := (now() AT TIME ZONE 'UTC' + interval '6 hours')::date;
    v_span_days   int;

    v_first text[] := ARRAY['Rahim','Karim','Fatema','Nusrat','Tanvir','Sadia','Mehedi','Rafi','Jannat','Arif',
                            'Sumaiya','Imran','Tasnim','Shakib','Mim','Rakib','Nabila','Fahim','Sharmin','Sabbir',
                            'Ayesha','Hasan','Farhana','Jubayer','Mahmuda','Tamim','Ritu','Nayeem','Lamia','Shanto',
                            'Rumana','Sohel','Priya','Asif','Shila','Mahin','Tanjila','Rony','Bristi','Zahid'];
    v_last  text[] := ARRAY['Ahmed','Hossain','Islam','Rahman','Khan','Chowdhury','Akter','Begum','Sultana','Mia',
                            'Sheikh','Sarkar','Bhuiyan','Talukder','Haque','Uddin','Siddique','Karim','Mollah','Das'];
    -- Address, and whether it is inside Dhaka city (৳60 vs ৳120 delivery).
    v_areas text[] := ARRAY['House 12, Road 7, Dhanmondi, Dhaka-1205','Road 11, Block F, Gulshan 1, Dhaka-1212',
                            'Section 10, Mirpur, Dhaka-1216','Sector 7, Uttara, Dhaka-1230','Road 5, Banani, Dhaka-1213',
                            'Tajmahal Road, Mohammadpur, Dhaka-1207','Block C, Bashundhara R/A, Dhaka-1229',
                            'Dilkusha, Motijheel, Dhaka-1000','Middle Badda, Dhaka-1212','Khilgaon Taltola, Dhaka-1219',
                            'Wari, Old Dhaka, Dhaka-1203','Shantinagar, Dhaka-1217',
                            'Agrabad C/A, Chattogram-4100','GEC Circle, Chattogram-4000','Zindabazar, Sylhet-3100',
                            'Sonadanga, Khulna-9100','Shaheb Bazar, Rajshahi-6100','Chashara, Narayanganj-1400',
                            'Tongi Bazar, Gazipur-1710','Kandirpar, Cumilla-3500','Sadar Road, Barishal-8200',
                            'Ganginar Par, Mymensingh-2200','Shapla Chattar, Rangpur-5400','Bogura Sadar, Bogura-5800'];
    v_dhaka_areas CONSTANT int := 12; -- first N entries are inside Dhaka

    -- Ordering hour weights, Dhaka local, 00:00 … 23:00.
    v_hour_w numeric[] := ARRAY[0.5,0.3,0.2,0.1,0.1,0.2,0.4,0.8,1.2,1.6,2.0,2.4,
                                2.6,2.4,2.0,1.8,2.0,2.4,2.8,3.2,3.6,3.4,2.4,1.2];
    v_hour_total numeric := 0;

    -- Customers
    v_cust_id      uuid[]   := '{}';
    v_cust_name    text[]   := '{}';
    v_cust_email   text[]   := '{}';
    v_cust_phone   text[]   := '{}';
    v_cust_addr    text[]   := '{}';
    v_cust_dhaka   boolean[]:= '{}';
    v_cust_joined  date[]   := '{}';

    -- Catalogue
    v_prod_id    bigint[];
    v_prod_price numeric[];
    v_prod_count int;

    -- loop state
    d            date;
    v_age        int;
    v_rate       numeric;
    v_mult       numeric;
    v_count      int;
    v_eligible   int;
    ci           int;
    i            int;
    j            int;
    k            int;
    r            numeric;
    acc          numeric;
    v_hour       int;
    v_local_ts   timestamp;
    v_created    timestamp;
    v_order_id   uuid;
    v_lines      int;
    v_qty        int;
    v_pidx       int;
    v_unit       numeric;
    v_subtotal   numeric;
    v_discount   numeric;
    v_shipping   numeric;
    v_total      numeric;
    v_coupon     text;
    v_payment    text;
    v_status     text;
    v_delivered  timestamp;
    v_cancelled  timestamp;
    v_final_at   timestamp;
    v_changed_by text;
    v_reason     text;
    v_in_eid     boolean;
BEGIN
    v_span_days := v_today_local - c_start;
    IF v_span_days < 30 THEN
        RAISE EXCEPTION 'Clock looks wrong: today (%) is less than 30 days after %', v_today_local, c_start;
    END IF;

    FOR i IN 1..24 LOOP v_hour_total := v_hour_total + v_hour_w[i]; END LOOP;

    -- Catalogue: every live product at its current selling price.
    SELECT array_agg(id ORDER BY id), array_agg(COALESCE(discount_price, price) ORDER BY id), COUNT(*)
      INTO v_prod_id, v_prod_price, v_prod_count
      FROM product
     WHERE COALESCE(deleted, false) = false AND price IS NOT NULL AND price > 0;
    IF v_prod_count IS NULL OR v_prod_count = 0 THEN
        RAISE EXCEPTION 'No products found - run deploy/seed_demo_data.py first';
    END IF;

    -- Customers join spread over the first 85 % of the span, earliest first,
    -- so later months have more customers to draw from.
    FOR i IN 1..c_customers LOOP
        v_cust_id    := v_cust_id    || gen_random_uuid();
        v_cust_name  := v_cust_name  || (v_first[1 + (i - 1) % array_length(v_first, 1)] || ' ' ||
                                         v_last[1 + ((i * 7) % array_length(v_last, 1))]);
        v_cust_email := v_cust_email || (lower(v_first[1 + (i - 1) % array_length(v_first, 1)]) || '.' ||
                                         lower(v_last[1 + ((i * 7) % array_length(v_last, 1))]) || i ||
                                         '@demo.erezer.local');
        v_cust_phone := v_cust_phone || ('+88017' || lpad((10000000 + floor(random() * 89999999))::bigint::text, 8, '0'));
        k := 1 + floor(random() * array_length(v_areas, 1))::int;
        v_cust_addr  := v_cust_addr  || v_areas[k];
        v_cust_dhaka := v_cust_dhaka || (k <= v_dhaka_areas);
        v_cust_joined := v_cust_joined || (c_start + floor(power((i - 1)::numeric / c_customers, 1.0) * v_span_days * 0.85)::int);
    END LOOP;

    FOR i IN 1..c_customers LOOP
        INSERT INTO users (id, created_at, updated_at, deleted, version, email, first_name, last_name,
                           phone_number, is_active, email_verified)
        VALUES (v_cust_id[i],
                (v_cust_joined[i] + time '10:00') - c_offset,
                (v_cust_joined[i] + time '10:00') - c_offset,
                false, 0, v_cust_email[i],
                split_part(v_cust_name[i], ' ', 1), split_part(v_cust_name[i], ' ', 2),
                v_cust_phone[i], true, true);
    END LOOP;

    -- ── one day at a time ────────────────────────────────────────────────
    d := c_start;
    WHILE d <= v_today_local LOOP
        -- Weekday base rate (Sunday = 0). Friday evening is the big shopping night.
        v_rate := CASE EXTRACT(DOW FROM d)::int
                      WHEN 0 THEN 4.0 WHEN 1 THEN 4.2 WHEN 2 THEN 4.0 WHEN 3 THEN 4.1
                      WHEN 4 THEN 4.5 WHEN 5 THEN 5.5 ELSE 5.0 END;

        -- Growth trend: 0.7× at the start of FY 2025-26 rising to 1.3× today.
        v_mult := 0.7 + 0.6 * ((d - c_start)::numeric / v_span_days);

        v_in_eid := false;
        -- Ramadan 2026 (≈18 Feb – 19 Mar) with Eid-ul-Fitr ≈ 20 Mar 2026.
        IF d BETWEEN DATE '2026-02-18' AND DATE '2026-03-19' THEN
            v_mult := v_mult * (1.4 + 1.8 * ((d - DATE '2026-02-18')::numeric / 30)); v_in_eid := true;
        ELSIF d BETWEEN DATE '2026-03-20' AND DATE '2026-03-23' THEN
            v_mult := v_mult * 0.25;                       -- Eid holidays, couriers closed
        -- Pohela Boishakh 14 Apr 2026.
        ELSIF d BETWEEN DATE '2026-04-01' AND DATE '2026-04-13' THEN
            v_mult := v_mult * 1.6;
        ELSIF d = DATE '2026-04-14' THEN
            v_mult := v_mult * 0.6;
        -- Eid-ul-Adha ≈ 27 May 2026.
        ELSIF d BETWEEN DATE '2026-05-06' AND DATE '2026-05-26' THEN
            v_mult := v_mult * (1.3 + 0.9 * ((d - DATE '2026-05-06')::numeric / 21)); v_in_eid := true;
        ELSIF d BETWEEN DATE '2026-05-27' AND DATE '2026-05-30' THEN
            v_mult := v_mult * 0.25;
        -- Durga Puja 2025 (≈ 28 Sep – 2 Oct).
        ELSIF d BETWEEN DATE '2025-09-18' AND DATE '2025-09-30' THEN
            v_mult := v_mult * 1.3;
        -- 11.11 sale.
        ELSIF d BETWEEN DATE '2025-11-09' AND DATE '2025-11-12' THEN
            v_mult := v_mult * 2.2;
        -- Winter wear lift.
        ELSIF EXTRACT(MONTH FROM d) IN (12, 1) THEN
            v_mult := v_mult * 1.3;
        END IF;

        v_count := floor(v_rate * v_mult * (0.6 + random() * 0.8))::int;
        v_age := v_today_local - d;

        -- Customers who had joined by this day.
        v_eligible := 0;
        FOR i IN 1..c_customers LOOP
            IF v_cust_joined[i] <= d THEN v_eligible := i; END IF;
        END LOOP;
        IF v_eligible = 0 THEN v_eligible := 1; END IF;

        FOR j IN 1..v_count LOOP
            -- Skewed: a loyal core places most orders.
            ci := 1 + floor(power(random(), 1.6) * v_eligible)::int;
            IF ci > v_eligible THEN ci := v_eligible; END IF;

            -- Hour of day from the weight table.
            r := random() * v_hour_total; acc := 0; v_hour := 23;
            FOR i IN 1..24 LOOP
                acc := acc + v_hour_w[i];
                IF r < acc THEN v_hour := i - 1; EXIT; END IF;
            END LOOP;
            v_local_ts := d + make_interval(hours => v_hour, mins => floor(random() * 60)::int,
                                            secs => floor(random() * 60)::int);
            v_created := v_local_ts - c_offset;
            IF v_created > v_now_utc THEN CONTINUE; END IF;   -- later today: not placed yet

            v_order_id := gen_random_uuid();

            -- Lines.
            r := random();
            v_lines := CASE WHEN r < 0.65 THEN 1 WHEN r < 0.90 THEN 2 ELSE 3 END;
            v_subtotal := 0;
            FOR k IN 1..v_lines LOOP
                v_pidx := 1 + floor(random() * v_prod_count)::int;
                IF v_pidx > v_prod_count THEN v_pidx := v_prod_count; END IF;
                r := random();
                v_qty := CASE WHEN r < 0.80 THEN 1 WHEN r < 0.95 THEN 2 ELSE 3 END;
                v_unit := v_prod_price[v_pidx];
                v_subtotal := v_subtotal + v_unit * v_qty;
                INSERT INTO order_item (id, created_at, updated_at, deleted, version, order_id, product_id,
                                        quantity, price_at_order)
                VALUES (gen_random_uuid(), v_created, v_created, false, 0, v_order_id, v_prod_id[v_pidx],
                        v_qty, v_unit);
            END LOOP;

            -- Shipping: ৳60 inside Dhaka, ৳120 outside, free from ৳2,999.
            v_shipping := CASE WHEN v_subtotal >= 2999 THEN 0
                               WHEN v_cust_dhaka[ci] THEN 60 ELSE 120 END;

            -- Coupons on a minority of orders.
            v_coupon := NULL; v_discount := 0;
            r := random();
            IF v_in_eid AND r < 0.30 THEN
                v_coupon := 'EID10'; v_discount := round(v_subtotal * 0.10, 2);
            ELSIF r < 0.08 THEN
                v_coupon := 'SAVE10'; v_discount := round(v_subtotal * 0.10, 2);
            ELSIF r < 0.13 THEN
                v_coupon := 'WELCOME100'; v_discount := LEAST(100, v_subtotal);
            ELSIF r < 0.16 THEN
                v_coupon := 'FREESHIP'; v_shipping := 0;
            END IF;

            v_total := v_subtotal - v_discount + v_shipping;
            IF v_total < 0 THEN v_total := 0; END IF;

            r := random();
            v_payment := CASE WHEN r < 0.68 THEN 'CASH' WHEN r < 0.93 THEN 'BKASH' ELSE 'CARD' END;

            -- Outcome by age.
            r := random();
            IF v_age >= 8 THEN
                v_status := CASE WHEN r < 0.88 THEN 'DELIVERED' WHEN r < 0.97 THEN 'CANCELLED' ELSE 'RETURNED' END;
            ELSIF v_age >= 3 THEN
                v_status := CASE WHEN r < 0.55 THEN 'DELIVERED' WHEN r < 0.65 THEN 'OUT_FOR_DELIVERY'
                                 WHEN r < 0.75 THEN 'SHIPPED' WHEN r < 0.85 THEN 'PROCESSING'
                                 WHEN r < 0.95 THEN 'CANCELLED' ELSE 'RETURNED' END;
            ELSE
                v_status := CASE WHEN r < 0.30 THEN 'PLACED' WHEN r < 0.50 THEN 'ACCEPTED'
                                 WHEN r < 0.65 THEN 'IN_PRODUCTION' WHEN r < 0.80 THEN 'PROCESSING'
                                 WHEN r < 0.90 THEN 'SHIPPED' WHEN r < 0.98 THEN 'CANCELLED' ELSE 'DELIVERED' END;
            END IF;

            v_delivered := NULL; v_cancelled := NULL; v_reason := NULL; v_changed_by := 'admin';
            IF v_status IN ('DELIVERED', 'RETURNED') THEN
                -- Inside Dhaka next day or the day after; outside 2–5 days.
                v_delivered := v_created + make_interval(
                    days => CASE WHEN v_cust_dhaka[ci] THEN 1 + floor(random() * 2)::int
                                 ELSE 2 + floor(random() * 4)::int END,
                    hours => 9 + floor(random() * 10)::int);
                IF v_delivered > v_now_utc - interval '1 hour' THEN
                    v_delivered := v_now_utc - interval '1 hour';
                END IF;
                v_final_at := v_delivered;
            ELSIF v_status = 'CANCELLED' THEN
                v_cancelled := v_created + make_interval(mins => 20 + floor(random() * 2800)::int);
                IF v_cancelled > v_now_utc - interval '10 minutes' THEN
                    v_cancelled := v_now_utc - interval '10 minutes';
                END IF;
                v_final_at := v_cancelled;
                IF random() < 0.6 THEN
                    v_changed_by := 'customer';
                    v_reason := (ARRAY['Changed my mind','Ordered the wrong size','Found it cheaper elsewhere',
                                       'Delivery would take too long'])[1 + floor(random() * 4)::int];
                ELSE
                    v_reason := (ARRAY['Customer unreachable on phone','Address incomplete',
                                       'Out of stock in requested size'])[1 + floor(random() * 3)::int];
                END IF;
            ELSIF v_status = 'PLACED' THEN
                v_final_at := NULL;
            ELSE
                v_final_at := v_created + make_interval(hours => 2 + floor(random() * 40)::int);
                IF v_final_at > v_now_utc - interval '10 minutes' THEN
                    v_final_at := v_now_utc - interval '10 minutes';
                END IF;
            END IF;

            INSERT INTO orders (id, created_at, updated_at, deleted, version,
                                client_id, customer_name, customer_email, customer_phone, delivery_address,
                                subtotal_amount, discount_amount, coupon_code, shipping_fee, delivery_charge,
                                tax_amount, total_amount, payment_method, order_status,
                                delivered_at, cancelled_at, cancellation_reason,
                                courier_name, tracking_number)
            VALUES (v_order_id, v_created, COALESCE(v_final_at, v_created), false, 0,
                    v_cust_id[ci], v_cust_name[ci], v_cust_email[ci], v_cust_phone[ci], v_cust_addr[ci],
                    v_subtotal, v_discount, v_coupon, v_shipping, v_shipping,
                    0, v_total, v_payment, v_status,
                    v_delivered, v_cancelled, v_reason,
                    CASE WHEN v_status IN ('SHIPPED','OUT_FOR_DELIVERY','DELIVERED','RETURNED')
                         THEN (ARRAY['Pathao Courier','Steadfast','RedX','Sundarban Courier'])[1 + floor(random() * 4)::int] END,
                    CASE WHEN v_status IN ('SHIPPED','OUT_FOR_DELIVERY','DELIVERED','RETURNED')
                         THEN 'BD' || lpad(floor(random() * 99999999)::bigint::text, 8, '0') END);

            -- Status trail: placed, then the final state.
            INSERT INTO order_status_history (id, created_at, updated_at, deleted, version, order_id,
                                              from_status, to_status, changed_by, note)
            VALUES (gen_random_uuid(), v_created, v_created, false, 0, v_order_id, NULL, 'PLACED', 'customer', NULL);
            IF v_final_at IS NOT NULL THEN
                INSERT INTO order_status_history (id, created_at, updated_at, deleted, version, order_id,
                                                  from_status, to_status, changed_by, note)
                VALUES (gen_random_uuid(), v_final_at, v_final_at, false, 0, v_order_id, 'PLACED', v_status,
                        v_changed_by, v_reason);
            END IF;
        END LOOP;

        d := d + 1;
    END LOOP;
END $$;

-- ── 3. report what was written ──────────────────────────────────────────────
SELECT 'demo customers' AS what, COUNT(*)::text AS n FROM users WHERE email LIKE '%@demo.erezer.local'
UNION ALL
SELECT 'demo orders', COUNT(*)::text FROM orders WHERE customer_email LIKE '%@demo.erezer.local'
UNION ALL
SELECT 'demo order lines', COUNT(*)::text FROM order_item oi
  JOIN orders o ON o.id = oi.order_id WHERE o.customer_email LIKE '%@demo.erezer.local'
UNION ALL
SELECT 'first order (UTC)', MIN(created_at)::text FROM orders WHERE customer_email LIKE '%@demo.erezer.local'
UNION ALL
SELECT 'last order (UTC)', MAX(created_at)::text FROM orders WHERE customer_email LIKE '%@demo.erezer.local'
UNION ALL
SELECT 'status ' || order_status, COUNT(*)::text FROM orders
 WHERE customer_email LIKE '%@demo.erezer.local' GROUP BY order_status ORDER BY 1;

COMMIT;
