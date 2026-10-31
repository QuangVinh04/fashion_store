
CREATE TABLE public.cart (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    user_id character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL
);

CREATE TABLE public.cart_item (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    cart_id character varying(36) NOT NULL,
    variant_id character varying(36) NOT NULL,
    product_id character varying(36) NOT NULL,
    size_name character varying(20),
    color_name character varying(50),
    unit_price numeric(19,2) NOT NULL,
    quantity integer NOT NULL,
    product_name character varying(255)
);

CREATE TABLE public.checkout (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    order_id character varying(36),
    user_id character varying(36) NOT NULL,
    status character varying(20) NOT NULL,
    payment_method character varying(20) NOT NULL,
    payment_provider character varying(30) NOT NULL,
    shipping_method character varying(20) NOT NULL,
    coupon_code character varying(100),
    subtotal_amount numeric(19,2) NOT NULL,
    discount_amount numeric(19,2) NOT NULL,
    shipping_fee numeric(19,2) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    submitted_at timestamp without time zone,
    expired_at timestamp without time zone,
    address_id character varying(36),
    recipient_email character varying(255),
    recipient_name character varying(120),
    recipient_phone character varying(20),
    province character varying(100),
    district character varying(100),
    ward character varying(100),
    detail_address character varying(255),
    district_id integer,
    ward_code character varying(20),
    province_id integer,
    ward_id integer,
    version bigint DEFAULT 0 NOT NULL
);

CREATE TABLE public.checkout_item (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    checkout_id character varying(36) NOT NULL,
    variant_id character varying(36) NOT NULL,
    product_name character varying(255) NOT NULL,
    size_name character varying(20),
    color_name character varying(50),
    unit_price numeric(19,2) NOT NULL,
    quantity integer NOT NULL,
    line_total numeric(19,2) NOT NULL,
    cart_item_id character varying(36),
    product_id character varying(36),
    category_id character varying(36)
);

CREATE TABLE public.coupon_usage (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    promotion_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    used_at timestamp without time zone NOT NULL,
    status character varying(20) NOT NULL
);

CREATE TABLE public.order_item (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    order_id character varying(36) NOT NULL,
    variant_id character varying(36) NOT NULL,
    product_name character varying(255) NOT NULL,
    size_name character varying(20),
    color_name character varying(50),
    unit_price numeric(19,2) NOT NULL,
    quantity integer NOT NULL,
    line_total numeric(19,2) NOT NULL,
    cart_item_id character varying(36)
);

CREATE TABLE public.order_saga (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    status character varying(20) NOT NULL,
    current_step character varying(30) NOT NULL,
    inventory_reservation_id character varying(36),
    payment_id character varying(36),
    failure_code character varying(60),
    failure_reason character varying(500),
    retry_count integer DEFAULT 0 NOT NULL,
    step_deadline timestamp without time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    completed_at timestamp without time zone,
    type character varying(20) DEFAULT 'CHECKOUT'::character varying NOT NULL,
    request_operation_id character varying(160),
    refund_id character varying(120),
    refund_ledger_entry_id character varying(36)
);

CREATE TABLE public.order_status_history (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    from_status character varying(30),
    to_status character varying(30) NOT NULL,
    action character varying(100),
    changed_by character varying(120) NOT NULL,
    reason character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    details_json text
);

CREATE TABLE public.orders (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    order_code character varying(30) NOT NULL,
    user_id character varying(36) NOT NULL,
    status character varying(30) NOT NULL,
    recipient_name character varying(120) NOT NULL,
    recipient_phone character varying(20) NOT NULL,
    shipping_address character varying(500) NOT NULL,
    shipping_provider character varying(100),
    tracking_code character varying(100),
    subtotal_amount numeric(19,2) NOT NULL,
    discount_amount numeric(19,2) NOT NULL,
    shipping_fee numeric(19,2) NOT NULL,
    total_amount numeric(19,2) NOT NULL,
    idempotency_key character varying(120) NOT NULL,
    payment_id character varying(36),
    checkout_id character varying(36),
    currency character varying(3) DEFAULT 'VND'::character varying NOT NULL,
    payment_method character varying(20),
    payment_provider character varying(30),
    cancel_reason character varying(500),
    recipient_email character varying(255),
    client_ip character varying(45),
    payment_url character varying(1000),
    province character varying(100),
    district character varying(100),
    ward character varying(100),
    detail_address character varying(255),
    district_id integer,
    ward_code character varying(20),
    province_id integer,
    ward_id integer,
    shipment_initiation_token character varying(36),
    shipment_initiation_started_at timestamp with time zone,
    delivered_at timestamp with time zone,
    completed_at timestamp with time zone,
    completion_source character varying(20),
    delivery_evidence_issue character varying(500),
    delivery_notified_at timestamp with time zone
);

