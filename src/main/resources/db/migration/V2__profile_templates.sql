alter table accounts add column profile_template varchar(20) not null default 'CLASSIC';
alter table accounts add constraint accounts_profile_template_check
    check (profile_template in ('CLASSIC', 'MINIMAL', 'STUDIO', 'MIDNIGHT'));
