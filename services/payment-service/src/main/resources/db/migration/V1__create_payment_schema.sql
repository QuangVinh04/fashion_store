
CREATE TABLE public.cancelled_payment_order (
    order_id character varying(255) NOT NULL,
    cancelled_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE public.outbox_event (
    id character varying(255) NOT NULL,
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
    aggregate_id character varying(255)
);

CREATE TABLE public.payment (
    id character varying(255) NOT NULL,
    order_id character varying(255),
    user_id character varying(255) NOT NULL,
    method character varying(20) NOT NULL,
    provider character varying(30) NOT NULL,
    status character varying(20) NOT NULL,
    amount numeric(19,2) NOT NULL,
    provider_amount numeric(19,2),
    provider_currency character varying(3),
    transaction_id character varying(120),
    merchant_reference character varying(100),
    failure_reason character varying(500),
    paid_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    saga_id character varying(36),
    currency character varying(3),
    provider_transaction_date character varying(14),
    payment_url character varying(2048),
    initiation_token character varying(36),
    initiation_started_at timestamp without time zone,
    initiation_attempts integer DEFAULT 0 NOT NULL,
    client_ip character varying(45),
    last_reconciled_at timestamp without time zone,
    type character varying(20) DEFAULT 'ORDER'::character varying NOT NULL,
    operation_id character varying(160),
    delivery_recorded_at timestamp with time zone,
    authorization_expires_at timestamp with time zone,
    CONSTRAINT ck_payment_type_order CHECK (((((type)::text = 'ORDER'::text) AND (order_id IS NOT NULL)) OR (((type)::text = 'TOP_UP'::text) AND (order_id IS NULL) AND ((method)::text = 'ONLINE'::text) AND ((provider)::text = 'PAYOS'::text))))
);

CREATE TABLE public.payment_refund (
    id character varying(255) NOT NULL,
    payment_id character varying(255) NOT NULL,
    order_id character varying(255) NOT NULL,
    amount numeric(19,2) NOT NULL,
    provider character varying(30) NOT NULL,
    status character varying(20) NOT NULL,
    provider_refund_id character varying(120),
    idempotency_key character varying(120) NOT NULL,
    reason character varying(500),
    failure_reason character varying(500),
    completed_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    business_refund_id character varying(160),
    destination character varying(20) DEFAULT 'PROVIDER'::character varying NOT NULL,
    reason_type character varying(40),
    ledger_entry_id character varying(255),
    attempt_generation integer DEFAULT 0,
    source_id character varying(160),
    currency character varying(3) DEFAULT 'VND'::character varying NOT NULL,
    budget_reserved boolean DEFAULT true NOT NULL,
    CONSTRAINT ck_payment_refund_amount_positive CHECK ((amount > (0)::numeric))
);

CREATE SEQUENCE public.payos_reference_seq
    START WITH 5000000000000000
    INCREMENT BY 1
    NO MINVALUE
    MAXVALUE 9007199254740991
    CACHE 1;

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

CREATE TABLE public.wallet_account (
    id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    currency character varying(3) DEFAULT 'VND'::character varying NOT NULL,
    balance bigint DEFAULT 0 NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT wallet_account_balance_check CHECK ((balance >= 0)),
    CONSTRAINT wallet_account_currency_check CHECK (((currency)::text = 'VND'::text))
);

CREATE TABLE public.wallet_ledger_entry (
    id character varying(255) NOT NULL,
    account_id character varying(255) NOT NULL,
    user_id character varying(255) NOT NULL,
    direction character varying(6) NOT NULL,
    amount bigint NOT NULL,
    balance_after bigint NOT NULL,
    source_type character varying(50) NOT NULL,
    source_id character varying(120) NOT NULL,
    operation_id character varying(160) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT wallet_ledger_entry_amount_check CHECK ((amount > 0)),
    CONSTRAINT wallet_ledger_entry_balance_after_check CHECK ((balance_after >= 0)),
    CONSTRAINT wallet_ledger_entry_direction_check CHECK (((direction)::text = ANY ((ARRAY['CREDIT'::character varying, 'DEBIT'::character varying])::text[])))
);

ALTER TABLE ONLY public.cancelled_payment_order
    ADD CONSTRAINT cancelled_payment_order_pkey PRIMARY KEY (order_id);

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_merchant_reference_key UNIQUE (merchant_reference);

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_order_id_key UNIQUE (order_id);

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payment_refund
    ADD CONSTRAINT payment_refund_business_refund_id_key UNIQUE (business_refund_id);

ALTER TABLE ONLY public.payment_refund
    ADD CONSTRAINT payment_refund_idempotency_key_key UNIQUE (idempotency_key);

ALTER TABLE ONLY public.payment_refund
    ADD CONSTRAINT payment_refund_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.payment
    ADD CONSTRAINT payment_transaction_id_key UNIQUE (transaction_id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT processed_message_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT uk_processed_message UNIQUE (message_id, consumer_name);

ALTER TABLE ONLY public.wallet_account
    ADD CONSTRAINT wallet_account_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.wallet_account
    ADD CONSTRAINT wallet_account_user_id_key UNIQUE (user_id);

ALTER TABLE ONLY public.wallet_ledger_entry
    ADD CONSTRAINT wallet_ledger_entry_operation_id_key UNIQUE (operation_id);

ALTER TABLE ONLY public.wallet_ledger_entry
    ADD CONSTRAINT wallet_ledger_entry_pkey PRIMARY KEY (id);

CREATE INDEX idx_outbox_event_publishable ON public.outbox_event USING btree (status, next_attempt_at, created_at);

CREATE INDEX idx_payment_reconciliation ON public.payment USING btree (status, last_reconciled_at);

CREATE INDEX idx_payment_refund_order_id ON public.payment_refund USING btree (order_id);

CREATE INDEX idx_payment_refund_payment_id ON public.payment_refund USING btree (payment_id);

CREATE INDEX idx_payment_saga_id ON public.payment USING btree (saga_id);

CREATE INDEX idx_payment_user_id ON public.payment USING btree (user_id);

CREATE INDEX idx_processed_message_consumer ON public.processed_message USING btree (consumer_name, processed_at);

CREATE INDEX idx_wallet_ledger_user_created ON public.wallet_ledger_entry USING btree (user_id, created_at DESC, id);

CREATE UNIQUE INDEX uq_payment_user_operation ON public.payment USING btree (user_id, operation_id) WHERE (operation_id IS NOT NULL);

ALTER TABLE ONLY public.payment_refund
    ADD CONSTRAINT payment_refund_payment_id_fkey FOREIGN KEY (payment_id) REFERENCES public.payment(id);

ALTER TABLE ONLY public.wallet_ledger_entry
    ADD CONSTRAINT wallet_ledger_entry_account_id_fkey FOREIGN KEY (account_id) REFERENCES public.wallet_account(id);

