import { admin, anonKey, json, sha256, url } from "../_shared/supabase.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

type RequestBody = { pair_code?: string; device_name?: string };

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const body = await request.json() as RequestBody;
  const [deviceId, rawCode] = body.pair_code?.split(".") ?? [];
  if (!deviceId || !rawCode) return json({ error: "The pairing code is invalid" }, 400);

  const db = admin();
  const { data: pairing } = await db.from("device_pairings")
    .select("id, code_hash, expires_at, used_at, device_id")
    .eq("device_id", deviceId).maybeSingle();
  if (!pairing || pairing.used_at || Date.parse(pairing.expires_at) < Date.now()) return json({ error: "The pairing code has expired" }, 410);
  if (await sha256(rawCode) !== pairing.code_hash) return json({ error: "The pairing code is invalid" }, 400);

  const email = `child-${crypto.randomUUID()}@guardian-link.invalid`;
  const password = crypto.randomUUID() + crypto.randomUUID();
  const { data: child, error: childError } = await db.auth.admin.createUser({ email, password, email_confirm: true });
  if (childError || !child.user) return json({ error: "Could not create a child device identity" }, 500);

  const auth = createClient(url, anonKey, { auth: { autoRefreshToken: false, persistSession: false } });
  const { data: sessionData, error: sessionError } = await auth.auth.signInWithPassword({ email, password });
  if (sessionError || !sessionData.session) return json({ error: "Could not sign in the child device" }, 500);

  const update: Record<string, string> = { child_auth_user_id: child.user.id };
  if (body.device_name?.trim()) update.display_name = body.device_name.trim();
  const { error: deviceError } = await db.from("devices").update(update).eq("id", deviceId).is("child_auth_user_id", null);
  if (deviceError) return json({ error: "This device has already been paired" }, 409);
  await db.from("device_pairings").update({ used_at: new Date().toISOString() }).eq("id", pairing.id);

  return json({
    device_id: deviceId,
    access_token: sessionData.session.access_token,
    refresh_token: sessionData.session.refresh_token,
    expires_at: sessionData.session.expires_at,
  });
});
