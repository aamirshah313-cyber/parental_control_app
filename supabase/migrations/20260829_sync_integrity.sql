-- Guardian Link synchronization integrity: permit a paired child to remove only its own stale app rows.
-- Run after 20260828_reported_apps.sql. Safe to re-run.

drop policy if exists "Child removes own stale apps" on public.device_apps;
create policy "Child removes own stale apps" on public.device_apps
  for delete using (
    exists (
      select 1 from public.devices d
      where d.id = device_id and d.child_auth_user_id = auth.uid()
    )
  );
