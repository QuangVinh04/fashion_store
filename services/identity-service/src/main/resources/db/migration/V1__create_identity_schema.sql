
CREATE TABLE public.outbox_event (
    id character varying(36) NOT NULL,
    routing_key character varying(120) NOT NULL,
    payload text NOT NULL,
    published_at timestamp without time zone,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp without time zone DEFAULT now() NOT NULL,
    last_error character varying(1000)
);

CREATE TABLE public.permissions (
    id character varying(36) NOT NULL,
    name character varying(120) NOT NULL,
    description character varying(255)
);

CREATE TABLE public.role_permissions (
    role_id character varying(36) NOT NULL,
    permission_id character varying(36) NOT NULL
);

CREATE TABLE public.roles (
    id character varying(36) NOT NULL,
    name character varying(50) NOT NULL,
    description character varying(255)
);

CREATE TABLE public.user_addresses (
    id character varying(36) NOT NULL,
    user_id character varying(36) NOT NULL,
    recipient_name character varying(120) NOT NULL,
    phone character varying(20) NOT NULL,
    province character varying(100) NOT NULL,
    district character varying(100),
    ward character varying(100) NOT NULL,
    detail_address character varying(255) NOT NULL,
    is_default boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    district_id integer,
    ward_code character varying(20),
    province_id integer,
    ward_id integer
);

CREATE TABLE public.user_roles (
    user_id character varying(36) NOT NULL,
    role_id character varying(36) NOT NULL
);

CREATE TABLE public.users (
    id character varying(36) NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255),
    full_name character varying(255) NOT NULL,
    phone character varying(30),
    address character varying(500),
    avatar character varying(1000),
    is_active boolean DEFAULT true NOT NULL,
    is_email_verified boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    created_by character varying(255),
    updated_by character varying(255),
    avatar_media_id character varying(36),
    avatar_revision bigint DEFAULT 0 NOT NULL
);

ALTER TABLE ONLY public.outbox_event
    ADD CONSTRAINT outbox_event_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_name_key UNIQUE (name);

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_name_key UNIQUE (name);

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_addresses
    ADD CONSTRAINT user_addresses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_pkey PRIMARY KEY (user_id, role_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE INDEX idx_outbox_event_pending ON public.outbox_event USING btree (status, next_attempt_at);

CREATE INDEX idx_user_addresses_user_default ON public.user_addresses USING btree (user_id, is_default);

CREATE INDEX idx_user_addresses_user_id ON public.user_addresses USING btree (user_id);

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_addresses
    ADD CONSTRAINT user_addresses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_roles
    ADD CONSTRAINT user_roles_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

INSERT INTO public.roles VALUES ('00000000-0000-0000-0000-000000000001', 'USER', 'Store customer');
INSERT INTO public.roles VALUES ('00000000-0000-0000-0000-000000000002', 'ADMIN', 'Store administrator');
