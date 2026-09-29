alter table accounts add column email_verified boolean not null default false;
alter table accounts add column auth_version bigint not null default 0;
create table account_tokens (
 token_hash varchar(64) primary key, owner_id uuid not null references accounts(id) on delete cascade,
 purpose varchar(10) not null check (purpose in ('VERIFY','RESET')),
 expires_at timestamp with time zone not null, used_at timestamp with time zone,
 created_at timestamp with time zone not null default current_timestamp
);
create index account_tokens_owner on account_tokens(owner_id,purpose,created_at);
alter table mail_outbox add column encrypted boolean not null default false;
alter table mail_outbox add column expires_at timestamp with time zone;
create table analytics_events (
 id uuid primary key, owner_id uuid not null references accounts(id) on delete cascade,
 event_type varchar(10) not null check (event_type in ('VIEW','CLICK')),
 event_date date not null, visitor_hash varchar(64),
 referrer_host varchar(253) not null default '',
 link_id uuid, link_title varchar(100),
 created_at timestamp with time zone not null
);
create index analytics_owner_date on analytics_events(owner_id,event_date,event_type);
create index analytics_unique_daily on analytics_events(owner_id,event_date,visitor_hash);
