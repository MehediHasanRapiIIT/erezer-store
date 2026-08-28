import { runtimeConfig } from './runtime-config';

/**
 * Storefront runtime config.
 *
 * META_PIXEL_ID: your Meta (Facebook) Pixel ID. Blank disables the pixel
 * entirely - no script is injected and no events fire. It is read at runtime
 * from /env.js (see core/runtime-config.ts) so it can be changed per
 * environment without rebuilding the image.
 *
 * The legacy `window.__EREZER_META_PIXEL_ID__` global is still honoured so
 * existing deployments that set it in index.html keep working.
 */
function legacyPixelId(): string | undefined {
  if (typeof window === 'undefined') return undefined;
  return (window as unknown as Record<string, string | undefined>)[
    '__EREZER_META_PIXEL_ID__'
  ];
}

export const META_PIXEL_ID: string = runtimeConfig(
  'META_PIXEL_ID',
  legacyPixelId() ?? '',
);
