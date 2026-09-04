-- Parent-approval workflow for child-side app actions (install / unblock / enable), plus a
-- location-status column so the parent UI can show *why* a location is missing (permission
-- denied, GPS off, offline, ...) instead of only "no location yet".
--
-- Reuses the existing families/devices/auth.users relationships rather than introducing a new
-- identity model, and reuses family_notifications (event_type 'app_request' already exists)
-- for both the parent-facing "new request" alert and the child-facing decision alert, so no
-- duplicate notification pipeline is created.
--
-- Android-privilege note (see docs/FEATURES_APPROVAL_LOCATION_GUEST.md for the full writeup):
-- this app is not a Device Owner/Device Admin, so it cannot silently install, hide, or disable
-- packages at the OS level. "install" approval pre-clears Guardian Link's own approval gate so
-- the app will not be soft-blocked once the child installs it themselves; "unblock"/"enable"
-- lift Guardian Link's own PolicyEngine soft-block (removing from blockedPackages / adding to
-- approvedPackages). None of this table's rows represent an OS-level app-install or app-enable
-- action actually being performed by the app.

create table public.app_action_requests (
  id uuid primary key default gen_random_uuid(),
  family_id uuid not null references public.families(id) on delete cascade,
  device_id uuid not null references public.devices(id) on delete cascade,
  app_name text not null check (char_length(app_name) between 1 and 160),
  package_name text not null check (char_length(package_name) between 1 and 255),
  action text not null check (action in ('install', 'unblock', 'enable')),
  status text not null default 'pending' check (status in ('pending', 'approved', 'denied', 'expired')),
  requested_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours',
  decided_at timestamptz,
  decided_by uuid references auth.users(id) on delete set null
);

create index app_action_requests_device_status_idx on public.app_action_requests (device_id, status, requested_at desc);
create index app_action_requests_family_status_idx on public.app_action_requests (family_id, status, requested_at desc);

-- A decided request is a record, not an editable form: only the decision fields may ever change,
-- and only away from 'pending' -- mirrors guardian_limit_notification_update's append-mostly shape.
create or replace function public.guardian_limit_app_action_request_update()
returns trigger language plpgsql as $$
begin
  if old.status <> 'pending' then
    raise exception 'This request has already been decided';
  end if;
  if (to_jsonb(new) - 'status' - 'decided_at' - 'decided_by') is distinct from (to_jsonb(old) - 'status' - 'decided_at' - 'decided_by') then
    raise exception 'Only status, decided_at, and decided_by may be changed on an app action request';
  end if;
  if new.status not in ('approved', 'denied') then
    raise exception 'A decision must set status to approved or denied';
  end if;
  return new;
end;
$$;

drop trigger if exists app_action_requests_decision_only on public.app_action_requests;
create trigger app_action_requests_decision_only
  before update on public.app_action_requests
  for each row execute function public.guardian_limit_app_action_request_update();

alter table public.app_action_requests enable row level security;
revoke all on public.app_action_requests from anon;
grant select, insert, update on public.app_action_requests to authenticated;

create policy "Child creates own app action requests" on public.app_action_requests
  for insert with check (
    status = 'pending' and decided_at is null and decided_by is null and exists (
      select 1 from public.devices d
      where d.id = app_action_requests.device_id and d.family_id = app_action_requests.family_id and d.child_auth_user_id = auth.uid()
    )
  );

create policy "Child reads own app action requests" on public.app_action_requests
  for select using (
    exists (select 1 from public.devices d where d.id = app_action_requests.device_id and d.child_auth_user_id = auth.uid())
  );

create policy "Parents read family app action requests" on public.app_action_requests
  for select using (
    exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = app_action_requests.device_id and f.owner_id = auth.uid()
    )
  );

create policy "Parents decide family app action requests" on public.app_action_requests
  for update using (
    exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = app_action_requests.device_id and f.owner_id = auth.uid()
    )
  ) with check (
    exists (
      select 1 from public.devices d join public.families f on f.id = d.family_id
      where d.id = app_action_requests.device_id and f.owner_id = auth.uid()
    )
  );

alter table public.device_health add column if not exists location_status text
  check (location_status in ('available', 'waiting', 'permission_denied', 'services_disabled', 'offline', 'unavailable'));
