
CREATE TABLE public.in_app_notification (
    id character varying(36) NOT NULL,
    user_id character varying(255) NOT NULL,
    type character varying(64) NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    order_id character varying(255) NOT NULL,
    read_at timestamp with time zone,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);

CREATE TABLE public.processed_message (
    id character varying(255) NOT NULL,
    message_id character varying(255) NOT NULL,
    consumer_name character varying(120) NOT NULL,
    processed_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);

ALTER TABLE ONLY public.in_app_notification
    ADD CONSTRAINT in_app_notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT processed_message_pkey1 PRIMARY KEY (id);

ALTER TABLE ONLY public.processed_message
    ADD CONSTRAINT uk_processed_message UNIQUE (message_id, consumer_name);

CREATE INDEX idx_notification_user_history ON public.in_app_notification USING btree (user_id, created_at DESC, id DESC);

CREATE INDEX idx_notification_user_unread ON public.in_app_notification USING btree (user_id) WHERE (read_at IS NULL);

CREATE INDEX idx_processed_message_consumer ON public.processed_message USING btree (consumer_name, processed_at);

