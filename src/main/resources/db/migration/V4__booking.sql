alter table accounts add column booking_timezone varchar(64) not null default 'Asia/Ho_Chi_Minh';
alter table service_offerings add column duration_minutes integer not null default 60 check (duration_minutes between 15 and 480);
create table availability_rules (
 id uuid primary key, owner_id uuid not null references accounts(id) on delete cascade,
 day_of_week integer not null check (day_of_week between 1 and 7),
 start_minute integer not null, end_minute integer not null,
 check (start_minute >= 0 and start_minute < end_minute and end_minute <= 1440)
);
create index availability_owner on availability_rules(owner_id, day_of_week);
create table availability_exceptions (
 id uuid primary key, owner_id uuid not null references accounts(id) on delete cascade,
 exception_date date not null, closed boolean not null,
 start_minute integer, end_minute integer,
 unique(owner_id, exception_date),
 check ((closed and start_minute is null and end_minute is null) or
 (not closed and start_minute is not null and end_minute is not null and start_minute >= 0 and start_minute < end_minute and end_minute <= 1440))
);
create table bookings (
 id uuid primary key, owner_id uuid not null references accounts(id) on delete cascade,
 service_id uuid references service_offerings(id) on delete set null,
 service_title varchar(100) not null,
 customer_name varchar(100) not null, customer_email varchar(254) not null, message varchar(2000) not null,
 starts_at timestamp with time zone not null, ends_at timestamp with time zone not null,
 timezone varchar(64) not null,
 status varchar(20) not null check (status in ('PENDING','CONFIRMED','CANCELLED','COMPLETED')),
 created_at timestamp with time zone not null default current_timestamp,
 check (starts_at < ends_at)
);
create index bookings_owner_start on bookings(owner_id, starts_at);
