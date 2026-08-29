-- Guardian Link category safety audit event. Run after the existing migrations.
-- The event stores only a category label and timestamp; it never stores URLs or search terms.

alter table public.device_events drop constraint if exists device_events_event_type_check;
alter table public.device_events add constraint device_events_event_type_check check (
  event_type in ('limit_reached', 'schedule_block', 'keyword_block', 'shorts_block', 'category_block',
                 'app_installed', 'location_update', 'safe_place_entered', 'safe_place_exited', 'sos',
                 'protection_status', 'permission_changed')
);
