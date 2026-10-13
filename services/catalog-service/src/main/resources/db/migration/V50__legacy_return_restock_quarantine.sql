create table legacy_return_restock_quarantine (
 id varchar(36) primary key,created_at timestamp,updated_at timestamp,
 message_id varchar(255) not null unique,order_id varchar(255) not null,
 payload text not null,status varchar(30) not null,received_at timestamptz not null,
 resolved_by varchar(120),resolved_at timestamptz,resolution_reason varchar(500),inventory_operation_id varchar(36)
);
create index idx_legacy_restock_review on legacy_return_restock_quarantine(status,received_at);