CREATE TABLE public.outbox_event (
    id character varying(36) NOT NULL,
    event_type character varying(300) NOT NULL,
    routing_key character varying(120) NOT NULL,
    payload text NOT NULL,
    status character varying(20) NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp without time zone NOT NULL,
    published_at timestamp without time zone,
    last_error character varying(1000),
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    aggregate_id character varying(36)
);

CREATE TABLE public.processed_message (
    id character varying(36) NOT NULL,
    message_id character varying(255) NOT NULL,
    consumer_name character varying(120) NOT NULL,
    processed_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255)
);

CREATE TABLE public.promotion (
    id character varying(36) NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    code character varying(50) NOT NULL,
    type character varying(20) NOT NULL,
    value numeric(19,2) NOT NULL,
    max_discount numeric(19,2),
    min_order_value numeric(19,2),
    start_at timestamp without time zone NOT NULL,
    end_at timestamp without time zone NOT NULL,
    total_quota integer,
    per_user_quota integer,
    scope_type character varying(20) NOT NULL,
    scope_ids text,
    active boolean DEFAULT true NOT NULL
);

CREATE TABLE public.return_request (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    reason text NOT NULL,
    images text,
    status character varying(30) NOT NULL,
    reject_reason character varying(500),
    reviewed_by character varying(120),
    reviewed_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    approved_refund_amount numeric(19,2),
    refund_ledger_entry_id character varying(255),
    received_at timestamp with time zone,
    approved_at timestamp with time zone,
    closed_at timestamp with time zone,
    received_by character varying(120),
    qc_passed boolean,
    qc_note character varying(500),
    qc_evidence_ids text,
    refund_started_at timestamp with time zone,
    refund_failure_reason character varying(500),
    kind character varying(30) DEFAULT 'CUSTOMER_RETURN'::character varying NOT NULL,
    verification_by character varying(120),
    verification_evidence_ids text
);

CREATE TABLE public.return_shipment (
    id character varying(36) NOT NULL,
    return_request_id character varying(36) NOT NULL,
    ghn_order_code character varying(100) NOT NULL,
    status character varying(30) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    picked_at timestamp with time zone,
    arrived_at timestamp with time zone,
    cancelled_at timestamp with time zone,
    last_ghn_status character varying(40),
    cancel_attempts integer DEFAULT 0 NOT NULL,
    last_reconciled_at timestamp with time zone,
    failure_code character varying(40),
    failure_reason character varying(500),
    failed_at timestamp with time zone,
    pickup_requested_at timestamp with time zone
);

CREATE TABLE public.shipment (
    id character varying(36) NOT NULL,
    order_id character varying(36) NOT NULL,
    provider character varying(50) NOT NULL,
    tracking_code character varying(100),
    ghn_order_code character varying(100),
    to_district_id integer,
    to_ward_code character varying(20),
    fee numeric(19,2) DEFAULT 0 NOT NULL,
    weight_gram integer,
    status character varying(50) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cod_status character varying(20) DEFAULT 'PENDING'::character varying,
    cod_amount numeric(19,2),
    cod_transferred_at timestamp with time zone,
    pod_url character varying(1000),
    delivered_at timestamp with time zone,
    handed_off_at timestamp with time zone,
    cod_collected_at timestamp with time zone
);

ALTER TABLE ONLY public.cart_item
    ADD CONSTRAINT cart_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cart
    ADD CONSTRAINT cart_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.cart
    ADD CONSTRAINT cart_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.checkout_item
    ADD CONSTRAINT checkout_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.checkout
    ADD CONSTRAINT checkout_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.checkout
    ADD CONSTRAINT checkout_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.coupon_usage
    ADD CONSTRAINT coupon_usage_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.coupon_usage
    ADD CONSTRAINT coupon_usage_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT order_item_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_saga
    ADD CONSTRAINT order_saga_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT order_status_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_order_code_key UNIQUE (order_code);

