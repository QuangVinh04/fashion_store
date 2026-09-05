create table media_file (
    id varchar(36) primary key,
    created_at timestamp,
    updated_at timestamp,
    owner_id varchar(64) not null,
    original_filename varchar(255) not null,
    display_name varchar(255) not null,
    stored_filename varchar(255) not null,
    storage_key varchar(500) not null unique,
    content_type varchar(120) not null,
    extension varchar(20),
    size_bytes bigint not null,
    checksum_sha256 varchar(64) not null,
    media_type varchar(20) not null,
    status varchar(20) not null default 'ACTIVE',
    visibility varchar(20) not null default 'PUBLIC',
    alt_text varchar(500),
    folder varchar(255),
    width integer,
    height integer,
    trashed_at timestamp
);

create table media_file_tag (
    file_id varchar(36) not null,
    tag varchar(80) not null,
    constraint fk_media_file_tag_file foreign key (file_id) references media_file (id) on delete cascade,
    constraint uq_media_file_tag unique (file_id, tag)
);

create index idx_media_file_owner_status on media_file(owner_id, status);
create index idx_media_file_type on media_file(media_type);
create index idx_media_file_folder on media_file(folder);
create index idx_media_file_created_at on media_file(created_at);
