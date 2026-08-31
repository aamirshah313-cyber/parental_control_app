-- Guardian Link command delivery queue and policy-application receipt.
-- Run after the existing migrations in the Supabase SQL Editor.
-- Safe to run once on an existing project.

alter table public.parent_commands
  add column if not exists child_processed_status text check (child_processed_status in ('received', 'applied', 'expired', 'failed')),
  add column if not exists child_processed_at timestamptz;

create index if not exists parent_commands_pending_delivery_idx
  on public.parent_commands (device_id, created_at asc)
  where child_processed_at is null;

-- A child completes a command through the narrowly-scoped RPC in the hardening migration.
-- It never receives a direct UPDATE policy on parent command content.

-- Do not allow a child token to acknowledge a command for a different device.
drop policy if exists "Child writes own acknowledgements" on public.device_acknowledgements;
create policy "Child writes own acknowledgements" on public.device_acknowledgements
  for insert with check (
    exists (
      select 1
      from public.devices d
      join public.parent_commands c on c.id = command_id
      where d.id = device_id
        and c.device_id = device_id
        and d.child_auth_user_id = auth.uid()
    )
  );

alter table public.device_health
  add column if not exists applied_policy_version integer check (applied_policy_version >= 0);
