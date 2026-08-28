/**
 * Runtime configuration.
 *
 * Angular inlines `environment.ts` into the bundle at build time, so any URL
 * written there pins one image to one environment. Instead, the container
 * writes `/env.js` on startup from its own environment variables and every
 * environment-specific value is read through here. One built image therefore
 * runs unchanged in local, staging and production - the artifact you test is
 * the artifact you deploy.
 *
 * Resolution order:
 *   1. `window.__ENV__` - written by docker-entrypoint.sh into /env.js
 *   2. `public/env.js`  - checked-in dev defaults, used by `ng serve`
 *   3. the `fallback` argument passed at each call site
 */
export interface RuntimeEnv {
  API_BASE_URL?: string;
  KEYCLOAK_URL?: string;
  KEYCLOAK_REALM?: string;
  KEYCLOAK_CLIENT_ID?: string;
  SENTRY_DSN?: string;
  SENTRY_ENV?: string;
  SENTRY_RELEASE?: string;
}

function env(): RuntimeEnv {
  return (globalThis as unknown as { __ENV__?: RuntimeEnv }).__ENV__ ?? {};
}

/**
 * Reads one runtime value, falling back when it is missing or was never
 * substituted. A literal `${API_BASE_URL}` means the entrypoint failed to fill
 * the slot; treating that as absent keeps a misconfigured deploy on the
 * fallback instead of firing requests at a nonsense URL.
 */
export function runtimeConfig<K extends keyof RuntimeEnv>(
  key: K,
  fallback: string,
): string {
  const value = env()[key];
  if (!value || value.startsWith('${')) return fallback;
  return value;
}
