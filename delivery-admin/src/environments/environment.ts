import { runtimeConfig } from '../app/core/runtime-config';

/**
 * Values are resolved at runtime from /env.js rather than inlined at build
 * time, so the same production image serves every environment. See
 * app/core/runtime-config.ts.
 */
export const environment = {
  apiBaseUrl: runtimeConfig('API_BASE_URL', 'http://localhost:8080'),
};
