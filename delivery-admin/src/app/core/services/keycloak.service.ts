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

@Injectable({ providedIn: 'root' })
export class KeycloakService {
  private keycloak = new Keycloak({ url, realm, clientId });

  init(): Promise<boolean> {
    return this.keycloak
      .init({
        onLoad: 'check-sso',
        silentCheckSsoRedirectUri:
          window.location.origin + '/silent-check-sso.html',
      })
      .catch(() => {
        this.login();
        return false;
      });
  }

  isAuthenticated(): boolean {
    return !!this.keycloak.authenticated;
  }

  async getToken(): Promise<string | null> {
    if (!this.isAuthenticated()) {
      return null;
    }
    await this.keycloak.updateToken(30);
    return this.keycloak.token ?? null;
  }

  login(): Promise<void> {
    return this.keycloak.login();
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
