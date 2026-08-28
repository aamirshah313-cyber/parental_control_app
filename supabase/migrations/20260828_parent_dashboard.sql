-- Guardian Link parent dashboard expansion. Run after the original schema.sql.
-- This migration preserves the no-paid-API architecture: all data stays in Supabase.

create table if not exists public.device_locations (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  latitude double precision not null check (latitude between -90 and 90),
  longitude double precision not null check (longitude between -180 and 180),
  accuracy_meters real,
  recorded_at timestamptz not null default now()
);

create index if not exists device_locations_device_recorded_at_idx
  on public.device_locations (device_id, recorded_at desc);

alter table public.device_locations enable row level security;

create policy "Child writes own locations" on public.device_locations
  for insert with check (exists (
    select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()
  ));

create policy "Parents read family locations" on public.device_locations
  for select using (exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = device_id and f.owner_id = auth.uid()
  ));

-- Standard mode reports installs and visible location service status. Extend the original narrow event list.
alter table public.device_events drop constraint if exists device_events_event_type_check;
alter table public.device_events add constraint device_events_event_type_check check (
  event_type in ('limit_reached', 'schedule_block', 'keyword_block', 'shorts_block',
                 'app_installed', 'location_update', 'safe_place_entered', 'safe_place_exited', 'sos',
                 'protection_status', 'permission_changed')
);

-- A child session may report only its own last-seen field; it cannot edit its family or identity.
revoke update on public.devices from authenticated;
grant update(last_seen_at) on public.devices to authenticated;
create policy "Child updates own last seen" on public.devices
  for update using (child_auth_user_id = auth.uid())
  with check (child_auth_user_id = auth.uid());
