-- Closes two Supabase security-advisor findings surfaced during the P01-P17 diagnosis pass.
-- (The other two findings the advisor reported are not addressed here: they both concern
-- complete_child_command's SECURITY DEFINER status, which is intentional -- it lets a child JWT
-- update its own parent_commands row without broader table grants -- and the function already
-- enforces d.child_auth_user_id = auth.uid() internally, so a caller can only touch their own
-- device's command.)

-- 1) function_search_path_mutable: this trigger function had no pinned search_path. It never
-- references a schema-qualified object (only to_jsonb/NEW/OLD, all resolved via pg_catalog), so
-- an empty search_path is safe and fully closes the finding per Supabase's own remediation.
alter function public.guardian_limit_notification_update() set search_path = '';

-- 2) anon_security_definer_function_executable: complete_child_command cannot actually be
-- abused by an anon caller (auth.uid() is null for anon, so its internal ownership check never
-- matches a row), but anon never legitimately calls it either -- only an authenticated child JWT
-- does, via the app's own paired-device session. Revoking the anon grant removes the needless
-- attack surface the advisor flagged, without touching the authenticated grant the app depends on.
revoke execute on function public.complete_child_command(uuid, text) from anon;
