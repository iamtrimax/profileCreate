create table accounts (
    id uuid primary key,
    email varchar(254) not null unique,
    password_hash varchar(100) not null,
    username varchar(30) not null unique,
    display_name varchar(100) not null,
    bio varchar(2000) not null default '',
    published boolean not null default false,
    created_at timestamp with time zone not null
);
create table social_links (
    id uuid primary key,
    owner_id uuid not null references accounts(id) on delete cascade,
    title varchar(100) not null,
    url varchar(2000) not null,
    sort_order integer not null default 0
);
create index social_links_owner on social_links(owner_id, sort_order);
create table service_offerings (
    id uuid primary key,
    owner_id uuid not null references accounts(id) on delete cascade,
    title varchar(100) not null,
    description varchar(2000) not null,
    price numeric(15,2) not null check (price >= 0),
    currency varchar(3) not null default 'VND'
);
create index service_offerings_owner on service_offerings(owner_id);
create table contact_requests (
    id uuid primary key,
    owner_id uuid not null references accounts(id) on delete cascade,
    name varchar(100) not null,
    email varchar(254) not null,
    message varchar(4000) not null,
    status varchar(20) not null check (status in ('NEW', 'READ', 'ARCHIVED')),
    created_at timestamp with time zone not null
);
create index contact_requests_owner on contact_requests(owner_id, created_at desc);
create table daily_visits (
    id uuid primary key,
    owner_id uuid not null references accounts(id) on delete cascade,
    visit_date date not null,
    views bigint not null default 0,
    unique(owner_id, visit_date)
);
