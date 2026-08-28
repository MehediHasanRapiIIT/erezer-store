import { ErrorHandler, Provider } from '@angular/core';
import { Router } from '@angular/router';
import * as Sentry from '@sentry/angular';

/**
 * Sentry bootstrap for the storefront.
 *
 * To enable: set the build-time global `__SENTRY_DSN__` (or replace the
 * `dsn` literal below). The init is a no-op when the DSN is falsy, so the
 * app keeps working with Sentry off.
 *
 * Pick up the build-time DSN from an Angular environment.ts or a runtime
 * `window.__ENV__.SENTRY_DSN` if you prefer not to bake it into the bundle.
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
    // Don't ship PII unless you opt in.
    sendDefaultPii: false,
  });
}

/**
 * Providers wire Sentry's ErrorHandler + router instrumentation into Angular.
 * Always safe to include — when the DSN is blank the init was a no-op and
 * these handlers gracefully do nothing.
 */
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
