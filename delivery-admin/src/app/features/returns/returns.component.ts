import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import {
  ReturnDecisionPayload,
  ReturnRequestResponse,
  ReturnService,
  ReturnStatus,
} from '../../core/services/return.service';
import { PermissionService } from '../../core/services/permission.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * The Returns page. Search, the status filter and paging are all done by the
 * server, so a search covers every return, not just the page on screen.
 */
@Component({
  selector: 'app-returns',
  standalone: true,
  imports: [FormsModule, SidebarComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Returns</h1>
          <div class="flex items-center gap-2 text-sm min-w-0">
            <input
              type="search"
              [ngModel]="search()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by customer email, order or reason…"
              aria-label="Search returns"
              class="w-56 md:w-80 min-w-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 placeholder-gray-400" />
            <label class="text-gray-500">Filter:</label>
            <select
              [ngModel]="statusFilter()"
              (ngModelChange)="setStatusFilter($event)"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
              <option value="ALL">All</option>
              <option value="REQUESTED">Requested</option>
              <option value="APPROVED">Approved</option>
              <option value="REJECTED">Rejected</option>
              <option value="PICKED_UP">Picked up</option>
              <option value="REFUNDED">Refunded</option>
            </select>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto grid gap-6 lg:grid-cols-[2fr_3fr]">

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden h-fit">
              <header class="border-b border-gray-100 bg-gray-50 px-4 py-2 text-xs uppercase text-gray-400">
                {{ total() }} return(s)
              </header>
              <ul class="divide-y divide-gray-50">
                @if (loading()) {
                  <li class="px-4 py-6 text-center text-sm text-gray-400">Loading…</li>
                }
                @for (r of returns(); track r.id) {
                  <li>
                    <button (click)="select(r)" class="w-full px-4 py-3 text-left hover:bg-gray-50"
                      [class.bg-blue-50]="selected()?.id === r.id">
                      <div class="flex items-center justify-between gap-2">
                        <span class="font-mono text-xs">{{ shortId(r.id) }}</span>
                        <span class="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
                          [class]="badgeClass(r.status)">{{ statusLabel(r.status) }}</span>
                      </div>
                      <p class="mt-1 text-xs text-gray-500">
                        Order {{ shortId(r.orderId) }} &middot; {{ shortDate(r.requestedAt) }}
                      </p>
                      <p class="text-xs text-gray-600">{{ r.customerEmail || '—' }}</p>
                    </button>
                  </li>
                } @empty {
                  @if (!loading()) {
                    <li class="px-4 py-6 text-center text-sm text-gray-400">
                      {{ searching() ? 'No returns match your search.' : 'No return requests yet.' }}
                    </li>
                  }
                }
              </ul>
              @if (total() > 0) {
                <div class="border-t border-gray-100">
                  <app-pager [page]="page()" [size]="pageSize" [total]="total()" [disabled]="loading()" (pageChange)="goToPage($event)" />
                </div>
              }
            </section>

            <!-- Detail -->
            <section class="bg-white rounded-xl border border-gray-200 p-5">
              @if (selected(); as r) {
                <div class="space-y-4">
                  <header class="flex items-start justify-between">
                    <div>
                      <h2 class="text-lg font-semibold">Return {{ shortId(r.id) }}</h2>
                      <p class="text-xs text-gray-500">Order {{ shortId(r.orderId) }} &middot; {{ r.reason.replace('_', ' ') }}</p>
                    </div>
                    <span class="inline-block rounded-full px-2.5 py-1 text-xs font-medium"
                      [class]="badgeClass(r.status)">{{ statusLabel(r.status) }}</span>
                  </header>

                  @if (r.customerNotes) {
                    <div class="rounded-lg border border-gray-100 bg-gray-50 p-3 text-sm text-gray-700">
                      <p class="mb-1 text-xs font-semibold uppercase tracking-wide text-gray-400">Customer notes</p>
                      {{ r.customerNotes }}
                    </div>
                  }

                  <!-- Items -->
                  <div>
                    <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">Items</p>
                    <ul class="space-y-1 text-sm">
                      @for (i of r.items; track i.id) {
                        <li class="flex items-center justify-between rounded-lg border border-gray-100 px-3 py-2">
                          <div>
                            <p>{{ i.productName || ('Item ' + i.orderItemId) }}</p>
                            <p class="text-xs text-gray-500">
                              Qty {{ i.quantity }}
                              @if (i.condition) { &middot; {{ i.condition }} }
                            </p>
                          </div>
                        </li>
                      }
                    </ul>
                  </div>

                  <!-- Photos -->
                  @if (r.photos.length > 0) {
                    <div>
                      <p class="mb-2 text-xs font-semibold uppercase tracking-wide text-gray-400">Photos</p>
                      <div class="flex gap-2">
                        @for (p of r.photos; track p.id) {
                          <a [href]="p.url" target="_blank" rel="noopener" class="block h-24 w-24 overflow-hidden rounded-lg border border-gray-200">
                            <img [src]="p.url" alt="Return evidence" class="h-full w-full object-cover" />
                          </a>
                        }
                      </div>
                    </div>
                  }

                  <!-- Decision controls -->
                  <div class="space-y-3 border-t border-gray-100 pt-4">
                    <label class="text-xs font-medium text-gray-600">
                      Admin notes (sent to customer)
                      <textarea
                        [(ngModel)]="decisionNotes"
                        [disabled]="!perms.canAny('returns.decide', 'returns.refund')"
                        rows="2"
                        maxlength="2000"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                    </label>
                    <label class="text-xs font-medium text-gray-600">
                      Refund amount (BDT)
                      <input
                        type="number"
                        step="0.01"
                        min="0"
                        [(ngModel)]="decisionAmount"
                        [disabled]="!perms.canAny('returns.decide', 'returns.refund')"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    @if (!perms.canAny('returns.decide', 'returns.refund')) {
                      <p class="text-xs text-gray-400">Needs the “Approve or reject returns” or “Refund returns” permission.</p>
                    }

                    @if (decisionError()) {
                      <p class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ decisionError() }}</p>
                    }

                    <div class="flex flex-wrap gap-2">
                      @if (perms.can('returns.decide')) {
                      <button (click)="approve(r)" [disabled]="acting() || r.status !== 'REQUESTED'"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                        Approve
                      </button>
                      <button (click)="reject(r)" [disabled]="acting() || (r.status !== 'REQUESTED' && r.status !== 'APPROVED' && r.status !== 'PICKED_UP')"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-red-500 hover:bg-red-600 rounded-lg disabled:opacity-50">
                        Reject
                      </button>
                      }
                      @if (perms.can('returns.pickup')) {
                      <button (click)="markPickedUp(r)" [disabled]="acting() || r.status !== 'APPROVED'"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-purple-600 hover:bg-purple-700 rounded-lg disabled:opacity-50">
                        Mark picked up
                      </button>
                      }
                      @if (perms.can('returns.refund')) {
                      <button (click)="refund(r)" [disabled]="acting() || r.status !== 'PICKED_UP'"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg disabled:opacity-50">
                        Mark refunded
                      </button>
                      }
                    </div>
                  </div>
                </div>
              } @else {
                <p class="text-sm text-gray-400">Select a return to review.</p>
              }
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class ReturnsComponent implements OnInit, OnDestroy {
  private readonly api = inject(ReturnService);
  protected readonly perms = inject(PermissionService);

  protected readonly pageSize = 20;

  readonly returns       = signal<ReturnRequestResponse[]>([]);
  readonly selected      = signal<ReturnRequestResponse | null>(null);
  readonly loading       = signal(false);
  readonly acting        = signal(false);
  readonly decisionError = signal<string>('');
  readonly statusFilter  = signal<string>('ALL');
  /** Zero-based page on screen, and the number of returns across all pages. */
  readonly page          = signal(0);
  readonly total         = signal(0);
  /** Typed search text, sent to the server after a short pause. */
  readonly search        = signal('');
  protected readonly searching = computed(() => this.search().trim().length > 0);

  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  protected decisionNotes  = '';
  protected decisionAmount: number | null = null;

  ngOnInit(): void {
    this.loadPage(0);
    // Typing searches all returns after a short pause, from the first page.
    this.searchSubject.pipe(map((q) => q.trim()), debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.searchSubject.next(q);
  }

  protected setStatusFilter(status: string): void {
    this.statusFilter.set(status);
    this.loadPage(0);
  }

  protected goToPage(page: number): void {
    this.loadPage(page);
  }

  /** One page from the server, for the current search and status filter. */
  private loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.api.list(this.statusFilter(), page, this.pageSize, this.search())
      .pipe(catchError(() => of(null)))
      .subscribe((res) => {
        if (request !== this.latestRequest) return;
        this.loading.set(false);
        if (!res) return;
        // The last row of this page went away (e.g. after an action): show the page before it.
        if (res.content.length === 0 && page > 0) {
          this.loadPage(Math.min(page - 1, Math.max(res.totalPages - 1, 0)));
          return;
        }
        this.returns.set(res.content);
        this.page.set(res.number);
        this.total.set(res.totalElements);
        // Refresh selected from the latest list snapshot.
        const sel = this.selected();
        if (sel) {
          const fresh = res.content.find((r) => r.id === sel.id);
          if (fresh) this.selected.set(fresh);
        }
      });
  }

  protected select(r: ReturnRequestResponse): void {
    this.selected.set(r);
    this.decisionNotes  = r.adminNotes ?? '';
    this.decisionAmount = r.refundAmount;
    this.decisionError.set('');
  }

  protected approve(r: ReturnRequestResponse): void { this.act(r, 'approve'); }
  protected reject(r: ReturnRequestResponse):  void { this.act(r, 'reject'); }
  protected markPickedUp(r: ReturnRequestResponse): void { this.act(r, 'pickup'); }
  protected refund(r: ReturnRequestResponse):  void { this.act(r, 'refund'); }

  private act(r: ReturnRequestResponse, action: 'approve' | 'reject' | 'pickup' | 'refund'): void {
    const payload: ReturnDecisionPayload = {
      adminNotes:   this.decisionNotes || undefined,
      refundAmount: this.decisionAmount ?? undefined,
    };
    const obs$ =
      action === 'approve' ? this.api.approve(r.id, payload)
      : action === 'reject'  ? this.api.reject(r.id, payload)
      : action === 'pickup'  ? this.api.markPickedUp(r.id)
      :                        this.api.refund(r.id, payload);

    this.acting.set(true);
    this.decisionError.set('');
    obs$.pipe(catchError((err) => {
      this.decisionError.set(parseApiError(err));
      this.acting.set(false);
      return of(null);
    })).subscribe((updated) => {
      this.acting.set(false);
      if (updated) {
        this.selected.set(updated);
        this.returns.update((list) => list.map((x) => x.id === updated.id ? updated : x));
        // Stay on this page; the row may have left the current filter.
        this.loadPage(this.page());
      }
    });
  }

  protected statusLabel(s: ReturnStatus): string {
    return s === 'REQUESTED' ? 'Requested'
         : s === 'APPROVED'  ? 'Approved'
         : s === 'REJECTED'  ? 'Rejected'
         : s === 'PICKED_UP' ? 'Picked up'
         :                     'Refunded';
  }

  protected badgeClass(s: ReturnStatus): string {
    return s === 'REFUNDED' ? 'bg-emerald-100 text-emerald-700'
         : s === 'REJECTED' ? 'bg-red-100 text-red-700'
         : s === 'APPROVED' || s === 'PICKED_UP' ? 'bg-blue-100 text-blue-700'
         :                    'bg-amber-100 text-amber-700';
  }

  protected shortId(id: string): string {
    return id ? id.slice(0, 8).toUpperCase() : '—';
  }

  protected shortDate(iso: string): string {
    if (!iso) return '';
    return new Date(iso).toLocaleDateString('en-GB',
      { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' });
  }
}
