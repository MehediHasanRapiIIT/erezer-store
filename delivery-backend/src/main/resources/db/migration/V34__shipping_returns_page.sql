-- A page behind the footer's "Shipping & Returns" link.
--
-- The link pointed at /shipping, which was never a page, so it dropped the
-- customer on the home page. It now opens a page of the shop's own, written
-- from what the shop already tells customers elsewhere (delivery across
-- Bangladesh in 2-4 days, the charge shown at checkout, cash on delivery and
-- bKash, returns within 3 days of delivery). The shop can change every word of
-- it under Pages in the admin panel.

INSERT INTO content_page (slug, title, eyebrow, intro, sections_json, closing, cta_label, cta_link, show_in_footer, is_active, sort_order, created_at, updated_at, deleted, version)
SELECT 'shipping-returns', 'Shipping & Returns', 'EREZER',
$txt$Everything you need to know about getting your order, and what to do if it isn't right.

We deliver across Bangladesh, and we want you to be happy with what arrives. If something is wrong, tell us and we will sort it out.$txt$,
$json$[
 {"heading":"Where We Deliver","body":"We deliver nationwide: inside Dhaka and to every district outside Dhaka.","imageUrl":null},
 {"heading":"Delivery Charge","body":"The delivery charge depends on your area. You see the exact charge at checkout, before you place the order, so there are no surprises.","imageUrl":null},
 {"heading":"Delivery Time","body":"Most orders arrive within 2 to 4 days. Deliveries outside Dhaka, and orders placed around holidays or sale events, can take a little longer.","imageUrl":null},
 {"heading":"Payment","body":"Pay with cash on delivery, or pay online with bKash when you place the order.","imageUrl":null},
 {"heading":"Tracking Your Order","body":"After you order you get an order number. Enter it on the Track Order page at any time to see where your order is.","imageUrl":null},
 {"heading":"Returns & Exchanges","body":"Changed your mind? Tell us within 3 days of delivery to start a return or exchange. Items must be unworn, unwashed and have their original tags attached.","imageUrl":null},
 {"heading":"How to Start a Return","body":"Contact our support team with your order number and what you would like to return or exchange. We will arrange the pickup and guide you through the rest.","imageUrl":null},
 {"heading":"Wrong or Damaged Item","body":"If you received the wrong item, or it arrived damaged, contact us as soon as you can with your order number and a photo. We will replace it or refund you.","imageUrl":null}
]$json$,
$txt$Still have a question? We are happy to help.$txt$,
'Contact us', '/contact', TRUE, TRUE, 2, now(), now(), FALSE, 0
WHERE NOT EXISTS (SELECT 1 FROM content_page WHERE LOWER(slug) = 'shipping-returns');

-- The footer link keeps its words and its place; only where it goes changes.
UPDATE store_settings
SET footer_json = jsonb_set(footer_json::jsonb, '{columns}', (
        SELECT COALESCE(jsonb_agg(jsonb_set(col, '{links}', (
                SELECT COALESCE(jsonb_agg(CASE WHEN l ->> 'url' = '/shipping'
                           THEN jsonb_set(l, '{url}', '"/pages/shipping-returns"'::jsonb) ELSE l END ORDER BY li), '[]'::jsonb)
                FROM jsonb_array_elements(COALESCE(col -> 'links', '[]'::jsonb)) WITH ORDINALITY AS x(l, li))) ORDER BY ci), '[]'::jsonb)
        FROM jsonb_array_elements(footer_json::jsonb -> 'columns') WITH ORDINALITY AS y(col, ci)))::text
WHERE footer_json IS NOT NULL AND footer_json LIKE '{%'
  AND jsonb_typeof(footer_json::jsonb -> 'columns') = 'array'
  AND footer_json LIKE '%"/shipping"%';
