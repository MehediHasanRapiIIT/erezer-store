-- ============================================================================
-- V1 - Baseline schema (Phase 0-8, generated from the live Hibernate schema)
-- ============================================================================
--
-- Generated with:
--   pg_dump -U postgres -d delivery_app_v1 --schema-only --no-owner
--           --no-privileges -T flyway_schema_history
-- against a database built by Hibernate ddl-auto=update from the entity model.
--
-- Two pg_dump artefacts were stripped by hand and MUST stay stripped if this
-- file is ever regenerated:
--   * restrict / unrestrict - psql meta-commands; Flyway's parser rejects them.
--   * SELECT pg_catalog.set_config('search_path', '', false) - Flyway runs every
--     migration on ONE connection, so an emptied search_path would break the
--     unqualified table names in V2-V6.
--
-- This replaces the former empty V1__baseline_marker.sql, which could not build
-- a fresh database: V2 assumed the Phase 0-2 tables already existed.
--
-- On an existing database already baselined at V1, this file is never executed
-- (baseline-on-migrate covers version 1); V2+ continue to apply as before.
-- ============================================================================

CREATE TABLE public.addresses (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    address character varying(255),
    address_type character varying(255),
    apartment_name character varying(255),
    consumer_id uuid,
    house_number bigint,
    latitude real,
    longitude real,
    name character varying(255),
    CONSTRAINT addresses_address_type_check CHECK (((address_type)::text = ANY ((ARRAY['HOME'::character varying, 'WORK'::character varying, 'OTHER'::character varying])::text[])))
);


--
-- Name: admin_user; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.admin_user (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    password character varying(255),
    role character varying(255),
    user_name character varying(255)
);


