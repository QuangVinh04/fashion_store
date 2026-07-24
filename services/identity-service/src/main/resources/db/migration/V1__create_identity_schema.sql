create table permissions (
    id varchar(36) primary key,
    name varchar(120) not null unique,
    description varchar(255)
);

create table roles (
    id varchar(36) primary key,
    name varchar(50) not null unique,
    description varchar(255)
);

create table role_permissions (
    role_id varchar(36) not null references roles(id) on delete cascade,
    permission_id varchar(36) not null references permissions(id) on delete cascade,
    primary key (role_id, permission_id)
);

create table users (
    id varchar(36) primary key,
    email varchar(255) not null unique,
    password varchar(255) not null,
    full_name varchar(255) not null,
    phone varchar(30),
    address varchar(500),
    avatar varchar(1000),
    is_active boolean not null default true,
    is_email_verified boolean not null default false,
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

create table user_roles (
    user_id varchar(36) not null references users(id) on delete cascade,
    role_id varchar(36) not null references roles(id) on delete cascade,
    primary key (user_id, role_id)
);

create table verification_tokens (
    id varchar(36) primary key,
    token varchar(255) not null unique,
    user_id varchar(36) not null unique references users(id) on delete cascade,
    expires_at timestamp not null,
    used boolean not null default false,
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

create table outbox_event (
    id varchar(36) primary key,
    routing_key varchar(120) not null,
    payload text not null,
    published_at timestamp,
    created_at timestamp,
    updated_at timestamp,
    created_by varchar(255),
    updated_by varchar(255)
);

insert into roles(id, name, description)
values
    ('00000000-0000-0000-0000-000000000001', 'USER', 'Store customer'),
    ('00000000-0000-0000-0000-000000000002', 'ADMIN', 'Store administrator')
on conflict (name) do nothing;
