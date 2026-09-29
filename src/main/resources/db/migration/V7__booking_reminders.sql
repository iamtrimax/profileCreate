create table booking_reminders (
 booking_id uuid not null references bookings(id) on delete cascade,
 stage varchar(8) not null check (stage in ('DAY','HOUR')),
 notification_id uuid not null unique references notifications(id) on delete cascade,
 expires_at timestamp with time zone not null,
 created_at timestamp with time zone not null default current_timestamp,
 primary key(booking_id,stage)
);
create index bookings_reminder_due on bookings(status,starts_at);
