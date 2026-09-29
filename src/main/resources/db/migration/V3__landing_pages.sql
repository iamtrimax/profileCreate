alter table accounts drop constraint accounts_profile_template_check;
alter table accounts add constraint accounts_profile_template_check
    check (profile_template in ('CLASSIC', 'MINIMAL', 'STUDIO', 'MIDNIGHT', 'BUSINESS', 'PORTFOLIO', 'CREATOR'));

create table landing_blocks (
    id uuid primary key,
    owner_id uuid not null references accounts(id) on delete cascade,
    type varchar(20) not null check (type in ('ABOUT', 'FEATURE', 'PROCESS', 'PROJECT', 'FAQ')),
    title varchar(120) not null,
    body varchar(3000) not null,
    url varchar(2000) not null default '',
    sort_order integer not null default 0,
    enabled boolean not null default true
);
create index landing_blocks_owner on landing_blocks(owner_id, sort_order);
