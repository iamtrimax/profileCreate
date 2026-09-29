create table notifications (
 id uuid primary key,
 owner_id uuid not null references accounts(id) on delete cascade,
 event_key varchar(160) not null unique,
 kind varchar(40) not null,
 title varchar(200) not null,
 body varchar(4000) not null,
 target varchar(40) not null,
 read_at timestamp with time zone,
 created_at timestamp with time zone not null default current_timestamp
);
create index notifications_owner_created on notifications(owner_id,created_at desc,id);
create index notifications_owner_read on notifications(owner_id,read_at);
create table mail_outbox (
 id uuid primary key,
 notification_id uuid not null references notifications(id) on delete cascade,
 recipient varchar(254) not null,
 subject varchar(200) not null,
 body varchar(6000) not null,
 status varchar(12) not null default 'PENDING' check (status in ('PENDING','SENDING','SENT','FAILED')),
 attempts integer not null default 0 check (attempts >= 0),
 next_attempt_at timestamp with time zone not null default current_timestamp,
 lease_until timestamp with time zone,
 lease_token uuid,
 last_error varchar(200),
 sent_at timestamp with time zone,
 created_at timestamp with time zone not null default current_timestamp,
 unique(notification_id,recipient)
);
create index mail_outbox_due on mail_outbox(status,next_attempt_at,lease_until);
