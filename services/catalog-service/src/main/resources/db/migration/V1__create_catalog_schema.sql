-- Catalog schema v1: fresh local/dev databases only.
-- Replaces V1-V55 archived under docs/database/catalog/pre-squash-2026-10-17.
-- Do not baseline/repair an existing V1-V55 database against this migration.
-- Integer product packaging: grams / centimeters; SKU overrides: grams / millimeters.

CREATE TABLE public.brand (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    description text,
    logo_media_id character varying(36),
    active boolean DEFAULT true NOT NULL,
    logo_url character varying(1000)
);

CREATE TABLE public.category (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    name character varying(255) NOT NULL,
    slug character varying(255) NOT NULL,
    description text,
    parent_id character varying(255),
    thumbnail_media_id character varying(36),
    display_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL,
    thumbnail_url character varying(1000)
);

CREATE TABLE public.color_option (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    name character varying(100) NOT NULL,
    normalized_name character varying(100) NOT NULL,
    color_hex character varying(7),
    display_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL
);

CREATE TABLE public.inventory (
    id character varying(255) NOT NULL,
    variant_id character varying(255) NOT NULL,
    quantity integer NOT NULL,
    reserved_quantity integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    product_id character varying(255) NOT NULL,
    status character varying(20) NOT NULL,
    min_threshold integer DEFAULT 10 NOT NULL,
    CONSTRAINT ck_inventory_min_threshold CHECK ((min_threshold >= 0)),
    CONSTRAINT ck_inventory_quantities CHECK (((quantity >= 0) AND (reserved_quantity >= 0) AND (quantity >= reserved_quantity)))
);

CREATE TABLE public.inventory_ledger (
    id character varying(36) NOT NULL,
    variant_id character varying(255) NOT NULL,
    type character varying(20) NOT NULL,
    quantity integer NOT NULL,
    ref_order_id character varying(255),
    created_by character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason character varying(500),
    quantity_before integer,
    quantity_after integer,
    reserved_before integer,
    reserved_after integer,
    operation_id character varying(36)
);

CREATE TABLE public.inventory_reservation (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    status character varying(20) NOT NULL,
    rejection_reason character varying(500),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    cancellation_id character varying(36)
);

CREATE TABLE public.inventory_reservation_item (
    id character varying(36) NOT NULL,
    reservation_id character varying(36) NOT NULL,
    variant_id character varying(36) NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    CONSTRAINT inventory_reservation_item_quantity_check CHECK ((quantity > 0))
);

CREATE TABLE public.legacy_return_restock_quarantine (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    message_id character varying(255) NOT NULL,
    order_id character varying(255) NOT NULL,
    payload text NOT NULL,
    status character varying(30) NOT NULL,
    received_at timestamp with time zone NOT NULL,
    resolved_by character varying(120),
    resolved_at timestamp with time zone,
    resolution_reason character varying(500),
    inventory_operation_id character varying(36)
);

CREATE TABLE public.media_file (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    owner_id character varying(64) NOT NULL,
    original_filename character varying(255) NOT NULL,
    display_name character varying(255),
    stored_filename character varying(255) NOT NULL,
    storage_key character varying(500) NOT NULL,
    content_type character varying(120) NOT NULL,
    extension character varying(20),
    size_bytes bigint NOT NULL,
    media_type character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    visibility character varying(20) DEFAULT 'PUBLIC'::character varying NOT NULL,
    alt_text character varying(500),
    folder character varying(255),
    width integer,
    height integer,
    trashed_at timestamp without time zone,
    etag character varying(64),
    purpose character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    expires_at timestamp without time zone,
    retired_at timestamp without time zone
);

CREATE TABLE public.media_file_tag (
    file_id character varying(36) NOT NULL,
    tag character varying(80) NOT NULL
);

CREATE TABLE public.outbox_event (
    id character varying(255) NOT NULL,
    event_type character varying(255) NOT NULL,
    routing_key character varying(255) NOT NULL,
    payload text NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    last_error text,
    published_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    aggregate_id character varying(255)
);