--
-- Name: bundle_offer; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bundle_offer (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    bundle_price numeric(12,2) NOT NULL,
    buy_count integer NOT NULL,
    compare_at_price numeric(12,2),
    description text,
    featured boolean NOT NULL,
    get_count integer NOT NULL,
    is_active boolean NOT NULL,
    label character varying(120),
    name character varying(150) NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: bundle_offer_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bundle_offer_image (
    bundle_offer_id uuid NOT NULL,
    image_url character varying(1000),
    sort_order integer NOT NULL
);


--
-- Name: bundle_offer_product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bundle_offer_product (
    bundle_offer_id uuid NOT NULL,
    product_id bigint NOT NULL,
    "position" integer NOT NULL
);


--
-- Name: cart; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cart (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    delivery_instructions character varying(500),
    image_url character varying(500),
    last_emailed_at timestamp(6) without time zone,
    product_id bigint,
    product_name character varying(150),
    quantity integer,
    stock_status character varying(255),
    unit_price numeric(19,4),
    user_id uuid,
    variant_id bigint,
    CONSTRAINT cart_stock_status_check CHECK (((stock_status)::text = ANY ((ARRAY['IN_STOCK'::character varying, 'LOW_STOCK'::character varying, 'OUT_OF_STOCK'::character varying])::text[])))
);


--
-- Name: category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.category (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    image_url character varying(255),
    is_active boolean,
    name character varying(255)
);


--
-- Name: category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.category ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: contact_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.contact_message (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    email character varying(255) NOT NULL,
    message character varying(4000) NOT NULL,
    name character varying(200) NOT NULL,
    order_id uuid,
    status character varying(32) NOT NULL,
    subject character varying(255)
);


--
-- Name: coupon; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coupon (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    code character varying(64) NOT NULL,
    description character varying(255),
    discount_type character varying(16) NOT NULL,
    discount_value numeric(12,2),
    is_active boolean NOT NULL,
    min_order_amount numeric(12,2),
    per_user_limit integer,
    times_used integer NOT NULL,
    usage_limit integer,
    valid_from timestamp(6) without time zone,
    valid_to timestamp(6) without time zone
);


--
-- Name: coupon_redemption; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.coupon_redemption (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    coupon_id uuid NOT NULL,
    discount_amount numeric(12,2) NOT NULL,
    order_id uuid NOT NULL,
    redeemed_at timestamp(6) without time zone NOT NULL,
    user_id uuid
);


--
-- Name: custom_design_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_asset (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    active boolean NOT NULL,
    name character varying(200) NOT NULL,
    sort_order integer NOT NULL,
    url character varying(1000) NOT NULL
);


--
-- Name: custom_design_color; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_color (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    back_image_url character varying(1000),
    front_image_url character varying(1000),
    hex character varying(9) NOT NULL,
    left_sleeve_image_url character varying(1000),
    name character varying(80) NOT NULL,
    right_sleeve_image_url character varying(1000),
    sort_order integer NOT NULL,
    item_id uuid NOT NULL
);


--
-- Name: custom_design_draft; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_draft (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    color_name character varying(80),
    design_json text,
    item_name character varying(150),
    name character varying(200) NOT NULL,
    share_token character varying(64),
    thumbnail_url character varying(1000),
    user_id uuid NOT NULL
);


--
-- Name: custom_design_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_item (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    active boolean NOT NULL,
    category character varying(100),
    name character varying(150) NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: custom_design_item_print_technique; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_item_print_technique (
    item_id uuid NOT NULL,
    technique character varying(80),
    "position" integer NOT NULL
);


--
-- Name: custom_design_item_size; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_design_item_size (
    item_id uuid NOT NULL,
    size character varying(40),
    "position" integer NOT NULL
);


--
-- Name: custom_order; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    admin_notes text,
    apartment character varying(255),
    city character varying(150) NOT NULL,
    color_name character varying(80),
    country character varying(120) NOT NULL,
    design_json text,
    email character varying(255) NOT NULL,
    first_name character varying(120) NOT NULL,
    item_name character varying(150),
    last_name character varying(120) NOT NULL,
    notes text NOT NULL,
    phone character varying(40) NOT NULL,
    print_technique character varying(80),
    reference character varying(20) NOT NULL,
    shipping_address character varying(500) NOT NULL,
    size character varying(40),
    status character varying(20) NOT NULL,
    user_id uuid,
    zip_code character varying(30),
    CONSTRAINT custom_order_status_check CHECK (((status)::text = ANY ((ARRAY['NEW'::character varying, 'IN_REVIEW'::character varying, 'QUOTED'::character varying, 'CONFIRMED'::character varying, 'DELIVERED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: custom_order_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_image (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    sort_order integer NOT NULL,
    url character varying(1000) NOT NULL,
    view_name character varying(40) NOT NULL,
    custom_order_id uuid NOT NULL
);


--
-- Name: discount; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.discount (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    description character varying(255),
    discount_type character varying(16) NOT NULL,
    discount_value numeric(12,2),
    is_active boolean NOT NULL,
    name character varying(120) NOT NULL,
    priority integer NOT NULL,
    scope character varying(16) NOT NULL,
    stackable boolean NOT NULL,
    target_id bigint,
    valid_from timestamp(6) without time zone,
    valid_to timestamp(6) without time zone
);


--
-- Name: flash_sale; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flash_sale (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    coupon_code character varying(40),
    discount_type character varying(16) NOT NULL,
    discount_value numeric(12,2) NOT NULL,
    ends_at timestamp(6) without time zone NOT NULL,
    featured boolean DEFAULT false NOT NULL,
    is_active boolean NOT NULL,
    label character varying(120),
    min_spend numeric(12,2),
    name character varying(120) NOT NULL,
    starts_at timestamp(6) without time zone
);


--
-- Name: flash_sale_product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flash_sale_product (
    flash_sale_id uuid NOT NULL,
    product_id bigint NOT NULL,
    sort_order integer NOT NULL
);


--
-- Name: inventory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory (
    id bigint NOT NULL,
    last_updated timestamp(6) without time zone,
    low_stock_threshold integer,
    product_id bigint NOT NULL,
    stock_quantity integer DEFAULT 0 NOT NULL,
    unit character varying(255),
    CONSTRAINT inventory_stock_quantity_check CHECK ((stock_quantity >= 0))
);


--
-- Name: inventory_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.inventory ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.inventory_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_campaign; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_campaign (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    audience character varying(64) NOT NULL,
    body_html text NOT NULL,
    fail_count integer NOT NULL,
    sent_at timestamp(6) without time zone,
    sent_by character varying(200),
    sent_count integer NOT NULL,
    status character varying(32) NOT NULL,
    subject character varying(255) NOT NULL
);


--
-- Name: newsletter_send_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_send_log (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    campaign_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    error_message character varying(2000),
    sent_at timestamp(6) without time zone NOT NULL,
    status character varying(32) NOT NULL,
    subscriber_id uuid
);


--
-- Name: newsletter_send_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.newsletter_send_log ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.newsletter_send_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: newsletter_subscriber; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.newsletter_subscriber (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    email character varying(255) NOT NULL,
    source character varying(64),
    status character varying(32) NOT NULL,
    subscribed_at timestamp(6) without time zone NOT NULL,
    unsubscribe_token character varying(64) NOT NULL,
    unsubscribed_at timestamp(6) without time zone
);


--
-- Name: notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    is_read boolean,
    message_bn character varying(255),
    message_en character varying(255),
    title_bn character varying(255),
    title_en character varying(255),
    user_id uuid
);


--
-- Name: order_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_item (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    custom_measurements character varying(2000),
    custom_surcharge numeric(12,2),
    order_id uuid,
    price_at_order numeric(38,2),
    product_id bigint,
    quantity integer,
    variant_id bigint,
    variant_name character varying(255),
    variant_size character varying(255)
);


--
-- Name: order_note; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_note (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    author character varying(200),
    body character varying(4000) NOT NULL,
    order_id uuid NOT NULL
);


--
-- Name: order_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.order_status_history (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    changed_by character varying(200),
    from_status character varying(32),
    note character varying(1000),
    order_id uuid NOT NULL,
    to_status character varying(32) NOT NULL
);


--
-- Name: orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.orders (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    cancellation_reason character varying(500),
    cancelled_at timestamp(6) without time zone,
    client_id uuid,
    coupon_code character varying(64),
    coupon_id uuid,
    courier_name character varying(255),
    customer_email character varying(255),
    customer_name character varying(255),
    customer_phone character varying(40),
    delivered_at timestamp(6) without time zone,
    delivery_address character varying(255),
    delivery_charge double precision,
    discount_amount numeric(12,2),
    latitude real,
    longitude real,
    order_status character varying(255),
    payment_id uuid,
    payment_method character varying(255),
    rider_id uuid,
    shipping_fee numeric(38,2),
    shipping_zone_id bigint,
    shop_id bigint,
    subtotal_amount numeric(12,2),
    tax_amount numeric(38,2),
    total_amount numeric(38,2),
    tracking_number character varying(255)
);


--
-- Name: otp_verification; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.otp_verification (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    expires_at timestamp(6) without time zone,
    is_used boolean,
    otp_code character varying(255),
    phone_number character varying(255),
    user_id uuid,
    user_role character varying(255)
);


--
-- Name: payment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payment (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    amount numeric(38,2),
    callback_url character varying(1024),
    metadata text,
    method character varying(255),
    order_id uuid,
    payer_account character varying(64),
    provider character varying(32),
    provider_payment_id character varying(128),
    provider_trx_id character varying(128),
    status character varying(255),
    transaction_id character varying(255)
);


--
-- Name: product; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    avg_rating double precision DEFAULT 0.0 NOT NULL,
    brand character varying(120),
    care_instructions character varying(2000),
    category_id bigint,
    custom_size_enabled boolean,
    custom_size_note character varying(255),
    custom_size_surcharge numeric(12,2),
    description character varying(255),
    discount_price numeric(38,2),
    gender character varying(16),
    image_url character varying(255),
    is_available boolean,
    is_featured boolean,
    is_new_arrival boolean,
    low_stock_threshold integer,
    material character varying(255),
    name character varying(255),
    price numeric(38,2),
    shop_id bigint,
    sku character varying(255),
    stock_quantity integer DEFAULT 0 NOT NULL,
    total_reviews integer DEFAULT 0 NOT NULL,
    unit character varying(255),
    CONSTRAINT product_stock_quantity_check CHECK ((stock_quantity >= 0))
);


--
-- Name: product_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.product ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.product_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: product_image; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_image (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    alt_text character varying(255),
    is_primary boolean NOT NULL,
    product_id bigint NOT NULL,
    sort_order integer NOT NULL,
    url character varying(1024) NOT NULL
);


--
-- Name: product_image_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.product_image ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.product_image_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: promotional_banner; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.promotional_banner (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    from_date date,
    image_url character varying(255),
    promotion_details character varying(255),
    promotion_title character varying(255),
    to_date date
);


--
-- Name: return_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_item (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    condition character varying(32),
    line_refund_amount numeric(12,2),
    order_item_id uuid NOT NULL,
    product_id bigint,
    quantity integer NOT NULL,
    return_request_id uuid NOT NULL
);


--
-- Name: return_photo; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_photo (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    caption character varying(255),
    return_request_id uuid NOT NULL,
    url character varying(1024) NOT NULL
);


--
-- Name: return_photo_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.return_photo ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.return_photo_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: return_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.return_request (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    admin_notes character varying(2000),
    customer_email character varying(255),
    customer_notes character varying(2000),
    decided_at timestamp(6) without time zone,
    decided_by character varying(200),
    order_id uuid NOT NULL,
    picked_up_at timestamp(6) without time zone,
    reason character varying(64) NOT NULL,
    refund_amount numeric(12,2),
    refunded_at timestamp(6) without time zone,
    requested_at timestamp(6) without time zone NOT NULL,
    status character varying(32) NOT NULL,
    user_id uuid
);


--
-- Name: review; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.review (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    comment text,
    order_id uuid NOT NULL,
    product_id bigint NOT NULL,
    rating integer NOT NULL,
    user_id uuid NOT NULL
);


--
-- Name: rider_earnings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.rider_earnings (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    earning_amount numeric(38,2),
    order_id uuid,
    rider_id uuid
);


--
-- Name: shipping_zone; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shipping_zone (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    code character varying(64) NOT NULL,
    country_code character varying(8) NOT NULL,
    display_name character varying(120) NOT NULL,
    flat_fee numeric(12,2) NOT NULL,
    free_above numeric(12,2),
    is_active boolean NOT NULL,
    is_default boolean NOT NULL,
    region_keywords character varying(2000),
    sort_order integer NOT NULL
);


--
-- Name: shipping_zone_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.shipping_zone ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.shipping_zone_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: shop; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shop (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    description bigint,
    location character varying(255),
    name character varying(255)
);


--
-- Name: shop_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.shop ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.shop_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: store_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_settings (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    brand_story_json text,
    exchange_window_days integer,
    footer_json text,
    highlights_json text,
    marquee_json text,
    payment_bkash_enabled boolean,
    payment_card_enabled boolean,
    payment_cod_enabled boolean,
    return_policy_text text,
    size_chart_json text,
    support_email character varying(128),
    support_hours character varying(120),
    support_phone character varying(32)
);


--
-- Name: tax_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tax_rule (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    code character varying(64) NOT NULL,
    display_name character varying(120) NOT NULL,
    is_active boolean NOT NULL,
    is_inclusive boolean NOT NULL,
    rate numeric(6,4) NOT NULL,
    zone_id bigint
);


--
-- Name: tax_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.tax_rule ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.tax_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_rider; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_rider (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    address character varying(255),
    approved_at timestamp(6) without time zone,
    contact_no character varying(255),
    contact_phone character varying(255),
    driving_license character varying(255),
    guardian_name character varying(255),
    image_url character varying(255),
    name character varying(255),
    nid_number character varying(255),
    password character varying(255),
    plate_number character varying(255),
    rating double precision,
    status character varying(255),
    vehicle_type character varying(255)
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    email character varying(255),
    email_verification_expires_at timestamp(6) without time zone,
    email_verification_token character varying(255),
    email_verified boolean,
    first_name character varying(255),
    is_active boolean,
    last_login_at timestamp(6) without time zone,
    last_name character varying(255),
    latitude real,
    longitude real,
    password_hash character varying(255),
    password_reset_expires_at timestamp(6) without time zone,
    password_reset_token character varying(255),
    phone_number character varying(255),
    profile_image character varying(255)
);


--
-- Name: variant; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.variant (
    id bigint NOT NULL,
    created_at timestamp without time zone,
    created_by bigint,
    deleted boolean DEFAULT false,
    deleted_at timestamp without time zone,
    deleted_by bigint,
    updated_at timestamp without time zone,
    updated_by bigint,
    version bigint DEFAULT 0,
    category_id bigint,
    name character varying(255),
    price_override numeric(12,2),
    product_id bigint,
    quantity bigint,
    shop_id bigint,
    size character varying(16),
    sku character varying(64),
    stock_quantity integer
);


--
-- Name: variant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.variant ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.variant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: addresses addresses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.addresses
    ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);


--
-- Name: admin_user admin_user_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.admin_user
    ADD CONSTRAINT admin_user_pkey PRIMARY KEY (id);


--
-- Name: bundle_offer_image bundle_offer_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bundle_offer_image
    ADD CONSTRAINT bundle_offer_image_pkey PRIMARY KEY (bundle_offer_id, sort_order);


--
-- Name: bundle_offer bundle_offer_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bundle_offer
    ADD CONSTRAINT bundle_offer_pkey PRIMARY KEY (id);


--
-- Name: bundle_offer_product bundle_offer_product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bundle_offer_product
    ADD CONSTRAINT bundle_offer_product_pkey PRIMARY KEY (bundle_offer_id, "position");


--
-- Name: cart cart_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cart
    ADD CONSTRAINT cart_pkey PRIMARY KEY (id);


--
-- Name: category category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);


--
-- Name: contact_message contact_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.contact_message
    ADD CONSTRAINT contact_message_pkey PRIMARY KEY (id);


--
-- Name: coupon coupon_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupon
    ADD CONSTRAINT coupon_pkey PRIMARY KEY (id);


--
-- Name: coupon_redemption coupon_redemption_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.coupon_redemption
    ADD CONSTRAINT coupon_redemption_pkey PRIMARY KEY (id);


--
-- Name: custom_design_asset custom_design_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_asset
    ADD CONSTRAINT custom_design_asset_pkey PRIMARY KEY (id);


--
-- Name: custom_design_color custom_design_color_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_color
    ADD CONSTRAINT custom_design_color_pkey PRIMARY KEY (id);


--
-- Name: custom_design_draft custom_design_draft_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_draft
    ADD CONSTRAINT custom_design_draft_pkey PRIMARY KEY (id);


--
-- Name: custom_design_item custom_design_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_item
    ADD CONSTRAINT custom_design_item_pkey PRIMARY KEY (id);


--
-- Name: custom_design_item_print_technique custom_design_item_print_technique_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_item_print_technique
    ADD CONSTRAINT custom_design_item_print_technique_pkey PRIMARY KEY (item_id, "position");


--
-- Name: custom_design_item_size custom_design_item_size_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_item_size
    ADD CONSTRAINT custom_design_item_size_pkey PRIMARY KEY (item_id, "position");


--
-- Name: custom_order_image custom_order_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_image
    ADD CONSTRAINT custom_order_image_pkey PRIMARY KEY (id);


--
-- Name: custom_order custom_order_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order
    ADD CONSTRAINT custom_order_pkey PRIMARY KEY (id);


--
-- Name: discount discount_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.discount
    ADD CONSTRAINT discount_pkey PRIMARY KEY (id);


--
-- Name: flash_sale flash_sale_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flash_sale
    ADD CONSTRAINT flash_sale_pkey PRIMARY KEY (id);


--
-- Name: flash_sale_product flash_sale_product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flash_sale_product
    ADD CONSTRAINT flash_sale_product_pkey PRIMARY KEY (flash_sale_id, sort_order);


--
-- Name: inventory inventory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);


--
-- Name: newsletter_campaign newsletter_campaign_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_campaign
    ADD CONSTRAINT newsletter_campaign_pkey PRIMARY KEY (id);


--
-- Name: newsletter_send_log newsletter_send_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_send_log
    ADD CONSTRAINT newsletter_send_log_pkey PRIMARY KEY (id);


--
-- Name: newsletter_subscriber newsletter_subscriber_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.newsletter_subscriber
    ADD CONSTRAINT newsletter_subscriber_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: order_item order_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_pkey PRIMARY KEY (id);


--
-- Name: order_note order_note_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_note
    ADD CONSTRAINT order_note_pkey PRIMARY KEY (id);


--
-- Name: order_status_history order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);


