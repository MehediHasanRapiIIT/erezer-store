import { inject, Injectable, signal } from '@angular/core';
import { ApiService } from './api.service';
import { AuthTokenResponse, EmailLoginPayload, RegisterPayload } from './api.models';

const KEY_USER_ID       = 'erezer-userId';
const KEY_ACCESS_TOKEN  = 'erezer-access-token';
const KEY_REFRESH_TOKEN = 'erezer-refresh-token';
const KEY_EMAIL         = 'erezer-email';
const KEY_FIRST_NAME    = 'erezer-firstName';
const KEY_LAST_NAME     = 'erezer-lastName';
const KEY_EMAIL_VERIFIED = 'erezer-emailVerified';

function readStorage(key: string): string | null {
  try { return localStorage.getItem(key); } catch { return null; }
}
function writeStorage(key: string, value: string): void {
  try { localStorage.setItem(key, value); } catch { /* ignore */ }
}
function clearStorage(...keys: string[]): void {
  try { keys.forEach((k) => localStorage.removeItem(k)); } catch { /* ignore */ }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  // ── persistent identity ────────────────────────────────────────────────────
  readonly userId        = signal<string | null>(readStorage(KEY_USER_ID));
  readonly accessToken   = signal<string | null>(readStorage(KEY_ACCESS_TOKEN));
  readonly refreshToken  = signal<string | null>(readStorage(KEY_REFRESH_TOKEN));
  readonly email         = signal<string | null>(readStorage(KEY_EMAIL));
  readonly firstName     = signal<string | null>(readStorage(KEY_FIRST_NAME));
  readonly lastName      = signal<string | null>(readStorage(KEY_LAST_NAME));
  readonly emailVerified = signal<boolean>(readStorage(KEY_EMAIL_VERIFIED) === 'true');
  readonly isAuthenticated = signal<boolean>(!!readStorage(KEY_ACCESS_TOKEN));

  // ── transient UI state ─────────────────────────────────────────────────────
  readonly loading = signal(false);
  readonly error   = signal<string | null>(null);

  constructor() {
    // Keep this tab in sync with auth changes made in OTHER tabs (the `storage`
    // event only fires cross-tab). E.g. verifying email in the link's tab clears
    // the "verify your email" warning here live, without a manual refresh.
    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (e) => {
        if (e.key === KEY_EMAIL_VERIFIED) {
          const verified = readStorage(KEY_EMAIL_VERIFIED) === 'true';
          const wasVerified = this.emailVerified();
          this.emailVerified.set(verified);
          // On the false→true transition, pull a fresh token so this tab's
          // session is verified server-side too (not just the UI flag).
          if (verified && !wasVerified && this.refreshToken()) {
            this.refresh().catch(() => { /* keep existing session */ });
          }
        }
        if (e.key === KEY_ACCESS_TOKEN) {
          const token = readStorage(KEY_ACCESS_TOKEN);
          this.accessToken.set(token);
          this.isAuthenticated.set(!!token);
          if (!token) this.userId.set(null);
        }
      });
    }
  }

  register(payload: RegisterPayload): Promise<AuthTokenResponse> {
    return this.runAuthCall(() => this.api.register(payload), 'Could not create your account.');
  }

  login(payload: EmailLoginPayload): Promise<AuthTokenResponse> {
    return this.runAuthCall(() => this.api.login(payload), 'Invalid email or password.');
  }

  refresh(): Promise<AuthTokenResponse> {
    const rt = this.refreshToken();
    if (!rt) {
      return Promise.reject(new Error('No refresh token.'));
    }
    return this.runAuthCall(() => this.api.refreshToken(rt), 'Session expired. Please sign in again.');
  }

  verifyEmail(token: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    return new Promise((resolve, reject) => {
      this.api.verifyEmail(token).subscribe({
        next: () => {
          this.emailVerified.set(true);
          writeStorage(KEY_EMAIL_VERIFIED, 'true');
          this.loading.set(false);
          // Refresh this tab's token so it's verified server-side, and the
          // localStorage write above notifies other open tabs live.
          if (this.refreshToken()) this.refresh().catch(() => { /* ignore */ });
          resolve();
        },
        error: (err) => {
          this.loading.set(false);
          const msg = err?.error?.message ?? 'Verification link is invalid or expired.';
          this.error.set(msg);
          reject(err);
        },
      });
    });
  }

  resendVerification(emailAddr: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    return new Promise((resolve, reject) => {
      this.api.resendVerification(emailAddr).subscribe({
        next: () => { this.loading.set(false); resolve(); },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? 'Could not resend the verification email.');
          reject(err);
        },
      });
    });
  }

  forgotPassword(emailAddr: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    return new Promise((resolve, reject) => {
      this.api.forgotPassword(emailAddr).subscribe({
        next: () => { this.loading.set(false); resolve(); },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? 'Could not start password reset.');
          reject(err);
        },
      });
    });
  }

  resetPassword(token: string, newPassword: string): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    return new Promise((resolve, reject) => {
      this.api.resetPassword(token, newPassword).subscribe({
        next: () => { this.loading.set(false); resolve(); },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? 'Reset link is invalid or expired.');
          reject(err);
        },
      });
    });
  }

  signOut(): void {
    this.userId.set(null);
    this.accessToken.set(null);
    this.refreshToken.set(null);
    this.email.set(null);
    this.firstName.set(null);
    this.lastName.set(null);
    this.emailVerified.set(false);
    this.isAuthenticated.set(false);
    clearStorage(
      KEY_USER_ID, KEY_ACCESS_TOKEN, KEY_REFRESH_TOKEN,
      KEY_EMAIL, KEY_FIRST_NAME, KEY_LAST_NAME, KEY_EMAIL_VERIFIED,
    );
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private runAuthCall(
    call: () => import('rxjs').Observable<AuthTokenResponse>,
    fallbackMsg: string,
  ): Promise<AuthTokenResponse> {
    this.loading.set(true);
    this.error.set(null);
    return new Promise((resolve, reject) => {
      call().subscribe({
        next: (res) => {
          this.applyTokens(res);
          this.loading.set(false);
          resolve(res);
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(err?.error?.message ?? fallbackMsg);
          reject(err);
        },
      });
    });
  }

  private applyTokens(res: AuthTokenResponse): void {
    this.userId.set(res.userId);
    this.accessToken.set(res.accessToken);
    this.refreshToken.set(res.refreshToken);
    this.email.set(res.email);
    this.firstName.set(res.firstName);
    this.lastName.set(res.lastName);
    this.emailVerified.set(res.emailVerified);
    this.isAuthenticated.set(true);

    writeStorage(KEY_USER_ID, res.userId);
    writeStorage(KEY_ACCESS_TOKEN, res.accessToken);
    writeStorage(KEY_REFRESH_TOKEN, res.refreshToken);
    writeStorage(KEY_EMAIL, res.email);
    writeStorage(KEY_FIRST_NAME, res.firstName ?? '');
    writeStorage(KEY_LAST_NAME, res.lastName ?? '');
    writeStorage(KEY_EMAIL_VERIFIED, String(res.emailVerified));
  }
}
