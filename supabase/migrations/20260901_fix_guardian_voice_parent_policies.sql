-- Fixes a real bug found while investigating a reported "audio messages don't work" complaint:
-- the parent-side storage.objects policies for the guardian-voice bucket never actually worked.
--
-- 20260829_family_chat_voice.sql wrote `(storage.foldername(name))[1]` inside
-- `exists (select 1 from public.devices d join public.families f on f.id = d.family_id
-- where ... )`. Postgres resolves the bare `name` against the innermost scope first, and
-- `families` (aliased f) has its own `name` column (the family's display name, e.g. "Smith
-- Family") -- so `name` silently bound to `f.name` instead of the intended
-- `storage.objects.name` (the uploaded object's path, e.g. "<device-id>/<uuid>.m4a"). A family
-- display name never matches a device UUID, so these two policies always evaluated false: a
-- parent could never read or upload a voice note, while the child-side policies (whose subquery
-- only has `devices`, which has no `name` column, so there is no local match to shadow it) were
-- unaffected and worked correctly. This is the same class of scoping bug already fixed for
-- family_messages/family_notifications in 20260831_fix_family_delivery_scope_drift.sql.

drop policy if exists "Parents read guardian voice" on storage.objects;
drop policy if exists "Parents upload guardian voice" on storage.objects;

create policy "Parents read guardian voice" on storage.objects for select to authenticated using (
  bucket_id = 'guardian-voice' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id::text = (storage.foldername(objects.name))[1] and f.owner_id = auth.uid()
  )
);
create policy "Parents upload guardian voice" on storage.objects for insert to authenticated with check (
  bucket_id = 'guardian-voice' and exists (
    select 1 from public.devices d join public.families f on f.id = d.family_id
    where d.id::text = (storage.foldername(objects.name))[1] and f.owner_id = auth.uid()
  )
);
