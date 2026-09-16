import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import {
  CouponDiscountType,
  CouponRequest,
  CouponResponse,
  CouponService,
  CouponSwitch,
} from '../../core/services/coupon.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

interface CouponForm {
  code: string;
  discountType: CouponDiscountType;
  discountValue: number | null;
  minOrderAmount: number | null;
  usageLimit: number | null;
  perUserLimit: number | null;
  validFrom: string;
  validTo: string;
  description: string;
  isActive: boolean;
}

const EMPTY_FORM: CouponForm = {
  code: '',
  discountType: 'PERCENT',
  discountValue: 10,
  minOrderAmount: null,
  usageLimit: null,
  perUserLimit: null,
  validFrom: '',
  validTo: '',
  description: '',
  isActive: true,
};

@Component({
  selector: 'app-coupons',
  standalone: true,
  imports: [FormsModule, SidebarComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Coupons</h1>
            <span class="text-xs text-gray-400">{{ total() }} total</span>
          </div>
          <div class="flex items-center gap-2 min-w-0">
            <input
              type="search"
              [ngModel]="search()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by code or description…"
              aria-label="Search coupons"
              class="w-48 md:w-64 min-w-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 placeholder-gray-400" />
            @if (perms.can('coupons.create')) {
              <button (click)="startCreate()"
                class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">
                + New coupon
              </button>
            }
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }

            <!-- Shop-wide promo code switch. Coupons are kept while it is off. -->
            @if (promoSwitch(); as sw) {
              <section class="rounded-xl border bg-white overflow-hidden"
                [class.border-red-300]="!codesOn()" [class.border-gray-200]="codesOn()">
                <div class="flex items-center justify-between gap-4 px-5 py-4" [class.bg-red-50]="!codesOn()">
                  <div>
                    <h2 class="font-bold text-gray-900">
                      Promo codes are
                      <span [class.text-emerald-600]="codesOn()" [class.text-red-600]="!codesOn()">
                        {{ codesOn() ? 'ON' : 'OFF' }}
                      </span>
                    </h2>
                    <p class="text-xs text-gray-500 mt-0.5">
                      @if (codesOn()) {
                        Customers see the promo code box in the cart, and active coupons below apply.
                      } @else {
                        The promo code box is hidden in the shop and no code applies at checkout. Nothing is deleted.
                      }
                    </p>
                    @if (!perms.can('coupons.switch')) {
                      <p class="text-xs text-gray-400 mt-1">Needs the “Turn promo codes on or off” permission.</p>
                    }
                  </div>
                  <button type="button" role="switch" [attr.aria-checked]="codesOn()" (click)="toggleCodes()"
                    [disabled]="savingSwitch() || !perms.can('coupons.switch')"
                    class="relative inline-flex h-7 flex-shrink-0 rounded-full transition-colors disabled:opacity-50"
                    [class.bg-emerald-500]="codesOn()" [class.bg-gray-300]="!codesOn()"
                    [attr.aria-label]="codesOn() ? 'Switch promo codes off' : 'Switch promo codes on'"
                    style="width:3.25rem">
                    <span class="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all"
                      [style.left]="codesOn() ? '1.75rem' : '0.25rem'"></span>
                  </button>
                </div>
              </section>
            }

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                    <th class="px-4 py-2.5 text-left">Code</th>
                    <th class="px-4 py-2.5 text-left">Type</th>
                    <th class="px-4 py-2.5 text-right">Value</th>
                    <th class="px-4 py-2.5 text-right">Min order</th>
                    <th class="px-4 py-2.5 text-right">Used / Limit</th>
                    <th class="px-4 py-2.5 text-left">Window</th>
                    <th class="px-4 py-2.5 text-center">Active</th>
                    <th class="px-4 py-2.5"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  @if (loading()) {
                    <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                  }
                  @for (c of coupons(); track c.id) {
                    <tr [class.bg-blue-50]="editingId() === c.id">
                      <td class="px-4 py-2.5 font-mono text-xs font-semibold">{{ c.code }}</td>
                      <td class="px-4 py-2.5">{{ typeLabel(c.discountType) }}</td>
                      <td class="px-4 py-2.5 text-right">
                        @switch (c.discountType) {
                          @case ('PERCENT') { {{ c.discountValue ?? 0 }}% }
                          @case ('FLAT')    { ৳ {{ c.discountValue ?? 0 }} }
                          @default          { — }
                        }
                      </td>
                      <td class="px-4 py-2.5 text-right">
                        @if (c.minOrderAmount != null) { ৳ {{ c.minOrderAmount }} } @else { — }
                      </td>
                      <td class="px-4 py-2.5 text-right">
                        {{ c.timesUsed }} / {{ c.usageLimit ?? '∞' }}
                      </td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">
                        @if (c.validFrom || c.validTo) {
                          {{ shortDate(c.validFrom) || '—' }} → {{ shortDate(c.validTo) || '—' }}
                        } @else { — }
                      </td>
                      <td class="px-4 py-2.5 text-center">
                        @if (perms.can('coupons.edit')) {
                          <button type="button" role="switch" [attr.aria-checked]="c.isActive"
                            (click)="toggleActive(c)" [disabled]="togglingId() === c.id"
                            [attr.aria-label]="(c.isActive ? 'Switch off ' : 'Switch on ') + c.code"
                            [title]="c.isActive ? 'Active - click to switch off' : 'Off - click to switch on'"
                            class="relative inline-flex h-5 w-9 rounded-full transition-colors disabled:opacity-50"
                            [class.bg-emerald-500]="c.isActive" [class.bg-gray-300]="!c.isActive">
                            <span class="absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all"
                              [style.left]="c.isActive ? '1.125rem' : '0.125rem'"></span>
                          </button>
                        } @else {
                          <span class="inline-block h-2.5 w-2.5 rounded-full"
                            [class.bg-emerald-500]="c.isActive"
                            [class.bg-gray-300]="!c.isActive"
                            [title]="c.isActive ? 'Active' : 'Off'"></span>
                        }
                      </td>
                      <td class="px-4 py-2.5">
                        <div class="flex items-center justify-end gap-2">
                          @if (perms.can('coupons.edit')) {
                          <button (click)="startEdit(c)" class="act-btn act-btn-edit" title="Edit">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                            Edit
                          </button>
                          }
                          @if (perms.can('coupons.delete')) {
                          <button (click)="remove(c)" class="act-btn act-btn-delete" title="Delete">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                            Delete
                          </button>
                          }
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">
                        {{ searching() ? 'No coupons match your search.' : 'No coupons yet.' }}
                      </td></tr>
                    }
                  }
                </tbody>
              </table>
              @if (total() > 0) {
                <div class="border-t border-gray-100">
                  <app-pager [page]="page()" [size]="pageSize" [total]="total()" [disabled]="loading()" (pageChange)="loadPage($event)" />
                </div>
              }
            </section>

            <!-- Form -->
            @if (creating() || editingId() !== null) {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold">
                  {{ editingId() ? 'Edit coupon' : 'New coupon' }}
                </h2>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <label class="text-xs font-medium text-gray-600">
                    Code
                    <input [(ngModel)]="form.code" placeholder="WELCOME10"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm font-mono uppercase" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Discount type
                    <select [(ngModel)]="form.discountType"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                      <option value="PERCENT">Percentage</option>
                      <option value="FLAT">Flat amount</option>
                      <option value="FREE_SHIPPING">Free shipping</option>
                    </select>
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Value
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.discountValue"
                      [disabled]="form.discountType === 'FREE_SHIPPING'"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm disabled:bg-gray-50" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Min order amount
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.minOrderAmount"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Usage limit (total)
                    <input type="number" min="1" [(ngModel)]="form.usageLimit"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Per-user limit
                    <input type="number" min="1" [(ngModel)]="form.perUserLimit"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Valid from
                    <input type="datetime-local" [(ngModel)]="form.validFrom"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Valid to
                    <input type="datetime-local" [(ngModel)]="form.validTo"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600">
                    <input type="checkbox" [(ngModel)]="form.isActive" />
                    Active
                  </label>
                  <label class="text-xs font-medium text-gray-600 sm:col-span-3">
                    Description (admin-facing)
                    <input [(ngModel)]="form.description"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                </div>

                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="cancelEdit()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="save()" [disabled]="saving()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ saving() ? 'Saving…' : 'Save coupon' }}
                  </button>
                </div>
              </section>
            }
          </div>
        </main>
      </div>
    </div>
  `,
})
export class CouponsComponent implements OnInit, OnDestroy {
  private readonly api = inject(CouponService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  protected readonly pageSize = 20;

  /** The coupons on the page on screen; search and paging are done by the server. */
  readonly coupons    = signal<CouponResponse[]>([]);
  /** Zero-based page on screen, and the number of coupons across all pages. */
  readonly page       = signal(0);
  readonly total      = signal(0);
  /** Typed search text, sent to the server after a short pause. */
  readonly search     = signal('');
  protected readonly searching = computed(() => this.search().trim().length > 0);
  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;
  readonly loading    = signal(false);
  readonly saving     = signal(false);
  readonly creating   = signal(false);
  readonly editingId  = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: CouponForm = { ...EMPTY_FORM };

  // ── shop-wide promo code switch ────────────────────────────────────────────

  readonly promoSwitch = signal<CouponSwitch | null>(null);
  readonly savingSwitch = signal(false);
  readonly togglingId = signal<string | null>(null);

  /** Null means on, so a database from before this switch keeps codes working. */
  protected codesOn(): boolean {
    return this.promoSwitch()?.couponsEnabled !== false;
  }

  protected async toggleCodes(): Promise<void> {
    const current = this.promoSwitch();
    if (!current || this.savingSwitch() || !this.perms.can('coupons.switch')) return;
    const turnOn = !this.codesOn();
    if (!turnOn) {
      const ok = await this.confirmer.ask({
        title: 'Turn promo codes off?',
        message: 'The promo code box disappears from the cart, and no code applies at checkout '
          + '- including codes customers already entered. Your coupons are kept.',
        confirmLabel: 'Turn off',
        danger: true,
      });
      if (!ok) return;
    }
    this.savingSwitch.set(true);
    // Optimistic: the switch flips at once and reverts if the save fails.
    this.promoSwitch.set({ couponsEnabled: turnOn });
    this.api.updateSwitch({ couponsEnabled: turnOn }).subscribe({
      next: (saved) => {
        this.promoSwitch.set(saved);
        this.savingSwitch.set(false);
        this.notices.success(turnOn ? 'Promo codes turned on' : 'Promo codes turned off',
          turnOn ? 'The promo code box is back in the cart.' : 'The promo code box is hidden in the shop.');
      },
      error: (err) => {
        this.promoSwitch.set(current);
        this.savingSwitch.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  /** One coupon on or off from the list, without opening the edit form. */
  protected toggleActive(c: CouponResponse): void {
    if (this.togglingId() || !this.perms.can('coupons.edit')) return;
    this.togglingId.set(c.id);
    this.api.update(c.id, { ...this.requestFrom(c), isActive: !c.isActive })
      .pipe(catchError((err) => {
        this.errorMessage.set(parseApiError(err));
        this.togglingId.set(null);
        return EMPTY;
      }))
      .subscribe((saved) => {
        this.coupons.update((list) => list.map((x) => (x.id === saved.id ? saved : x)));
        this.togglingId.set(null);
        this.notices.success(saved.isActive ? 'Coupon switched on' : 'Coupon switched off', saved.code);
        this.loadPage(this.page());
      });
  }

  /** A coupon as it is now, in the shape the update endpoint takes. */
  private requestFrom(c: CouponResponse): CouponRequest {
    return {
      code: c.code,
      discountType: c.discountType,
      discountValue: c.discountValue,
      minOrderAmount: c.minOrderAmount,
      usageLimit: c.usageLimit,
      perUserLimit: c.perUserLimit,
      validFrom: c.validFrom,
      validTo: c.validTo,
      description: c.description,
      isActive: c.isActive,
    };
  }

  ngOnInit(): void {
    this.loadPage(0);
    this.api.getSwitch()
      .pipe(catchError(() => of(null)))
      .subscribe((s) => this.promoSwitch.set(s));
    // Typing searches all coupons after a short pause, from the first page.
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

  /** One page from the server, for the current search. */
  protected loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.api.list(page, this.pageSize, this.search())
      .pipe(catchError(() => of(null)))
      .subscribe((res) => {
        if (request !== this.latestRequest) return;
        this.loading.set(false);
        if (!res) return;
        // The last row of this page went away (e.g. it was deleted): show the page before it.
        if (res.content.length === 0 && page > 0) {
          this.loadPage(Math.min(page - 1, Math.max(res.totalPages - 1, 0)));
          return;
        }
        this.coupons.set(res.content);
        this.page.set(res.number);
        this.total.set(res.totalElements);
      });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.form = { ...EMPTY_FORM };
  }

  protected startEdit(c: CouponResponse): void {
    this.creating.set(false);
    this.editingId.set(c.id);
    this.errorMessage.set('');
    this.form = {
      code: c.code,
      discountType: c.discountType,
      discountValue: c.discountValue,
      minOrderAmount: c.minOrderAmount,
      usageLimit: c.usageLimit,
      perUserLimit: c.perUserLimit,
      validFrom: c.validFrom ?? '',
      validTo:   c.validTo ?? '',
      description: c.description ?? '',
      isActive: c.isActive,
    };
  }

  protected cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM };
  }

  protected save(): void {
    this.saving.set(true);
    this.errorMessage.set('');
    const payload: CouponRequest = {
      code: this.form.code.trim(),
      discountType: this.form.discountType,
      discountValue: this.form.discountType === 'FREE_SHIPPING' ? null : this.form.discountValue,
      minOrderAmount: this.form.minOrderAmount,
      usageLimit: this.form.usageLimit,
      perUserLimit: this.form.perUserLimit,
      validFrom: this.form.validFrom || null,
      validTo:   this.form.validTo || null,
      description: this.form.description || null,
      isActive: this.form.isActive,
    };

    const editId = this.editingId();
    const obs$ = editId ? this.api.update(editId, payload) : this.api.create(payload);

    obs$.pipe(catchError((err) => {
      this.errorMessage.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((saved) => {
      this.saving.set(false);
      if (!saved) return;
      this.notices.success(editId ? 'Coupon saved' : 'Coupon added', saved.code);
      this.cancelEdit();
      // An edit stays on this page; a new coupon is the newest, so it shows on the first page.
      this.loadPage(editId ? this.page() : 0);
    });
  }

  protected async remove(c: CouponResponse): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete coupon "${c.code}"?`,
      message: 'Customers will no longer be able to use this code.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(c.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return EMPTY; }))
      .subscribe(() => {
        if (this.editingId() === c.id) this.cancelEdit();
        this.notices.success('Coupon deleted', c.code);
        this.loadPage(this.page());
      });
  }

  protected typeLabel(t: CouponDiscountType): string {
    return t === 'PERCENT' ? 'Percent'
         : t === 'FLAT'    ? 'Flat'
         : 'Free shipping';
  }

  protected shortDate(iso: string | null): string {
    if (!iso) return '';
    try {
      const d = new Date(iso);
      return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: '2-digit' });
    } catch {
      return iso;
    }
  }
}
