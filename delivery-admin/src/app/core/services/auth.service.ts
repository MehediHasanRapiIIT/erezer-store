import { Injectable } from '@angular/core';
import { KeycloakService } from './keycloak.service';

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private keycloak: KeycloakService) {}

  isAuthenticated(): boolean {
    return this.keycloak.isAuthenticated();
  }

  getToken(): Promise<string | null> {
    return this.keycloak.getToken();
  }

  login(): Promise<void> {
    return this.keycloak.login();
  }

  logout(): Promise<void> {
    return this.keycloak.logout();
  }

  /** Logged-in user's display profile (name, email, initials, role). */
  userProfile(): { name: string; email: string; initials: string; role: string } | null {
    return this.keycloak.getUserProfile();
  }
}