CREATE TABLE public.processed_message (
    id character varying(255) NOT NULL,
    message_id character varying(255) NOT NULL,
    consumer_name character varying(120) NOT NULL,
    processed_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);

CREATE TABLE public.product (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    name character varying(255) NOT NULL,
    description text,
    slug character varying(255) NOT NULL,
    short_description character varying(500),
    brand_id character varying(255),
    status character varying(50) DEFAULT 'DRAFT'::character varying NOT NULL,
    featured boolean DEFAULT false NOT NULL,
    gender character varying(50),
    product_type character varying(50),
    base_price numeric(12,2),
    sale_price numeric(12,2),
    thumbnail_media_id character varying(36),
    size_chart_id character varying(255),
    meta_title character varying(255),
    meta_keyword character varying(500),
    meta_description character varying(500),
    published_at timestamp without time zone,
    deleted_at timestamp without time zone,
    thumbnail_url character varying(1000),
    weight integer,
    length integer,
    width integer,
    height integer,
    CONSTRAINT ck_product_height_range CHECK (((height IS NULL) OR ((height >= 1) AND (height <= 200)))),
    CONSTRAINT ck_product_length_range CHECK (((length IS NULL) OR ((length >= 1) AND (length <= 200)))),
    CONSTRAINT ck_product_weight_range CHECK (((weight IS NULL) OR ((weight >= 1) AND (weight <= 50000)))),
    CONSTRAINT ck_product_width_range CHECK (((width IS NULL) OR ((width >= 1) AND (width <= 200))))
);

CREATE TABLE public.product_attribute (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    name character varying(100) NOT NULL,
    code character varying(50) NOT NULL,
    type character varying(50) NOT NULL,
    filterable boolean DEFAULT false NOT NULL,
    searchable boolean DEFAULT false NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    display_name character varying(255),
    published boolean DEFAULT true NOT NULL
);

CREATE TABLE public.product_attribute_option (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    attribute_id character varying(255) NOT NULL,
    value character varying(255) NOT NULL,
    normalized_value character varying(255) NOT NULL,
    published boolean DEFAULT false NOT NULL
);

CREATE TABLE public.product_attribute_value (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    product_id character varying(255) NOT NULL,
    attribute_id character varying(255) NOT NULL,
    value character varying(255) NOT NULL,
    normalized_value character varying(255) NOT NULL,
    attribute_option_id character varying(255),
    "position" integer DEFAULT 0 NOT NULL,
    published boolean DEFAULT false NOT NULL
);

CREATE TABLE public.product_category (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    product_id character varying(255) NOT NULL,
    category_id character varying(255) NOT NULL
);

CREATE TABLE public.product_image (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    product_id character varying(255) NOT NULL,
    media_id character varying(36),
    alt_text character varying(255),
    display_order integer DEFAULT 0 NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    color character varying(100),
    url character varying(1024) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    color_option_id character varying(255)
);

CREATE TABLE public.product_variant (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    product_id character varying(255) NOT NULL,
    size character varying(255),
    color character varying(255),
    sku character varying(255),
    price numeric(19,2) NOT NULL,
    barcode character varying(100),
    sale_price numeric(12,2),
    active boolean DEFAULT true NOT NULL,
    option_signature character varying(500) NOT NULL,
    display_name character varying(255) NOT NULL,
    thumbnail_media_id character varying(36),
    color_hex character varying(20),
    color_option_id character varying(255),
    size_option_id character varying(255),
    weight_gram integer,
    length_mm integer,
    width_mm integer,
    height_mm integer,
    thumbnail_url character varying(255),
    CONSTRAINT ck_variant_height_range CHECK (((height_mm IS NULL) OR ((height_mm >= 0) AND (height_mm <= 2000)))),
    CONSTRAINT ck_variant_length_range CHECK (((length_mm IS NULL) OR ((length_mm >= 0) AND (length_mm <= 2000)))),
    CONSTRAINT ck_variant_weight_range CHECK (((weight_gram IS NULL) OR ((weight_gram >= 0) AND (weight_gram <= 50000)))),
    CONSTRAINT ck_variant_width_range CHECK (((width_mm IS NULL) OR ((width_mm >= 0) AND (width_mm <= 2000))))
);