ALTER TABLE ONLY public.orders
    ADD CONSTRAINT orders_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT processed_message_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.promotion
    ADD CONSTRAINT promotion_code_key UNIQUE (code);

ALTER TABLE ONLY public.promotion
    ADD CONSTRAINT promotion_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.return_request
    ADD CONSTRAINT return_request_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.return_request
    ADD CONSTRAINT return_request_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.return_shipment
    ADD CONSTRAINT return_shipment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.shipment
    ADD CONSTRAINT shipment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.order_saga
    ADD CONSTRAINT uk_order_saga_order_type UNIQUE (order_id, type);

ALTER TABLE ONLY public.return_shipment
    ADD CONSTRAINT uk_return_shipment_ghn_order_code UNIQUE (ghn_order_code);

ALTER TABLE ONLY public.return_shipment
    ADD CONSTRAINT uk_return_shipment_return_request UNIQUE (return_request_id);

ALTER TABLE ONLY public.shipment
    ADD CONSTRAINT uk_shipment_order_id UNIQUE (order_id);

ALTER TABLE ONLY public.cart_item
    ADD CONSTRAINT uq_cart_item_cart_variant UNIQUE (cart_id, variant_id);

ALTER TABLE ONLY public.coupon_usage
    ADD CONSTRAINT uq_coupon_usage_promo_user_order UNIQUE (promotion_id, user_id, order_id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT uq_order_processed_message UNIQUE (message_id, consumer_name);

CREATE INDEX idx_checkout_status_created_at ON public.checkout USING btree (status, created_at);

CREATE INDEX idx_coupon_usage_order ON public.coupon_usage USING btree (order_id);

CREATE INDEX idx_coupon_usage_promotion_user ON public.coupon_usage USING btree (promotion_id, user_id);

CREATE INDEX idx_order_saga_due ON public.order_saga USING btree (status, step_deadline);

CREATE INDEX idx_order_status_history_order_id_created_at ON public.order_status_history USING btree (order_id, created_at DESC);

CREATE INDEX idx_orders_receipt_completion ON public.orders USING btree (delivered_at) WHERE ((status)::text = 'DELIVERED'::text);

CREATE INDEX idx_orders_status_updated_at ON public.orders USING btree (status, updated_at);

CREATE INDEX idx_orders_user_created_at ON public.orders USING btree (user_id, created_at DESC);

CREATE INDEX idx_outbox_event_pending ON public.outbox_event USING btree (status, next_attempt_at);

CREATE INDEX idx_promotion_active_end_at ON public.promotion USING btree (active, end_at);

CREATE INDEX idx_promotion_code ON public.promotion USING btree (code);

CREATE INDEX idx_return_request_status ON public.return_request USING btree (status);

CREATE INDEX idx_return_request_user_status ON public.return_request USING btree (user_id, status);

CREATE INDEX idx_return_shipment_open ON public.return_shipment USING btree (status, last_reconciled_at);

CREATE INDEX idx_shipment_status ON public.shipment USING btree (status);

CREATE INDEX idx_shipment_tracking_code ON public.shipment USING btree (tracking_code);

CREATE UNIQUE INDEX uq_orders_checkout_id ON public.orders USING btree (checkout_id) WHERE (checkout_id IS NOT NULL);

CREATE UNIQUE INDEX uq_orders_user_idempotency_key ON public.orders USING btree (user_id, idempotency_key);

ALTER TABLE ONLY public.cart_item
    ADD CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES public.cart(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.checkout_item
    ADD CONSTRAINT fk_checkout_item_checkout FOREIGN KEY (checkout_id) REFERENCES public.checkout(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.coupon_usage
    ADD CONSTRAINT fk_coupon_usage_promotion FOREIGN KEY (promotion_id) REFERENCES public.promotion(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.order_item
    ADD CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.order_saga
    ADD CONSTRAINT fk_order_saga_order FOREIGN KEY (order_id) REFERENCES public.orders(id);

ALTER TABLE ONLY public.order_status_history
    ADD CONSTRAINT fk_order_status_history_order FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.return_request
    ADD CONSTRAINT fk_return_request_order FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.return_shipment
    ADD CONSTRAINT fk_return_shipment_return_request FOREIGN KEY (return_request_id) REFERENCES public.return_request(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.shipment
    ADD CONSTRAINT fk_shipment_order FOREIGN KEY (order_id) REFERENCES public.orders(id) ON DELETE CASCADE;

