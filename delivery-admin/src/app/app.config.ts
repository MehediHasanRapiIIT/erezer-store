import { ApplicationConfig, APP_INITIALIZER, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import * as Sentry from '@sentry/angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { KeycloakService } from './core/services/keycloak.service';
import { PermissionService } from './core/services/permission.service';
import { initSentry, sentryProviders } from './core/sentry';

// Initialise Sentry before Angular bootstraps so very early errors are caught.
initSentry();

/**
 * Logs in, then reads the person's permissions before the first page opens,
 * so the page guard, the sidebar and the landing page all know them.
 */
function initKeycloak(keycloakService: KeycloakService, permissions: PermissionService): () => Promise<boolean> {
  return () => keycloakService.init().then(async (ok) => {
    if (ok && keycloakService.isAuthenticated()) {
      await permissions.refresh();
      permissions.startAutoRefresh();
    }
    return ok;
  });
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),
    ...sentryProviders(),
    {
      provide: APP_INITIALIZER,
      useFactory: initKeycloak,
      deps: [KeycloakService, PermissionService],
      multi: true,
    },
    // Force TraceService construction so router-level tracing wires up.
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: () => () => inject(Sentry.TraceService),
    },
  ]
};