CREATE TABLE public.review (
    id character varying(36) NOT NULL,
    product_id character varying(255) NOT NULL,
    user_id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    rating integer NOT NULL,
    comment text,
    verified_purchase boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(20) DEFAULT 'VISIBLE'::character varying NOT NULL,
    reply_comment text,
    replied_at timestamp without time zone,
    replied_by character varying(36),
    fit_rating character varying(20),
    customer_height integer,
    customer_weight integer,
    CONSTRAINT review_rating_check CHECK (((rating >= 1) AND (rating <= 5)))
);

CREATE TABLE public.review_media (
    id character varying(36) NOT NULL,
    review_id character varying(36) NOT NULL,
    url character varying(1000) NOT NULL,
    media_type character varying(20) DEFAULT 'IMAGE'::character varying NOT NULL,
    display_order integer DEFAULT 0 NOT NULL
);

CREATE TABLE public.size_chart (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    name character varying(255) NOT NULL,
    unit character varying(20) NOT NULL,
    gender character varying(50),
    product_type character varying(50),
    active boolean DEFAULT true NOT NULL
);

CREATE TABLE public.size_chart_row (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    size_chart_id character varying(255) NOT NULL,
    size_code character varying(50) NOT NULL,
    chest numeric(10,2),
    waist numeric(10,2),
    hip numeric(10,2),
    shoulder numeric(10,2),
    length numeric(10,2),
    inseam numeric(10,2),
    height_min numeric(10,2),
    height_max numeric(10,2),
    weight_min numeric(10,2),
    weight_max numeric(10,2),
    CONSTRAINT ck_size_chart_height_max_positive CHECK (((height_max IS NULL) OR (height_max > (0)::numeric))),
    CONSTRAINT ck_size_chart_height_min_positive CHECK (((height_min IS NULL) OR (height_min > (0)::numeric))),
    CONSTRAINT ck_size_chart_height_range CHECK (((height_min IS NULL) OR (height_max IS NULL) OR (height_min <= height_max))),
    CONSTRAINT ck_size_chart_weight_max_positive CHECK (((weight_max IS NULL) OR (weight_max > (0)::numeric))),
    CONSTRAINT ck_size_chart_weight_min_positive CHECK (((weight_min IS NULL) OR (weight_min > (0)::numeric))),
    CONSTRAINT ck_size_chart_weight_range CHECK (((weight_min IS NULL) OR (weight_max IS NULL) OR (weight_min <= weight_max)))
);

CREATE TABLE public.size_option (
    id character varying(255) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    name character varying(50) NOT NULL,
    normalized_name character varying(50) NOT NULL,
    category character varying(50),
    display_order integer DEFAULT 0 NOT NULL,
    active boolean DEFAULT true NOT NULL
);

