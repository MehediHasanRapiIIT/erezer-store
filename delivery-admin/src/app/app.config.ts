import { ApplicationConfig, APP_INITIALIZER, inject, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import * as Sentry from '@sentry/angular';

import { routes } from './app.routes';
import { authInterceptor } from './core/interceptors/auth.interceptor';
import { KeycloakService } from './core/services/keycloak.service';
import { initSentry, sentryProviders } from './core/sentry';

// Initialise Sentry before Angular bootstraps so very early errors are caught.
initSentry();

function initKeycloak(keycloakService: KeycloakService): () => Promise<boolean> {
  return () => keycloakService.init();
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
      deps: [KeycloakService],
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
