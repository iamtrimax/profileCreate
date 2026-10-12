create table candidate_profiles (
 owner_id uuid primary key references accounts(id) on delete cascade,
 seeking boolean not null default false,
 desired_role varchar(120) not null, summary varchar(3000) not null,
 experience_years integer not null check (experience_years between 0 and 60),
 location varchar(120) not null, work_mode varchar(20) not null check (work_mode in ('ANY','ONSITE','HYBRID','REMOTE')),
 salary_min bigint not null check (salary_min >= 0), salary_max bigint not null check (salary_max >= salary_min),
 updated_at timestamp with time zone not null default current_timestamp
);
create table candidate_skills (
 owner_id uuid not null references candidate_profiles(owner_id) on delete cascade,
 skill varchar(80) not null, primary key(owner_id,skill)
);
create table recruitment_jobs (
 id uuid primary key, owner_id uuid not null references accounts(id) on delete cascade,
 title varchar(120) not null, company varchar(120) not null, role varchar(120) not null,
 description varchar(6000) not null, min_experience integer not null check (min_experience between 0 and 60),
 location varchar(120) not null, work_mode varchar(20) not null check (work_mode in ('ONSITE','HYBRID','REMOTE')),
 salary_min bigint not null check (salary_min >= 0), salary_max bigint not null check (salary_max >= salary_min),
 open boolean not null default true, created_at timestamp with time zone not null default current_timestamp
);
create index recruitment_jobs_owner on recruitment_jobs(owner_id,created_at);
create table recruitment_job_skills (
 job_id uuid not null references recruitment_jobs(id) on delete cascade,
 skill varchar(80) not null, required boolean not null, primary key(job_id,skill)
);
create table recruitment_matches (
 id uuid primary key, job_id uuid not null references recruitment_jobs(id) on delete cascade,
 candidate_id uuid not null references accounts(id) on delete cascade,
 status varchar(20) not null check (status in ('PRESENTED','SKIPPED','PENDING','ACCEPTED','DECLINED')),
 created_at timestamp with time zone not null default current_timestamp,
 updated_at timestamp with time zone not null default current_timestamp,
 unique(job_id,candidate_id)
);
create index recruitment_matches_candidate on recruitment_matches(candidate_id,status);
create table recruitment_messages (
 id uuid primary key, match_id uuid not null references recruitment_matches(id) on delete cascade,
 sender_id uuid not null references accounts(id) on delete cascade,
 body varchar(2000) not null, created_at timestamp with time zone not null default current_timestamp
);
create index recruitment_messages_match on recruitment_messages(match_id,created_at);
create table recruitment_interviews (
 id uuid primary key, match_id uuid not null references recruitment_matches(id) on delete cascade,
 recruiter_id uuid not null references accounts(id) on delete cascade,
 candidate_id uuid not null references accounts(id) on delete cascade,
 starts_at timestamp with time zone not null, ends_at timestamp with time zone not null,
 meeting_details varchar(2000) not null,
 status varchar(20) not null check (status in ('PENDING','CONFIRMED','DECLINED','CANCELLED')),
 created_at timestamp with time zone not null default current_timestamp,
 check (starts_at < ends_at), check (recruiter_id <> candidate_id)
);
create index recruitment_interviews_recruiter on recruitment_interviews(recruiter_id,starts_at);
create index recruitment_interviews_candidate on recruitment_interviews(candidate_id,starts_at);
