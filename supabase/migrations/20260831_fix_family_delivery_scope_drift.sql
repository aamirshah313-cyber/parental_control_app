-- Corrects a real bug found during the P01-P17 diagnosis pass, present since the tables were
-- created (20260829_family_quick_messages.sql, 20260830_family_notifications.sql): inside
-- `exists (select 1 from public.devices d where ... and d.family_id = family_id ...)`, the bare
-- `family_id` resolves to the subquery's own devices.family_id (Postgres favors the innermost
-- scope), not the outer family_messages/family_notifications row. That makes the comparison a
-- tautology (d.family_id = d.family_id, always true) and silently drops the intended
-- family-ownership check. This reapplies the same predicate with the outer table explicitly
-- qualified, matching the pattern 20260830_harden_family_delivery.sql already used correctly
-- for family_chat_messages.

drop policy if exists "Parents send family quick messages" on public.family_messages;
drop policy if exists "Child sends own quick messages" on public.family_messages;

create policy "Parents send family quick messages" on public.family_messages
  for insert with check (
    sender_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = family_messages.device_id and d.family_id = family_messages.family_id and f.owner_id = auth.uid()
    )
  );

create policy "Child sends own quick messages" on public.family_messages
  for insert with check (
    sender_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = family_messages.device_id and d.family_id = family_messages.family_id and d.child_auth_user_id = auth.uid()
    )
  );

drop policy if exists "Parents read own family notifications" on public.family_notifications;
drop policy if exists "Children read own family notifications" on public.family_notifications;

create policy "Parents read own family notifications" on public.family_notifications
  for select using (
    target_role = 'parent' and exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and f.owner_id = auth.uid()
    )
  );

create policy "Children read own family notifications" on public.family_notifications
  for select using (
    target_role = 'child' and exists (
      select 1 from public.devices d
      where d.id = family_notifications.device_id and d.family_id = family_notifications.family_id and d.child_auth_user_id = auth.uid()
    )
  );
