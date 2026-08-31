-- Locks every chat and inbox record to the family of its referenced child device.
-- This replaces older hand-run policies whose family_id comparison was ambiguous.

drop policy if exists "Parents send family chat" on public.family_chat_messages;
drop policy if exists "Child sends own family chat" on public.family_chat_messages;
create policy "Parents send family chat" on public.family_chat_messages for insert with check (
  sender_role = 'parent' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = family_chat_messages.device_id and d.family_id = family_chat_messages.family_id and f.owner_id = auth.uid()
  )
);
create policy "Child sends own family chat" on public.family_chat_messages for insert with check (
  sender_role = 'child' and exists (
    select 1 from public.devices d
    where d.id = family_chat_messages.device_id and d.family_id = family_chat_messages.family_id and d.child_auth_user_id = auth.uid()
  )
);

drop policy if exists "Parents notify child" on public.family_notifications;
drop policy if exists "Children notify parent" on public.family_notifications;
drop policy if exists "Parents mark own family notifications read" on public.family_notifications;
drop policy if exists "Children mark own family notifications read" on public.family_notifications;
create policy "Parents notify child" on public.family_notifications for insert with check (
  target_role = 'child' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and f.owner_id = auth.uid()
  )
);
create policy "Children notify parent" on public.family_notifications for insert with check (
  target_role = 'parent' and exists (
    select 1 from public.devices d
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and d.child_auth_user_id = auth.uid()
  )
);
create policy "Parents mark own family notifications read" on public.family_notifications for update using (
  target_role = 'parent' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and f.owner_id = auth.uid()
  )
) with check (
  target_role = 'parent' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and f.owner_id = auth.uid()
  )
);
create policy "Children mark own family notifications read" on public.family_notifications for update using (
  target_role = 'child' and exists (
    select 1 from public.devices d
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and d.child_auth_user_id = auth.uid()
  )
) with check (
  target_role = 'child' and exists (
    select 1 from public.devices d
    where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and d.child_auth_user_id = auth.uid()
  )
);
