import { admin, caller, json, sha256 } from "../_shared/supabase.ts";

type RequestBody = { family_id?: string; child_name?: string; valid_for_seconds?: number };

Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Parent authentication is required" }, 401);

  const { data: authData, error: authError } = await caller(authorization).auth.getUser();
  if (authError || !authData.user) return json({ error: "Invalid parent session" }, 401);
  const body = await request.json() as RequestBody;
  if (!body.family_id || !body.child_name?.trim()) return json({ error: "family_id and child_name are required" }, 400);
  const allowedValidity = [600, 1_800, 3_600, 86_400];
  const validitySeconds = allowedValidity.includes(body.valid_for_seconds ?? 600) ? body.valid_for_seconds! : 600;

  const db = admin();
  const { data: family } = await db.from("families")
    .select("id").eq("id", body.family_id).eq("owner_id", authData.user.id).maybeSingle();
  if (!family) return json({ error: "Family was not found" }, 404);

  // Re-generating a code for an unpaired child must not create duplicate dashboard entries.
  // A paired row is deliberately never reused: two real children may have the same display name.
  const childName = body.child_name.trim();
  const { data: existing } = await db.from("devices")
    .select("id").eq("family_id", family.id).eq("display_name", childName)
    .is("child_auth_user_id", null).is("retired_at", null).order("created_at", { ascending: false }).limit(1).maybeSingle();
  let device = existing;
  if (!device) {
    const { data: created, error: deviceError } = await db.from("devices")
      .insert({ family_id: family.id, display_name: childName, platform: "android" })
      .select("id").single();
    if (deviceError || !created) return json({ error: "Could not create the child device" }, 500);
    device = created;
  }

  const rawCode = crypto.randomUUID().replaceAll("-", "") + crypto.randomUUID().replaceAll("-", "");
  const { error: pairingError } = await db.from("device_pairings").upsert({
    device_id: device.id,
    code_hash: await sha256(rawCode),
    expires_at: new Date(Date.now() + validitySeconds * 1000).toISOString(),
    created_by: authData.user.id,
  }, { onConflict: "device_id" });
  if (pairingError) return json({ error: "Could not create the pairing code" }, 500);

  return json({ device_id: device.id, pair_code: `${device.id}.${rawCode}`, expires_in_seconds: validitySeconds });
});
