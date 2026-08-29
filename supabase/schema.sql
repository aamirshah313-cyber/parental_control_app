-- Guardian Link: minimal, privacy-preserving control plane.
-- Run this in Supabase SQL Editor before connecting the Android client.
create extension if not exists pgcrypto;

create table public.families (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references auth.users(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 80),
  created_at timestamptz not null default now()
);

create table public.devices (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  child_auth_user_id uuid unique references auth.users(id) on delete set null,
  display_name text not null check (char_length(display_name) between 1 and 80),
  platform text not null default 'android' check (platform in ('android', 'ios')),
  last_seen_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.device_policies (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  version integer not null check (version > 0),
  policy jsonb not null,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  unique (device_id, version)
);

create table public.parent_commands (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  command_type text not null check (command_type in ('pause', 'resume', 'refresh_policy')),
  scope text not null default 'managed_apps',
  expires_at timestamptz,
  created_at timestamptz not null default now()
);

create table public.device_acknowledgements (
  id uuid primary key default gen_random_uuid(),
  command_id uuid not null references public.parent_commands(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  status text not null check (status in ('received', 'applied', 'expired', 'failed')),
  created_at timestamptz not null default now()
);

create table public.device_events (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  event_type text not null check (event_type in ('limit_reached', 'schedule_block', 'keyword_block', 'shorts_block', 'category_block')),
  details jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

-- Pairing secrets are only handled by Edge Functions using the service role.
-- The raw code is never stored in Postgres.
create table public.device_pairings (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null unique references public.devices(id) on delete cascade,
  code_hash text not null,
  expires_at timestamptz not null,
  used_at timestamptz,
  created_by uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

alter table public.families enable row level security;
alter table public.devices enable row level security;
alter table public.device_policies enable row level security;
alter table public.parent_commands enable row level security;
alter table public.device_acknowledgements enable row level security;
alter table public.device_events enable row level security;
alter table public.device_pairings enable row level security;

create policy "Parents manage their families" on public.families
  for all using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create policy "Parents manage family devices" on public.devices
  for all using (exists (select 1 from public.families f where f.id = family_id and f.owner_id = auth.uid()))
  with check (exists (select 1 from public.families f where f.id = family_id and f.owner_id = auth.uid()));
create policy "Child sees own device" on public.devices
  for select using (child_auth_user_id = auth.uid());

create policy "Parents manage policies" on public.device_policies
  for all using (exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid()))
  with check (exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid()));
create policy "Child reads own policies" on public.device_policies
  for select using (exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()));

create policy "Parents issue commands" on public.parent_commands
  for all using (exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid()))
  with check (exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid()));
create policy "Child reads own commands" on public.parent_commands
  for select using (exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()));

create policy "Child writes own acknowledgements" on public.device_acknowledgements
  for insert with check (exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()));
create policy "Parents read acknowledgements" on public.device_acknowledgements
  for select using (exists (select 1 from public.parent_commands c join public.devices d on d.id = c.device_id join public.families f on f.id = d.family_id where c.id = command_id and f.owner_id = auth.uid()));

create policy "Child writes minimal own events" on public.device_events
  for insert with check (exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()));
create policy "Parents read family events" on public.device_events
  for select using (exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid()));

create policy "Parents view their pairing state" on public.device_pairings
  for select using (created_by = auth.uid());

-- Enable Postgres change broadcasts only for the small command/policy tables.
alter publication supabase_realtime add table public.device_policies, public.parent_commands;
