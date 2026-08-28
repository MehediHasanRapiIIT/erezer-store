/**
 * Runtime config for the storefront.
 *
 * META_PIXEL_ID: your Meta (Facebook) Pixel ID. Leave blank to disable the
 * pixel entirely (no script is injected, no events fire). Set it here, or
 * override at deploy time via a global `window.__EREZER_META_PIXEL_ID__`
 * defined in index.html so non-dev environments can change it without a rebuild.
 */
function fromWindow(key: string): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return (window as unknown as Record<string, string | undefined>)[key];
}

export const META_PIXEL_ID: string = fromWindow('__EREZER_META_PIXEL_ID__') ?? '';