--
-- Name: orders orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);


--
-- Name: otp_verification otp_verification_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.otp_verification
    ADD CONSTRAINT otp_verification_pkey PRIMARY KEY (id);


--
-- Name: payment payment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);


--
-- Name: product_image product_image_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_image
    ADD CONSTRAINT product_image_pkey PRIMARY KEY (id);


--
-- Name: product product_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_pkey PRIMARY KEY (id);


--
-- Name: promotional_banner promotional_banner_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotional_banner
    ADD CONSTRAINT promotional_banner_pkey PRIMARY KEY (id);


--
-- Name: return_item return_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_item
    ADD CONSTRAINT return_item_pkey PRIMARY KEY (id);


--
-- Name: return_photo return_photo_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_photo
    ADD CONSTRAINT return_photo_pkey PRIMARY KEY (id);


--
-- Name: return_request return_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.return_request
    ADD CONSTRAINT return_request_pkey PRIMARY KEY (id);


--
-- Name: review review_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.review
    ADD CONSTRAINT review_pkey PRIMARY KEY (id);


--
-- Name: rider_earnings rider_earnings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.rider_earnings
    ADD CONSTRAINT rider_earnings_pkey PRIMARY KEY (id);


--
-- Name: shipping_zone shipping_zone_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shipping_zone
    ADD CONSTRAINT shipping_zone_pkey PRIMARY KEY (id);


