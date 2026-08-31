-- Guardian Link: restrict child command completion to a narrowly-scoped RPC.
-- Run after 20260829_command_delivery_queue.sql.

drop policy if exists "Child completes own commands" on public.parent_commands;

create or replace function public.complete_child_command(p_command_id uuid, p_status text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
begin
  if p_status not in ('received', 'applied', 'expired', 'failed') then
    raise exception 'invalid command status';
  end if;

  update public.parent_commands c
  set child_processed_at = now(), child_processed_status = p_status
  where c.id = p_command_id
    and c.child_processed_at is null
    and exists (
      select 1 from public.devices d
      where d.id = c.device_id and d.child_auth_user_id = auth.uid()
    );

  return found;
end;
$$;

revoke all on function public.complete_child_command(uuid, text) from public;
grant execute on function public.complete_child_command(uuid, text) to authenticated;
