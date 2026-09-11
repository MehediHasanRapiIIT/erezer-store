import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PermissionService } from '../../core/services/permission.service';
import { ActivityPage, ActivityPerson, ActivityService } from '../../core/services/activity.service';
import { parseApiError } from '../../core/utils/api-error.util';

const PAGE_SIZE = 50;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type Shortcut = 'today' | 'week' | 'month';

/** 'YYYY-MM-DD' for a date in the browser's local time. */
function ymd(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Who did what in the admin panel, newest first. */
@Component({
  selector: 'app-activity',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Activity log</h1>
            <span class="text-xs text-gray-400">Newest first</span>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto space-y-5">

            <!-- Filters -->
            <section class="bg-white rounded-xl border border-gray-200 p-4">
              <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <label class="text-xs font-medium text-gray-600">
                  Person
                  <select [ngModel]="staffId()" (ngModelChange)="staffId.set($event); search()"
                    class="mt-1 w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                    <option value="">Everyone</option>
                    @for (p of people(); track p.staffId) {
                      <option [value]="p.staffId">{{ p.name }}</option>
                    }
                  </select>
                </label>
                <label class="text-xs font-medium text-gray-600">
                  Area
                  <select [ngModel]="area()" (ngModelChange)="area.set($event); search()"
                    class="mt-1 w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                    <option value="">Any area</option>
                    @for (a of areas(); track a) {
                      <option [value]="a">{{ a }}</option>
                    }
                  </select>
                </label>
                <label class="text-xs font-medium text-gray-600">
                  From
                  <input type="date" [ngModel]="from()" (ngModelChange)="from.set($event ?? ''); search()"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="text-xs font-medium text-gray-600">
                  To
                  <input type="date" [ngModel]="to()" (ngModelChange)="to.set($event ?? ''); search()"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
              </div>
              <div class="mt-3 flex flex-wrap items-center gap-2">
                <button type="button" class="act-btn" [class.border-blue-300]="activeShortcut() === 'today'"
                  [attr.aria-pressed]="activeShortcut() === 'today'" (click)="useShortcut('today')">Today</button>
                <button type="button" class="act-btn" [class.border-blue-300]="activeShortcut() === 'week'"
                  [attr.aria-pressed]="activeShortcut() === 'week'" (click)="useShortcut('week')">Last 7 days</button>
                <button type="button" class="act-btn" [class.border-blue-300]="activeShortcut() === 'month'"
                  [attr.aria-pressed]="activeShortcut() === 'month'" (click)="useShortcut('month')">This month</button>
                @if (staffId() || area() || from() || to()) {
                  <button type="button" class="act-btn act-btn-delete" (click)="clearFilters()">Clear filters</button>
                }
              </div>
            </section>

            @if (rangeError()) {
              <p class="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-700" role="alert">{{ rangeError() }}</p>
            }
            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700" role="alert">{{ errorMessage() }}</p>
            }

            <!-- Log -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <div class="overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                      <th class="px-4 py-2.5 text-left">Time</th>
                      <th class="px-4 py-2.5 text-left">Person</th>
                      <th class="px-4 py-2.5 text-left">What happened</th>
                      <th class="px-4 py-2.5 text-left">Area</th>
                      <th class="px-4 py-2.5 text-left">Target</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-gray-50">
                    @if (loading() && items().length === 0) {
                      <tr><td colspan="5" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                    }
                    @for (e of items(); track e.id) {
                      <tr>
                        <td class="px-4 py-2.5 whitespace-nowrap text-xs tabular-nums text-gray-500">{{ e.occurredAt }}</td>
                        <td class="px-4 py-2.5">
                          <div class="font-medium text-gray-900">{{ e.staffName }}</div>
                          @if (e.staffUsername) {
                            <div class="font-mono text-xs text-gray-400">{{ e.staffUsername }}</div>
                          }
                        </td>
                        <td class="px-4 py-2.5 text-gray-700">
                          @if (e.summary) {
                            {{ e.summary }}
                          } @else {
                            <span class="font-mono text-xs">{{ e.method }} {{ e.path }}</span>
                          }
                          @if (e.status !== null && e.status >= 400) {
                            <span class="ml-1 rounded-full bg-red-50 px-2 py-0.5 text-[11px] font-semibold text-red-700">
                              {{ e.status === 403 ? 'Refused' : 'Failed' }} ({{ e.status }})
                            </span>
                          }
                        </td>
                        <td class="px-4 py-2.5 text-xs text-gray-500">{{ e.area || '—' }}</td>
                        <td class="px-4 py-2.5 text-xs text-gray-500">
                          @if (e.targetId) {
                            @if (isUuid(e.targetId)) {
                              <span class="font-mono" [title]="e.targetId" [attr.aria-label]="e.targetId">{{ e.targetId.slice(0, 8) }}…</span>
                            } @else {
                              <span class="font-mono">{{ e.targetId }}</span>
                            }
                          } @else {
                            —
                          }
                        </td>
                      </tr>
                    } @empty {
                      @if (!loading()) {
                        <tr><td colspan="5" class="px-4 py-6 text-center text-gray-400">Nothing recorded for these filters.</td></tr>
                      }
                    }
                  </tbody>
                </table>
              </div>

              @if (total() > 0) {
                <div class="flex flex-wrap items-center justify-between gap-2 border-t border-gray-100 px-4 py-3 text-xs text-gray-500">
                  <span>Showing {{ firstShown() }}–{{ lastShown() }} of {{ total() }}</span>
                  <div class="flex gap-2">
                    <button type="button" class="act-btn" (click)="goTo(page() - 1)" [disabled]="page() === 0 || loading()">
                      Previous
                    </button>
                    <button type="button" class="act-btn" (click)="goTo(page() + 1)" [disabled]="!hasNext() || loading()">
                      Next
                    </button>
                  </div>
                </div>
              }
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ActivityComponent implements OnInit {
  private readonly api = inject(ActivityService);
  protected readonly perms = inject(PermissionService);

  readonly people = signal<ActivityPerson[]>([]);
  /** Every area in the permission catalogue, in catalogue order. */
  readonly areas = computed(() => [...new Set(this.perms.catalogList().map((p) => p.area))]);

  readonly staffId = signal('');
  readonly area = signal('');
  readonly from = signal('');
  readonly to = signal('');
  readonly page = signal(0);

  readonly result = signal<ActivityPage | null>(null);
  readonly loading = signal(false);
  readonly errorMessage = signal('');

  readonly rangeError = computed(() =>
    this.from() && this.to() && this.from() > this.to() ? 'The From date is after the To date.' : '');

  readonly items = computed(() => this.result()?.items ?? []);
  readonly total = computed(() => this.result()?.total ?? 0);
  readonly firstShown = computed(() => {
    const r = this.result();
    return r && r.total > 0 ? r.page * r.size + 1 : 0;
  });
  readonly lastShown = computed(() => {
    const r = this.result();
    return r ? Math.min((r.page + 1) * r.size, r.total) : 0;
  });
  readonly hasNext = computed(() => this.lastShown() < this.total());

  /** Which shortcut the current dates match, if any. */
  readonly activeShortcut = computed<Shortcut | null>(() => {
    for (const s of ['today', 'week', 'month'] as Shortcut[]) {
      const range = this.shortcutRange(s);
      if (this.from() === range.from && this.to() === range.to) return s;
    }
    return null;
  });

  /** Ignores answers to requests that a newer one has replaced. */
  private requestNo = 0;

  ngOnInit(): void {
    this.api.people().subscribe({
      next: (list) => this.people.set(list),
      error: () => this.people.set([]),
    });
    if (this.perms.catalogList().length === 0) this.perms.refresh();
    this.load();
  }

  /** A filter changed: back to the first page. */
  protected search(): void {
    this.page.set(0);
    this.load();
  }

  protected useShortcut(s: Shortcut): void {
    const range = this.shortcutRange(s);
    this.from.set(range.from);
    this.to.set(range.to);
    this.search();
  }

  protected clearFilters(): void {
    this.staffId.set('');
    this.area.set('');
    this.from.set('');
    this.to.set('');
    this.search();
  }

  protected goTo(page: number): void {
    if (page < 0) return;
    this.page.set(page);
    this.load();
  }

  protected isUuid(value: string): boolean {
    return UUID_PATTERN.test(value);
  }

  private shortcutRange(s: Shortcut): { from: string; to: string } {
    const now = new Date();
    const today = ymd(now);
    if (s === 'today') return { from: today, to: today };
    if (s === 'week') return { from: ymd(new Date(now.getFullYear(), now.getMonth(), now.getDate() - 6)), to: today };
    return { from: ymd(new Date(now.getFullYear(), now.getMonth(), 1)), to: today };
  }

  private load(): void {
    if (this.rangeError()) return;
    const requestNo = ++this.requestNo;
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.list({
      staffId: this.staffId(),
      area: this.area(),
      from: this.from(),
      to: this.to(),
      page: this.page(),
      size: PAGE_SIZE,
    }).subscribe({
      next: (page) => {
        if (requestNo !== this.requestNo) return;
        this.result.set(page);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        if (requestNo !== this.requestNo) return;
        this.loading.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }
}
