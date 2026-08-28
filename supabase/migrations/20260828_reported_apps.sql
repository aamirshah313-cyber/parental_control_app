-- Child-reported launchable app inventory and parent approval queue.
-- This avoids Android's broad QUERY_ALL_PACKAGES permission.
create table if not exists public.device_apps (
  id uuid primary key default gen_random_uuid(),
  device_id uuid not null references public.devices(id) on delete cascade,
  package_name text not null check (char_length(package_name) between 1 and 255),
  display_name text not null check (char_length(display_name) between 1 and 160),
  pending_approval boolean not null default false,
  last_reported_at timestamptz not null default now(),
  unique (device_id, package_name)
);

create index if not exists device_apps_device_name_idx on public.device_apps (device_id, display_name);
alter table public.device_apps enable row level security;

drop policy if exists "Child reports own apps" on public.device_apps;
drop policy if exists "Child refreshes own apps" on public.device_apps;
drop policy if exists "Child reads own apps" on public.device_apps;
drop policy if exists "Parents read family apps" on public.device_apps;
create policy "Child reports own apps" on public.device_apps for insert with check (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child refreshes own apps" on public.device_apps for update using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
) with check (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child reads own apps" on public.device_apps for select using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Parents read family apps" on public.device_apps for select using (
  exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid())
);