CREATE TABLE public.wishlist_item (
    user_id character varying(36) NOT NULL,
    product_id character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

ALTER TABLE ONLY public.brand
    ADD CONSTRAINT brand_name_key UNIQUE (name);

ALTER TABLE ONLY public.brand
    ADD CONSTRAINT brand_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.brand
    ADD CONSTRAINT brand_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_name_key UNIQUE (name);

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.category
    ADD CONSTRAINT category_slug_key UNIQUE (slug);

ALTER TABLE ONLY public.color_option
    ADD CONSTRAINT color_option_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_ledger
    ADD CONSTRAINT inventory_ledger_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_reservation_item
    ADD CONSTRAINT inventory_reservation_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory_reservation
    ADD CONSTRAINT inventory_reservation_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.inventory_reservation
    ADD CONSTRAINT inventory_reservation_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_variant_id_key UNIQUE (variant_id);

ALTER TABLE ONLY public.legacy_return_restock_quarantine
    ADD CONSTRAINT legacy_return_restock_quarantine_message_id_key UNIQUE (message_id);

ALTER TABLE ONLY public.legacy_return_restock_quarantine
    ADD CONSTRAINT legacy_return_restock_quarantine_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.media_file
    ADD CONSTRAINT media_file_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.media_file
    ADD CONSTRAINT media_file_storage_key_key UNIQUE (storage_key);

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.wishlist_item
    ADD CONSTRAINT pk_wishlist_item PRIMARY KEY (user_id, product_id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT processed_message_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_attribute
    ADD CONSTRAINT product_attribute_code_key UNIQUE (code);

ALTER TABLE ONLY public.product_attribute_option
    ADD CONSTRAINT product_attribute_option_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_attribute
    ADD CONSTRAINT product_attribute_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_attribute_value
    ADD CONSTRAINT product_attribute_value_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_category
    ADD CONSTRAINT product_category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_image
    ADD CONSTRAINT product_image_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product
    ADD CONSTRAINT product_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT product_variant_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT product_variant_sku_key UNIQUE (sku);

ALTER TABLE ONLY public.review_media
    ADD CONSTRAINT review_media_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.review
    ADD CONSTRAINT review_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.size_chart
    ADD CONSTRAINT size_chart_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.size_chart_row
    ADD CONSTRAINT size_chart_row_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.size_option
    ADD CONSTRAINT size_option_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.color_option
    ADD CONSTRAINT uk_color_option_name UNIQUE (name);

ALTER TABLE ONLY public.color_option
    ADD CONSTRAINT uk_color_option_normalized_name UNIQUE (normalized_name);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT uk_processed_message UNIQUE (message_id, consumer_name);

ALTER TABLE ONLY public.product_attribute_option
    ADD CONSTRAINT uk_product_attribute_option_value UNIQUE (attribute_id, normalized_value);

ALTER TABLE ONLY public.product_category
    ADD CONSTRAINT uk_product_category UNIQUE (product_id, category_id);

ALTER TABLE ONLY public.product
    ADD CONSTRAINT uk_product_slug UNIQUE (slug);

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT uk_product_variant_product_signature UNIQUE (product_id, option_signature);

ALTER TABLE ONLY public.size_option
    ADD CONSTRAINT uk_size_option_name UNIQUE (name);

ALTER TABLE ONLY public.size_option
    ADD CONSTRAINT uk_size_option_normalized_name UNIQUE (normalized_name);

ALTER TABLE ONLY public.media_file_tag
    ADD CONSTRAINT uq_media_file_tag UNIQUE (file_id, tag);

ALTER TABLE ONLY public.review
    ADD CONSTRAINT uq_review_product_user_order UNIQUE (product_id, user_id, order_id);

CREATE INDEX idx_inventory_ledger_ref_order ON public.inventory_ledger USING btree (ref_order_id);

CREATE INDEX idx_inventory_ledger_variant_created ON public.inventory_ledger USING btree (variant_id, created_at DESC);

CREATE INDEX idx_inventory_reservation_item_reservation ON public.inventory_reservation_item USING btree (reservation_id);

CREATE INDEX idx_inventory_reservation_item_variant ON public.inventory_reservation_item USING btree (variant_id);

CREATE INDEX idx_inventory_variant_id ON public.inventory USING btree (variant_id);

CREATE INDEX idx_legacy_restock_review ON public.legacy_return_restock_quarantine USING btree (status, received_at);

CREATE INDEX idx_media_file_created_at ON public.media_file USING btree (created_at);

CREATE INDEX idx_media_file_folder ON public.media_file USING btree (folder);

CREATE INDEX idx_media_file_lifecycle ON public.media_file USING btree (purpose, status, expires_at);

CREATE INDEX idx_media_file_owner_status ON public.media_file USING btree (owner_id, status);

CREATE INDEX idx_media_file_retired ON public.media_file USING btree (status, retired_at);

CREATE INDEX idx_media_file_status_created_at ON public.media_file USING btree (status, created_at);

CREATE INDEX idx_media_file_type ON public.media_file USING btree (media_type);

CREATE INDEX idx_outbox_event_status_next_attempt_at ON public.outbox_event USING btree (status, next_attempt_at);

CREATE INDEX idx_processed_message_consumer ON public.processed_message USING btree (consumer_name, processed_at);

CREATE INDEX idx_product_category_category_id ON public.product_category USING btree (category_id);

CREATE INDEX idx_product_category_product_id ON public.product_category USING btree (product_id);

CREATE INDEX idx_product_image_color_option_id ON public.product_image USING btree (color_option_id);

CREATE INDEX idx_product_variant_color_option_id ON public.product_variant USING btree (color_option_id);

CREATE INDEX idx_product_variant_product_id ON public.product_variant USING btree (product_id);

CREATE INDEX idx_product_variant_size_option_id ON public.product_variant USING btree (size_option_id);

CREATE INDEX idx_review_media_review_id ON public.review_media USING btree (review_id);

CREATE INDEX idx_review_product_created ON public.review USING btree (product_id, created_at DESC);

CREATE INDEX idx_review_product_rating ON public.review USING btree (product_id, rating);

CREATE INDEX idx_review_product_status ON public.review USING btree (product_id, status);

CREATE INDEX idx_review_status ON public.review USING btree (status);

CREATE INDEX idx_review_user_id ON public.review USING btree (user_id);

CREATE INDEX idx_wishlist_user_id ON public.wishlist_item USING btree (user_id);

CREATE UNIQUE INDEX uk_inventory_ledger_operation_id ON public.inventory_ledger USING btree (operation_id) WHERE (operation_id IS NOT NULL);

CREATE UNIQUE INDEX uk_product_variant_barcode ON public.product_variant USING btree (barcode) WHERE ((barcode IS NOT NULL) AND (btrim((barcode)::text) <> ''::text));

CREATE UNIQUE INDEX uq_inventory_reservation_cancellation_id ON public.inventory_reservation USING btree (cancellation_id) WHERE (cancellation_id IS NOT NULL);

ALTER TABLE ONLY public.category
    ADD CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES public.category(id);

ALTER TABLE ONLY public.product_attribute_value
    ADD CONSTRAINT fk_product_attribute_value_attribute FOREIGN KEY (attribute_id) REFERENCES public.product_attribute(id);

ALTER TABLE ONLY public.product_attribute_value
    ADD CONSTRAINT fk_product_attribute_value_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product
    ADD CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES public.brand(id);

ALTER TABLE ONLY public.product_category
    ADD CONSTRAINT fk_product_category_category FOREIGN KEY (category_id) REFERENCES public.category(id);

ALTER TABLE ONLY public.product_category
    ADD CONSTRAINT fk_product_category_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_image
    ADD CONSTRAINT fk_product_image_color_option FOREIGN KEY (color_option_id) REFERENCES public.color_option(id);

ALTER TABLE ONLY public.product_image
    ADD CONSTRAINT fk_product_image_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT fk_product_variant_color_option FOREIGN KEY (color_option_id) REFERENCES public.color_option(id);

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT fk_product_variant_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_variant
    ADD CONSTRAINT fk_product_variant_size_option FOREIGN KEY (size_option_id) REFERENCES public.size_option(id);

ALTER TABLE ONLY public.review_media
    ADD CONSTRAINT fk_review_media_review FOREIGN KEY (review_id) REFERENCES public.review(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.review
    ADD CONSTRAINT fk_review_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.size_chart_row
    ADD CONSTRAINT fk_size_chart_row_size_chart FOREIGN KEY (size_chart_id) REFERENCES public.size_chart(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.wishlist_item
    ADD CONSTRAINT fk_wishlist_item_product FOREIGN KEY (product_id) REFERENCES public.product(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.inventory_reservation_item
    ADD CONSTRAINT inventory_reservation_item_reservation_id_fkey FOREIGN KEY (reservation_id) REFERENCES public.inventory_reservation(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.media_file_tag
    ADD CONSTRAINT media_file_tag_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.media_file(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.product_attribute_option
    ADD CONSTRAINT product_attribute_option_attribute_id_fkey FOREIGN KEY (attribute_id) REFERENCES public.product_attribute(id);
