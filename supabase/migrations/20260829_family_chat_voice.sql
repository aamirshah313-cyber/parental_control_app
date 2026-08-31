-- Guardian Link typed family chat and private voice notes. Run after the existing family quick-message migration.
-- Messages and audio remain accessible only to the paired child and the owning parent account.

create table if not exists public.family_chat_messages (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  sender_role text not null check (sender_role in ('parent', 'child')),
  body text not null default '' check (char_length(body) <= 600),
  audio_path text check (char_length(audio_path) between 1 and 300),
  message_kind text not null default 'chat' check (message_kind in ('chat', 'quick_update')),
  template_key text,
  created_at timestamptz not null default now(),
  check (char_length(body) > 0 or audio_path is not null),
  check (audio_path is null or audio_path like device_id::text || '/%')
);
create index if not exists family_chat_messages_device_created_idx on public.family_chat_messages (device_id, created_at desc);
create index if not exists family_chat_messages_device_kind_created_idx on public.family_chat_messages (device_id, message_kind, created_at desc);
alter table public.family_chat_messages enable row level security;
revoke all on public.family_chat_messages from anon;
grant select, insert on public.family_chat_messages to authenticated;

drop policy if exists "Parents read family chat" on public.family_chat_messages;
drop policy if exists "Parents send family chat" on public.family_chat_messages;
drop policy if exists "Child reads own family chat" on public.family_chat_messages;
drop policy if exists "Child sends own family chat" on public.family_chat_messages;
create policy "Parents read family chat" on public.family_chat_messages for select using (
  exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and f.owner_id = auth.uid())
);
create policy "Parents send family chat" on public.family_chat_messages for insert with check (
  sender_role = 'parent' and exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id = device_id and d.family_id = family_id and f.owner_id = auth.uid())
);
create policy "Child reads own family chat" on public.family_chat_messages for select using (
  exists (select 1 from public.devices d where d.id = device_id and d.child_auth_user_id = auth.uid())
);
create policy "Child sends own family chat" on public.family_chat_messages for insert with check (
  sender_role = 'child' and exists (select 1 from public.devices d where d.id = device_id and d.family_id = family_id and d.child_auth_user_id = auth.uid())
);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('guardian-voice', 'guardian-voice', false, 5242880, array['audio/mp4'])
on conflict (id) do update set public = false, file_size_limit = 5242880, allowed_mime_types = array['audio/mp4'];

drop policy if exists "Parents read guardian voice" on storage.objects;
drop policy if exists "Parents upload guardian voice" on storage.objects;
drop policy if exists "Child reads guardian voice" on storage.objects;
drop policy if exists "Child uploads guardian voice" on storage.objects;
create policy "Parents read guardian voice" on storage.objects for select to authenticated using (
  bucket_id = 'guardian-voice' and exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id::text = (storage.foldername(name))[1] and f.owner_id = auth.uid())
);
create policy "Parents upload guardian voice" on storage.objects for insert to authenticated with check (
  bucket_id = 'guardian-voice' and exists (select 1 from public.devices d join public.families f on f.id = d.family_id where d.id::text = (storage.foldername(name))[1] and f.owner_id = auth.uid())
);
create policy "Child reads guardian voice" on storage.objects for select to authenticated using (
  bucket_id = 'guardian-voice' and exists (select 1 from public.devices d where d.id::text = (storage.foldername(name))[1] and d.child_auth_user_id = auth.uid())
);
create policy "Child uploads guardian voice" on storage.objects for insert to authenticated with check (
  bucket_id = 'guardian-voice' and exists (select 1 from public.devices d where d.id::text = (storage.foldername(name))[1] and d.child_auth_user_id = auth.uid())
);
