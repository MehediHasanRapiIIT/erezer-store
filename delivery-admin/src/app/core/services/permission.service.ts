import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { ActivatedRouteSnapshot, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AccessRule } from '../access/admin-pages';

/** The logged-in staff member, from GET /admin/me. */
export interface StaffMe {
  id: string;
  username: string;
  email: string | null;
  name: string;
  role: 'ADMIN' | 'MODERATOR';
  admin: boolean;
  /** Every key for an admin; exactly what was granted for a moderator. */
  permissions: string[];
}

/** One permission with its plain-language name, from GET /admin/permissions. */
export interface PermissionInfo {
  key: string;
  area: string;
  label: string;
  description: string | null;
}

/** How often permissions are re-read while the tab is open and visible. */
const REFRESH_EVERY_MS = 60_000;
/** Returning to the tab re-reads them too, but not more often than this. */
const FOCUS_MIN_GAP_MS = 15_000;

/**
 * What the logged-in person may do in the panel. The backend checks every
 * action on its own; this only decides what to show, so a moderator never
 * meets buttons that would be refused.
 *
 * Permissions are re-read periodically, when the tab regains focus, and after
 * any refusal, so a change made on the Staff page shows up without logging
 * out. If the current page is no longer allowed, the person is moved to the
 * "No access" page.
 */
@Injectable({ providedIn: 'root' })
export class PermissionService {
  private http = inject(HttpClient);
  private router = inject(Router);
  private base = environment.apiBaseUrl;

  readonly me = signal<StaffMe | null>(null);
  /** Why this login cannot use the panel at all (not staff, deactivated), when it cannot. */
  readonly blockedReason = signal<string | null>(null);
  /** True once the first answer has come back, whatever it was. */
  readonly loaded = signal(false);

  private readonly keys = computed(() => new Set(this.me()?.permissions ?? []));
  private readonly catalog = signal<Map<string, PermissionInfo>>(new Map());

  /** Every permission, in display order (as GET /admin/permissions returned them); empty until loaded. */
  readonly catalogList = computed(() => [...this.catalog().values()]);

  readonly isAdmin = computed(() => this.me()?.admin === true);
  readonly roleLabel = computed(() => (this.me() ? (this.me()!.admin ? 'Admin' : 'Moderator') : ''));

  private inflight: Promise<void> | null = null;
  private lastLoadedAt = 0;
  private autoRefreshStarted = false;

  can(key: string): boolean {
    return this.isAdmin() || this.keys().has(key);
  }

  canAny(...keys: string[]): boolean {
    return keys.some((k) => this.can(k));
  }

  canAll(...keys: string[]): boolean {
    return keys.every((k) => this.can(k));
  }

  allows(rule: AccessRule | undefined): boolean {
    if (!rule) return true;
    if (rule.all && !this.canAll(...rule.all)) return false;
    if (rule.any && rule.any.length > 0 && !this.canAny(...rule.any)) return false;
    return true;
  }

  /** The plain-language name of a permission, e.g. "Cancel orders"; the key until names have loaded. */
  label(key: string): string {
    return this.catalog().get(key)?.label ?? key;
  }

  /** Loads permissions once; later calls reuse the answer. */
  ensureLoaded(): Promise<void> {
    return this.loaded() ? Promise.resolve() : this.refresh();
  }

  /** Re-reads permissions now. Concurrent calls share one request. */
  refresh(): Promise<void> {
    if (this.inflight) return this.inflight;
    this.inflight = firstValueFrom(this.http.get<StaffMe>(`${this.base}/admin/me`))
      .then((me) => {
        this.me.set(me);
        this.blockedReason.set(null);
      })
      .catch((err: unknown) => {
        if (err instanceof HttpErrorResponse && err.status === 403) {
          this.me.set(null);
          this.blockedReason.set(err.error?.message ?? 'This login is not set up for the admin panel.');
        }
        // Anything else (server down, network): keep what we had and try again later.
      })
      .finally(() => {
        this.inflight = null;
        this.lastLoadedAt = Date.now();
        this.loaded.set(true);
        this.leavePageIfNoLongerAllowed();
      });
    if (this.catalog().size === 0) this.loadCatalog();
    return this.inflight;
  }

  /** Starts the periodic and on-focus re-reads. Safe to call more than once. */
  startAutoRefresh(): void {
    if (this.autoRefreshStarted) return;
    this.autoRefreshStarted = true;
    window.addEventListener('focus', () => {
      if (Date.now() - this.lastLoadedAt > FOCUS_MIN_GAP_MS) this.refresh();
    });
    setInterval(() => {
      if (document.visibilityState === 'visible') this.refresh();
    }, REFRESH_EVERY_MS);
  }

  private loadCatalog(): void {
    this.http.get<PermissionInfo[]>(`${this.base}/admin/permissions`).subscribe({
      next: (list) => this.catalog.set(new Map(list.map((p) => [p.key, p]))),
      error: () => { /* names are a nicety; keys still work */ },
    });
  }

  /** After a change of permissions, don't leave someone on a page they may no longer open. */
  private leavePageIfNoLongerAllowed(): void {
    let snapshot: ActivatedRouteSnapshot | null = this.router.routerState?.snapshot?.root ?? null;
    while (snapshot?.firstChild) snapshot = snapshot.firstChild;
    const rule = snapshot?.data?.['access'] as AccessRule | undefined;
    if (!rule) return;
    if (this.me() && this.allows(rule)) return;
    if (this.blockedReason() || this.me()) {
      this.router.navigate(['/no-access'], {
        queryParams: { from: this.router.url, page: snapshot?.data?.['title'] ?? null },
      });
    }
  }
}