--
-- Name: shop shop_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shop
    ADD CONSTRAINT shop_pkey PRIMARY KEY (id);


--
-- Name: store_settings store_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_settings
    ADD CONSTRAINT store_settings_pkey PRIMARY KEY (id);


--
-- Name: tax_rule tax_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tax_rule
    ADD CONSTRAINT tax_rule_pkey PRIMARY KEY (id);


--
-- Name: custom_design_draft uk41bagbk6cl1rqfxrdkq0twqu6; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_draft
    ADD CONSTRAINT uk41bagbk6cl1rqfxrdkq0twqu6 UNIQUE (share_token);


--
-- Name: users uk6dotkott2kjsp8vw4d0m25fb7; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk6dotkott2kjsp8vw4d0m25fb7 UNIQUE (email);


--
-- Name: inventory ukce3rbi3bfstbvvyne34c1dvyv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT ukce3rbi3bfstbvvyne34c1dvyv UNIQUE (product_id);


--
-- Name: product ukq1mafxn973ldq80m1irp3mpvq; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product
    ADD CONSTRAINT ukq1mafxn973ldq80m1irp3mpvq UNIQUE (sku);


--
-- Name: custom_order ukqnlsxsqjib9sitgu2fgvgjk0a; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order
    ADD CONSTRAINT ukqnlsxsqjib9sitgu2fgvgjk0a UNIQUE (reference);


