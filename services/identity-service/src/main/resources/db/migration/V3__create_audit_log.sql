CREATE TABLE public.audit_log (
    id character varying(36) NOT NULL,
    event_id character varying(36) NOT NULL,
    source_service character varying(60) NOT NULL,
    actor_id character varying(64) NOT NULL,
    action character varying(80) NOT NULL,
    entity_type character varying(60) NOT NULL,
    entity_id character varying(100) NOT NULL,
    summary character varying(500),
    occurred_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    CONSTRAINT audit_log_pkey PRIMARY KEY (id),
    CONSTRAINT audit_log_event_id_key UNIQUE (event_id)
);

CREATE INDEX idx_audit_log_occurred_at ON public.audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_actor ON public.audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_log_entity ON public.audit_log (entity_type, entity_id);
