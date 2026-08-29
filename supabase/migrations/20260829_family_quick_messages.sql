-- Guardian Link quick parent/child messages.
-- These are in-app, preset messages—not carrier SMS—and are scoped to one paired child device.

create table if not exists public.family_messages (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  sender_role text not null check (sender_role in ('parent', 'child')),
  template_key text not null check (template_key in (
    'driver_on_way', 'at_pickup', 'running_late', 'parent_call_me',
    'waiting_at_stop', 'reached_school', 'need_pickup', 'child_call_me'
  )),
  body text not null check (char_length(body) between 1 and 180),
  created_at timestamptz not null default now(),
  check (
    (template_key = 'driver_on_way' and sender_role = 'parent' and body = 'Driver is on the way to pick you up from school.') or
    (template_key = 'at_pickup' and sender_role = 'parent' and body = 'I am here for pickup.') or
    (template_key = 'running_late' and sender_role = 'parent' and body = 'I am running late. Please wait safely.') or
    (template_key = 'parent_call_me' and sender_role = 'parent' and body = 'Please call me when you can.') or
    (template_key = 'waiting_at_stop' and sender_role = 'child' and body = 'I am waiting for the driver at the stop.') or
    (template_key = 'reached_school' and sender_role = 'child' and body = 'I have reached school safely.') or
    (template_key = 'need_pickup' and sender_role = 'child' and body = 'I need a pickup, please.') or
    (template_key = 'child_call_me' and sender_role = 'child' and body = 'Please call me when you can.')
  )
);

create index if not exists family_messages_device_created_at_idx
  on public.family_messages (device_id, created_at desc);

alter table public.family_messages enable row level security;
revoke all on public.family_messages from anon;
grant select, insert on public.family_messages to authenticated;

drop policy if exists "Parents read family quick messages" on public.family_messages;
drop policy if exists "Parents send family quick messages" on public.family_messages;
drop policy if exists "Child reads own quick messages" on public.family_messages;
drop policy if exists "Child sends own quick messages" on public.family_messages;

create policy "Parents read family quick messages" on public.family_messages
  for select using (exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = device_id and f.owner_id = auth.uid()
  ));

create policy "Parents send family quick messages" on public.family_messages
  for insert with check (
    sender_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid()
    )
  );

create policy "Child reads own quick messages" on public.family_messages
  for select using (exists (
    select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid()
  ));

create policy "Child sends own quick messages" on public.family_messages
  for insert with check (
    sender_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid()
    )
  );
