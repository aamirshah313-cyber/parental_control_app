-- Guardian Link recipient-specific in-app notifications.
-- Run after 20260829_unified_family_communication.sql. This keeps message delivery
-- visible even if a phone has muted Android notifications or its app is not open.

create table if not exists public.family_notifications (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  target_role text not null check (target_role in ('parent', 'child')),
  event_type text not null check (event_type in ('chat', 'quick_update', 'sos', 'app_request', 'policy')),
  title text not null check (char_length(title) between 1 and 120),
  body text not null check (char_length(body) between 1 and 600),
  read_at timestamptz,
  created_at timestamptz not null default now(),
  check ((target_role = 'parent') or (target_role = 'child'))
);

create index if not exists family_notifications_recipient_created_idx
  on public.family_notifications (device_id, target_role, created_at desc);

-- The inbox is append-only; recipients may only change the read marker.
create or replace function public.guardian_limit_notification_update()
returns trigger language plpgsql as $$
begin
  if (to_jsonb(new) - 'read_at') is distinct from (to_jsonb(old) - 'read_at') then
    raise exception 'Only read_at may be changed on a family notification';
  end if;
  return new;
end;
$$;

drop trigger if exists family_notifications_read_only on public.family_notifications;
create trigger family_notifications_read_only
  before update on public.family_notifications
  for each row execute function public.guardian_limit_notification_update();

alter table public.family_notifications enable row level security;
revoke all on public.family_notifications from anon;
grant select, insert, update on public.family_notifications to authenticated;

drop policy if exists "Parents read own family notifications" on public.family_notifications;
drop policy if exists "Children read own family notifications" on public.family_notifications;
drop policy if exists "Parents notify child" on public.family_notifications;
drop policy if exists "Children notify parent" on public.family_notifications;
drop policy if exists "Parents mark own family notifications read" on public.family_notifications;
drop policy if exists "Children mark own family notifications read" on public.family_notifications;

create policy "Parents read own family notifications" on public.family_notifications
  for select using (
    target_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid()
    )
  );

create policy "Children read own family notifications" on public.family_notifications
  for select using (
    target_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid()
    )
  );

create policy "Parents notify child" on public.family_notifications
  for insert with check (
    target_role = 'child' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid()
    )
  );

create policy "Children notify parent" on public.family_notifications
  for insert with check (
    target_role = 'parent' and exists (
      select 1 from public.devices d
      where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid()
    )
  );

create policy "Parents mark own family notifications read" on public.family_notifications
  for update using (
    target_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid()
    )
  ) with check (
    target_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid()
    )
  );

create policy "Children mark own family notifications read" on public.family_notifications
  for update using (
    target_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid()
    )
  ) with check (
    target_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid()
    )
  );
