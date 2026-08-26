import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

export const url = Deno.env.get("SUPABASE_URL")!;
export const anonKey = Deno.env.get("SUPABASE_ANON_KEY")!;
export const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

export const admin = () => createClient(url, serviceRoleKey, {
  auth: { autoRefreshToken: false, persistSession: false },
});

export const caller = (authorization: string) => createClient(url, anonKey, {
  global: { headers: { Authorization: authorization } },
  auth: { autoRefreshToken: false, persistSession: false },
});

export async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

export function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
