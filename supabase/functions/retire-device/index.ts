import { admin, caller, json } from "../_shared/supabase.ts";

type RequestBody = { device_id?: string };

/** Soft-retire a device after verifying that the caller owns its family. Historical safety data remains intact. */
Deno.serve(async (request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);
  const authorization = request.headers.get("Authorization");
  if (!authorization?.startsWith("Bearer ")) return json({ error: "Parent authentication is required" }, 401);
  const { data: authData, error: authError } = await caller(authorization).auth.getUser();
  if (authError || !authData.user) return json({ error: "Invalid parent session" }, 401);
  const body = await request.json() as RequestBody;
  if (!body.device_id) return json({ error: "device_id is required" }, 400);

  const db = admin();
  const { data: device } = await db.from("devices")
    .select("id, family_id, families!inner(owner_id)").eq("id", body.device_id).maybeSingle();
  if (!device || (device.families as { owner_id?: string }).owner_id !== authData.user.id) return json({ error: "Device was not found" }, 404);
  const { error } = await db.from("devices").update({ retired_at: new Date().toISOString() }).eq("id", body.device_id);
  if (error) return json({ error: "Could not retire the device" }, 500);
  return json({ retired: true });
});
