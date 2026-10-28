import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';
import { runtimeConfig } from '../runtime-config';

// Resolved at runtime from /env.js, not baked into the bundle, so one image
// works against any Keycloak. `url` is the address the BROWSER uses; it must
// match Keycloak's KC_HOSTNAME, because that is what lands in the token's
// `iss` claim, which the backend validates against its own issuer-uri.
const url = runtimeConfig('KEYCLOAK_URL', 'http://localhost:9090');
const realm = runtimeConfig('KEYCLOAK_REALM', 'delivery-admin');
const clientId = runtimeConfig('KEYCLOAK_CLIENT_ID', 'delivery-admin-ui');

/** How long to wait for the silent login check before falling back to a redirect. */
const INIT_TIMEOUT_MS = 10_000;

/** The login has run out and can't be renewed: the page is on its way to the login screen. */
export class SessionExpiredError extends Error {
  constructor() {
    super('Your session has expired. Taking you to the login page.');
    this.name = 'SessionExpiredError';
  }
}

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private keycloak = new Keycloak({ url, realm, clientId });
  /** Set once the login page is on its way, so a burst of requests sends you there only once. */
  private redirecting = false;

  init(): Promise<boolean> {
    // keycloak-js waits, with no time limit, for /silent-check-sso.html to report
    // back from a hidden iframe. Anything that stops that page rendering - a
    // proxy's X-Frame-Options, a browser extension - left the app never starting,
    // on a blank page. Past the limit, do the full redirect instead: with a live
    // Keycloak session it returns at once, logged in.
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error('Keycloak silent check timed out')), INIT_TIMEOUT_MS);
    });
    const init = this.keycloak.init({
      onLoad: 'check-sso',
      silentCheckSsoRedirectUri:
        window.location.origin + '/silent-check-sso.html',
    });
    return Promise.race([init, timeout])
      .catch(() => {
        this.login();
        return false;
      })
      .finally(() => clearTimeout(timer));
  }

  isAuthenticated(): boolean {
    return !!this.keycloak.authenticated;
  }

  async getToken(): Promise<string | null> {
    if (!this.isAuthenticated()) {
      return null;
    }
    try {
      await this.keycloak.updateToken(30);
    } catch {
      // The session ran out (left open overnight, or ended on the server): a
      // fresh token can't be had. Go to the login page, which brings you back
      // to this page once you're signed in, rather than letting every request
      // fail until the page is reloaded.
      this.sessionExpired();
      throw new SessionExpiredError();
    }
    return this.keycloak.token ?? null;
  }

  /** Sends you to the login page once, however many requests notice at the same moment. */
  sessionExpired(): void {
    if (this.redirecting) return;
    this.redirecting = true;
    this.keycloak.login().catch(() => { this.redirecting = false; });
  }

  login(): Promise<void> {
    if (this.redirecting) return Promise.resolve();
    this.redirecting = true;
    return this.keycloak.login().catch((e) => { this.redirecting = false; throw e; });
  }

  logout(): Promise<void> {
    // Redirect back to wherever the admin app is actually served (e.g. :4300),
    // which must be registered under the client's "Valid post logout redirect URIs".
    return this.keycloak.logout({ redirectUri: window.location.origin });
  }

  /** Logged-in user's profile, read from the Keycloak access token. */
  getUserProfile(): { name: string; email: string; initials: string; role: string } | null {
    const t = this.keycloak.tokenParsed as Record<string, any> | undefined;
    if (!t) return null;
    const fullName = t['name']
      || [t['given_name'], t['family_name']].filter(Boolean).join(' ')
      || t['preferred_username']
      || t['email']
      || 'User';
    const email = t['email'] || t['preferred_username'] || '';
    const roles: string[] = t['realm_access']?.roles ?? [];
    const role = roles.find(
      (r) => !r.startsWith('default-roles') && !['offline_access', 'uma_authorization'].includes(r),
    );
    const initials = fullName
      .split(' ')
      .filter(Boolean)
      .slice(0, 2)
      .map((p: string) => p[0])
      .join('')
      .toUpperCase() || 'U';
    return {
      name: fullName,
      email,
      initials,
      role: role ? this.prettyRole(role) : 'Administrator',
    };
  }

  private prettyRole(r: string): string {
    return r.replace(/[_-]+/g, ' ').replace(/\b\w/g, (c) => c.toUpperCase());
  }
}
