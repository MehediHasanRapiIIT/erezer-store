import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ADMIN_PAGES, firstAllowedPage } from '../../core/access/admin-pages';
import { AuthService } from '../../core/services/auth.service';
import { PermissionService } from '../../core/services/permission.service';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';

/**
 * Where people land when they open a page they may not use, or when their
 * login cannot use the panel at all. Explains what is missing in plain words
 * and offers the pages they can open.
 */
@Component({
  selector: 'app-no-access',
  standalone: true,
  imports: [RouterLink, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      @if (perms.me()) {
        <app-sidebar />
      }
      <main class="flex-1 overflow-y-auto p-6">
        <div class="mx-auto mt-10 max-w-lg rounded-xl border border-gray-200 bg-white p-8">
          @if (perms.blockedReason(); as reason) {
            <h1 class="text-lg font-bold text-gray-900">This login can't open the admin panel</h1>
            <p class="mt-2 text-sm text-gray-600">{{ reason }}</p>
            <p class="mt-2 text-sm text-gray-600">If you think this is a mistake, ask an admin.</p>
            <div class="mt-6 flex gap-2">
              <button type="button" (click)="checkAgain()" [disabled]="checking()"
                class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50">
                {{ checking() ? 'Checking…' : 'Check again' }}
              </button>
              <button type="button" (click)="auth.logout()"
                class="rounded-lg bg-gray-900 px-3 py-1.5 text-sm font-semibold text-white hover:bg-gray-700">
                Log out
              </button>
            </div>
          } @else if (!perms.me()) {
            <h1 class="text-lg font-bold text-gray-900">Couldn't load your permissions</h1>
            <p class="mt-2 text-sm text-gray-600">The server didn't answer. Check your connection and try again.</p>
            <button type="button" (click)="checkAgain()" [disabled]="checking()"
              class="mt-6 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-50">
              {{ checking() ? 'Checking…' : 'Try again' }}
            </button>
          } @else {
            <h1 class="text-lg font-bold text-gray-900">
              You don't have access to {{ pageName() }}
            </h1>
            @if (missing().length > 0) {
              <p class="mt-2 text-sm text-gray-600">
                It needs {{ missing().length > 1 ? (mode() === 'all' ? 'all of these permissions' : 'one of these permissions') : 'this permission' }}:
              </p>
              <ul class="mt-2 list-disc pl-5 text-sm text-gray-800">
                @for (key of missing(); track key) {
                  <li>{{ perms.label(key) }}</li>
                }
              </ul>
            }
            <p class="mt-3 text-sm text-gray-600">An admin can give it to you on the Staff page. Once they have, press “Check again”.</p>
            <div class="mt-6 flex flex-wrap gap-2">
              <button type="button" (click)="checkAgain()" [disabled]="checking()"
                class="rounded-lg bg-blue-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                {{ checking() ? 'Checking…' : 'Check again' }}
              </button>
              @if (home(); as h) {
                <a [routerLink]="h"
                  class="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50">
                  Go to {{ labelFor(h) }}
                </a>
              }
            </div>
            @if (!home()) {
              <p class="mt-4 rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
                You don't have access to any page yet.
              </p>
            }
          }
        </div>
      </main>
    </div>
  `,
})
export class NoAccessComponent {
  protected perms = inject(PermissionService);
  protected auth = inject(AuthService);
  private router = inject(Router);
  private params = inject(ActivatedRoute).snapshot.queryParamMap;

  protected readonly checking = signal(false);
  private readonly from = this.params.get('from');

  protected readonly pageName = computed(() => this.params.get('page') || 'this page');

  /** The rule of the page they tried to open, found by its address. */
  private readonly rule = computed(() => {
    const path = (this.from ?? '').split('?')[0];
    const route = this.router.config.find((r) => r.path && this.matches(r.path, path));
    return route?.data?.['access'] as { any?: string[]; all?: string[] } | undefined;
  });

  protected readonly mode = computed(() => (this.rule()?.all?.length ? 'all' : 'any'));

  /** Permissions from that rule the person doesn't hold. */
  protected readonly missing = computed(() => {
    const rule = this.rule();
    if (!rule) return [];
    const keys = [...(rule.all ?? []), ...(rule.any ?? [])];
    return keys.filter((k) => !this.perms.can(k));
  });

  protected readonly home = computed(() => firstAllowedPage(this.perms));

  protected labelFor(route: string): string {
    return ADMIN_PAGES.find((p) => p.route === route)?.label ?? route;
  }

  protected async checkAgain(): Promise<void> {
    this.checking.set(true);
    await this.perms.refresh();
    this.checking.set(false);
    if (this.perms.me() && this.from && this.from !== '/no-access') {
      this.router.navigateByUrl(this.from);
    }
  }

  /** "orders/:orderId" matches "/orders/123". */
  private matches(pattern: string, path: string): boolean {
    const a = pattern.split('/');
    const b = path.replace(/^\//, '').split('/');
    return a.length === b.length && a.every((seg, i) => seg.startsWith(':') || seg === b[i]);
  }
}
