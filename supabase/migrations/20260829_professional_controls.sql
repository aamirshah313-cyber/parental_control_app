-- Guardian Link professional controls: device lifecycle, parent-approved bonus time, and minimal health status.
-- Run after the existing migrations. Safe to re-run in the Supabase SQL Editor.

alter table public.devices add column if not exists retired_at timestamptz;
create index if not exists devices_family_active_idx on public.devices (family_id, created_at desc) where retired_at is null;

alter table public.parent_commands add column if not exists payload jsonb not null default '{}'::jsonb;
alter table public.parent_commands drop constraint if exists parent_commands_command_type_check;
alter table public.parent_commands add constraint parent_commands_command_type_check
  check (command_type in ('pause', 'resume', 'refresh_policy', 'grant_time'));

create table if not exists public.child_time_requests (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  request_type text not null default 'more_time' check (request_type = 'more_time'),
  requested_minutes integer not null check (requested_minutes between 5 and 120),
  status text not null default 'pending' check (status in ('pending', 'granted', 'declined')),
  granted_minutes integer check (granted_minutes is null or granted_minutes between 5 and 120),
  created_at timestamptz not null default now(),
  resolved_at timestamptz
);
create index if not exists child_time_requests_device_status_idx on public.child_time_requests (device_id, status, created_at);
alter table public.child_time_requests enable row level security;
drop policy if exists "Child creates own time requests" on public.child_time_requests;
drop policy if exists "Child reads own time requests" on public.child_time_requests;
drop policy if exists "Parents manage family time requests" on public.child_time_requests;
create policy "Child creates own time requests" on public.child_time_requests for insert with check (
  status = 'pending' and granted_minutes is null and exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child reads own time requests" on public.child_time_requests for select using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Parents manage family time requests" on public.child_time_requests for all using (
  exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid())
) with check (
  exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid())
);

create table if not exists public.device_health (
  device_id uuid primary key references public.devices(id) on delete cascade,
  battery_percent integer check (battery_percent between 0 and 100),
  protection_active boolean not null default false,
  usage_access_available boolean not null default false,
  screen_minutes_today integer not null default 0 check (screen_minutes_today >= 0),
  reported_at timestamptz not null default now()
);
alter table public.device_health enable row level security;
drop policy if exists "Child writes own health" on public.device_health;
drop policy if exists "Child updates own health" on public.device_health;
drop policy if exists "Child reads own health" on public.device_health;
drop policy if exists "Parents read family health" on public.device_health;
create policy "Child writes own health" on public.device_health for insert with check (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child updates own health" on public.device_health for update using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
) with check (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child reads own health" on public.device_health for select using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Parents read family health" on public.device_health for select using (
  exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid())
);