--
-- Name: review uq_review_user_product_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.review
    ADD CONSTRAINT uq_review_user_product_order UNIQUE (user_id, product_id, order_id);


--
-- Name: user_rider user_rider_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_rider
    ADD CONSTRAINT user_rider_pkey PRIMARY KEY (id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: variant variant_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.variant
    ADD CONSTRAINT variant_pkey PRIMARY KEY (id);


--
-- Name: idx_bundle_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bundle_active ON public.bundle_offer USING btree (is_active, sort_order);


--
-- Name: idx_bundle_featured; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bundle_featured ON public.bundle_offer USING btree (featured);


--
-- Name: idx_campaign_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_campaign_status ON public.newsletter_campaign USING btree (status, created_at DESC);


--
-- Name: idx_cd_asset_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cd_asset_active ON public.custom_design_asset USING btree (active, sort_order);


--
-- Name: idx_cd_color_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cd_color_item ON public.custom_design_color USING btree (item_id, sort_order);


--
-- Name: idx_cd_draft_share; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cd_draft_share ON public.custom_design_draft USING btree (share_token);


--
-- Name: idx_cd_draft_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cd_draft_user ON public.custom_design_draft USING btree (user_id, updated_at DESC);


--
-- Name: idx_cd_item_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cd_item_active ON public.custom_design_item USING btree (active, sort_order);


--
-- Name: idx_contact_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_contact_status ON public.contact_message USING btree (status, created_at DESC);


--
-- Name: idx_custom_order_email; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_order_email ON public.custom_order USING btree (email);


--
-- Name: idx_custom_order_image_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_order_image_order ON public.custom_order_image USING btree (custom_order_id);


--
-- Name: idx_custom_order_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_order_status ON public.custom_order USING btree (status, created_at DESC);


--
-- Name: idx_custom_order_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_order_user ON public.custom_order USING btree (user_id);


--
-- Name: idx_newsletter_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_newsletter_status ON public.newsletter_subscriber USING btree (status);


--
-- Name: idx_newsletter_unsub_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_newsletter_unsub_token ON public.newsletter_subscriber USING btree (unsubscribe_token);


--
-- Name: idx_order_note_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_order_note_order ON public.order_note USING btree (order_id, created_at DESC);


--
-- Name: idx_osh_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_osh_created ON public.order_status_history USING btree (created_at);


--
-- Name: idx_osh_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_osh_order ON public.order_status_history USING btree (order_id);


--
-- Name: idx_product_image_product; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_image_product ON public.product_image USING btree (product_id, sort_order);


--
-- Name: idx_return_item_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_item_request ON public.return_item USING btree (return_request_id);


--
-- Name: idx_return_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_order ON public.return_request USING btree (order_id);


--
-- Name: idx_return_photo_request; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_photo_request ON public.return_photo USING btree (return_request_id);


--
-- Name: idx_return_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_status ON public.return_request USING btree (status);


--
-- Name: idx_return_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_return_user ON public.return_request USING btree (user_id);


--
-- Name: idx_send_log_campaign; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_send_log_campaign ON public.newsletter_send_log USING btree (campaign_id);


--
-- Name: flash_sale_product fk1vmmu21g20w1lbys9kwbhv1mg; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flash_sale_product
    ADD CONSTRAINT fk1vmmu21g20w1lbys9kwbhv1mg FOREIGN KEY (flash_sale_id) REFERENCES public.flash_sale(id);


--
-- Name: custom_design_item_print_technique fk3dt30m753y5ktpf4lc30tiw2r; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_item_print_technique
    ADD CONSTRAINT fk3dt30m753y5ktpf4lc30tiw2r FOREIGN KEY (item_id) REFERENCES public.custom_design_item(id);


--
-- Name: custom_order_image fk8rqpbaesoxefneruaa0sov92u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_image
    ADD CONSTRAINT fk8rqpbaesoxefneruaa0sov92u FOREIGN KEY (custom_order_id) REFERENCES public.custom_order(id);


--
-- Name: custom_design_color fkakmjg7jcubhreeckh410b33a1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_color
    ADD CONSTRAINT fkakmjg7jcubhreeckh410b33a1 FOREIGN KEY (item_id) REFERENCES public.custom_design_item(id);


--
-- Name: bundle_offer_image fkblndsgalbi13ttl92fa0cr39x; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bundle_offer_image
    ADD CONSTRAINT fkblndsgalbi13ttl92fa0cr39x FOREIGN KEY (bundle_offer_id) REFERENCES public.bundle_offer(id);


--
-- Name: custom_design_item_size fkjnls4xv9uknbhmnh2ya4j0019; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_design_item_size
    ADD CONSTRAINT fkjnls4xv9uknbhmnh2ya4j0019 FOREIGN KEY (item_id) REFERENCES public.custom_design_item(id);


--
-- Name: bundle_offer_product fktnu3unbkm01644r9tg27beqhp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bundle_offer_product
    ADD CONSTRAINT fktnu3unbkm01644r9tg27beqhp FOREIGN KEY (bundle_offer_id) REFERENCES public.bundle_offer(id);


--
-- PostgreSQL database dump complete
--
