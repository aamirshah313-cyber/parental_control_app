-- Guardian Link unified family communication.
-- Run after 20260829_family_chat_voice.sql in the Supabase SQL Editor.
-- Quick Updates become a labelled preset within the same protected stream as Family Chat.

alter table public.family_chat_messages
  add column if not exists message_kind text not null default 'chat'
    check (message_kind in ('chat', 'quick_update')),
  add column if not exists template_key text;

create index if not exists family_chat_messages_device_kind_created_idx
  on public.family_chat_messages (device_id, message_kind, created_at desc);

-- Existing rows are ordinary chat messages. New quick updates have a fixed template key,
-- and remain subject to the same parent/child RLS policies as all other family messages.
