import { ErrorHandler, Provider } from '@angular/core';
import { Router } from '@angular/router';
import * as Sentry from '@sentry/angular';

/**
 * Sentry bootstrap for the admin panel. Mirrors the storefront wiring; lives
 * in its own copy so each Angular project has independent DSN + sample rates.
 *
 * To enable: set a build-time `__SENTRY_DSN__` global, a runtime
 * `window.__ENV__.SENTRY_DSN`, or hard-code the DSN below. Blank DSN ⇒ no-op.
 */
const DSN: string =
  (globalThis as any).__SENTRY_DSN__ ??
  (globalThis as any).__ENV__?.SENTRY_DSN ??
  '';

export function initSentry(): void {
  if (!DSN) return;
  Sentry.init({
    dsn: DSN,
    environment: (globalThis as any).__SENTRY_ENV__ ?? 'production',
    release:     (globalThis as any).__SENTRY_RELEASE__,
    integrations: [Sentry.browserTracingIntegration()],
    tracesSampleRate: 0.1,
    sendDefaultPii: false,
  });
}

export function sentryProviders(): Provider[] {
  return [
    {
      provide: ErrorHandler,
      useValue: Sentry.createErrorHandler({ showDialog: false }),
    },
    {
      provide: Sentry.TraceService,
      deps: [Router],
    },
  ];
}
