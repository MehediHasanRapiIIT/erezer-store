import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { PagerComponent } from '../../shared/pager/pager.component';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ProductMultiPickerComponent } from '../../shared/product-picker/product-multi-picker.component';
import {
  FlashSaleDiscountType,
  FlashSaleRequest,
  FlashSaleResponse,
  FlashSaleService,
} from '../../core/services/flash-sale.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

interface FlashSaleForm {
  name: string;
  label: string;
  discountType: FlashSaleDiscountType;
  discountValue: number | null;
  startsAt: string;
  endsAt: string;
  couponCode: string;
  minSpend: number | null;
  isActive: boolean;
  featured: boolean;
  productIds: number[];
}

const EMPTY_FORM: FlashSaleForm = {
  name: '',
  label: 'Limited time',
  discountType: 'PERCENT',
  discountValue: 20,
  startsAt: '',
  endsAt: '',
  couponCode: '',
  minSpend: null,
  isActive: true,
  featured: false,
  productIds: [],
};

@Component({
  selector: 'app-flash-sale',
  standalone: true,
  imports: [FormsModule, SidebarComponent, ProductMultiPickerComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Flash Sales</h1>
            <span class="text-xs text-gray-400">{{ total() }} total</span>
          </div>
          <div class="flex items-center gap-2 min-w-0">
            <input
              type="search"
              [ngModel]="search()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by name, label or coupon…"
              aria-label="Search flash sales"
              class="w-56 md:w-72 min-w-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 placeholder-gray-400" />
            @if (perms.can('flash_sales.create')) {
              <button (click)="startCreate()"
                class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">
                + New flash sale
              </button>
            }
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            <p class="rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700">
              Multiple flash sales can run at once — the storefront <strong>/flash-sale</strong> page lists all
              active ones, and each links to its own detail page. The landing-page widget shows the one marked
              <strong>Featured</strong> (or, if none is, the active sale ending soonest).
            </p>

            @if (errorMessage()) {
              <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ errorMessage() }}</p>
            }

            <!-- List -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                    <th class="px-4 py-2.5 text-left">Name</th>
                    <th class="px-4 py-2.5 text-right">Offer</th>
                    <th class="px-4 py-2.5 text-center">Items</th>
                    <th class="px-4 py-2.5 text-left">Window</th>
                    <th class="px-4 py-2.5 text-left">Coupon</th>
                    <th class="px-4 py-2.5 text-center">Featured</th>
                    <th class="px-4 py-2.5 text-center">Active</th>
                    <th class="px-4 py-2.5"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  @if (loading()) {
                    <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                  }
                  @for (s of sales(); track s.id) {
                    <tr [class.bg-blue-50]="editingId() === s.id">
                      <td class="px-4 py-2.5 font-medium">{{ s.name }}</td>
                      <td class="px-4 py-2.5 text-right">
                        @if (s.discountType === 'PERCENT') { {{ s.discountValue }}% } @else { ৳ {{ s.discountValue }} }
                      </td>
                      <td class="px-4 py-2.5 text-center">{{ s.products.length }}</td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">
                        {{ shortDate(s.startsAt) || '—' }} → {{ shortDate(s.endsAt) || '—' }}
                      </td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">{{ s.couponCode || '—' }}</td>
                      <td class="px-4 py-2.5 text-center">
                        @if (s.featured) {
                          <span class="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">★ Featured</span>
                        } @else { <span class="text-gray-300">—</span> }
                      </td>
                      <td class="px-4 py-2.5 text-center">
                        <span class="inline-block h-2.5 w-2.5 rounded-full"
                          [class.bg-emerald-500]="s.isActive"
                          [class.bg-gray-300]="!s.isActive"></span>
                      </td>
                      <td class="px-4 py-2.5">
                        <div class="flex items-center justify-end gap-2">
                          @if (perms.can('flash_sales.edit')) {
                          <button (click)="startEdit(s)" title="Edit"
                            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700">
                            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
                            </svg>
                            Edit
                          </button>
                          }
                          @if (perms.can('flash_sales.delete')) {
                          <button (click)="remove(s)" title="Delete"
                            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-600 shadow-sm transition hover:border-red-300 hover:bg-red-50 hover:text-red-600">
                            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                            </svg>
                            Delete
                          </button>
                          }
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">{{ searching() ? 'No flash sales match your search.' : 'No flash sales yet.' }}</td></tr>
                    }
                  }
                </tbody>
              </table>
              @if (total() > 0) {
                <div class="border-t border-gray-100">
                  <app-pager [page]="page()" [size]="pageSize" [total]="total()" [disabled]="loading()" (pageChange)="goToPage($event)" />
                </div>
              }
            </section>

            <!-- Form -->
            @if (creating() || editingId() !== null) {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold">
                  {{ editingId() ? 'Edit flash sale' : 'New flash sale' }}
                </h2>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <label class="text-xs font-medium text-gray-600 sm:col-span-2">
                    Name
                    <input [(ngModel)]="form.name" placeholder="Black Friday"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Eyebrow label
                    <input [(ngModel)]="form.label" placeholder="Limited time"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>

                  <label class="text-xs font-medium text-gray-600">
                    Discount type
                    <select [(ngModel)]="form.discountType"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                      <option value="PERCENT">Percentage</option>
                      <option value="FLAT">Flat amount</option>
                    </select>
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Value {{ form.discountType === 'PERCENT' ? '(%)' : '(৳)' }}
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.discountValue"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600 mt-5">
                    <input type="checkbox" [(ngModel)]="form.isActive" />
                    Active
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600 mt-5">
                    <input type="checkbox" [(ngModel)]="form.featured" />
                    Featured on landing
                  </label>

                  <label class="text-xs font-medium text-gray-600">
                    Starts at
                    <input type="datetime-local" [(ngModel)]="form.startsAt"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Ends at <span class="text-red-500">*</span>
                    <input type="datetime-local" [(ngModel)]="form.endsAt"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <div></div>

                  <label class="text-xs font-medium text-gray-600">
                    Coupon code <span class="font-normal text-gray-400">(optional)</span>
                    <input [(ngModel)]="form.couponCode" placeholder="FRIDAY"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm uppercase" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Min spend (৳) <span class="font-normal text-gray-400">(optional)</span>
                    <input type="number" step="1" min="0" [(ngModel)]="form.minSpend"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                </div>

                <!-- Product picker -->
                <div class="mt-5">
                  <app-product-multi-picker label="Products" [selected]="form.productIds"
                    (selectedChange)="form.productIds = $event" />
                </div>

                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="cancelEdit()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                    Cancel
                  </button>
                  <button type="button" (click)="save()" [disabled]="saving()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ saving() ? 'Saving…' : 'Save flash sale' }}
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
export class FlashSaleComponent implements OnInit, OnDestroy {
  private readonly api = inject(FlashSaleService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly sales      = signal<FlashSaleResponse[]>([]);
  readonly loading     = signal(false);
  readonly saving      = signal(false);
  readonly creating    = signal(false);
  readonly editingId   = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: FlashSaleForm = { ...EMPTY_FORM, productIds: [] };

  protected readonly pageSize = 20;
  /** Zero-based page on screen, and the number of flash sales across all pages. */
  readonly page = signal(0);
  readonly total = signal(0);
  /** Typed search text, sent to the server after a short pause. */
  readonly search = signal('');
  protected readonly searching = computed(() => this.search().trim().length > 0);

  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  ngOnInit(): void {
    this.loadPage(0);
    // Typing searches all flash sales after a short pause, from the first page.
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

  protected goToPage(page: number): void {
    this.loadPage(page);
  }

  /** One page from the server, for the current search. */
  private loadPage(page: number): void {
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
        this.sales.set(res.content);
        this.page.set(res.number);
        this.total.set(res.totalElements);
      });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.form = { ...EMPTY_FORM, productIds: [] };
  }

  protected startEdit(s: FlashSaleResponse): void {
    this.creating.set(false);
    this.editingId.set(s.id);
    this.errorMessage.set('');
    this.form = {
      name: s.name,
      label: s.label ?? '',
      discountType: s.discountType,
      discountValue: s.discountValue,
      startsAt: this.toLocalInput(s.startsAt),
      endsAt: this.toLocalInput(s.endsAt),
      couponCode: s.couponCode ?? '',
      minSpend: s.minSpend,
      isActive: s.isActive,
      featured: s.featured ?? false,
      productIds: s.products.map((p) => p.id),
    };
  }

  protected cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM, productIds: [] };
  }

  protected save(): void {
    if (!this.form.name.trim()) {
      this.errorMessage.set('Name is required.');
      return;
    }
    if (!this.form.endsAt) {
      this.errorMessage.set('An end date/time is required for the countdown.');
      return;
    }
    if (this.form.discountValue == null || this.form.discountValue <= 0) {
      this.errorMessage.set('Enter a discount value greater than 0.');
      return;
    }
    if (this.form.productIds.length === 0) {
      this.errorMessage.set('Select at least one product for the sale.');
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    const payload: FlashSaleRequest = {
      name: this.form.name.trim(),
      label: this.form.label.trim() || null,
      discountType: this.form.discountType,
      discountValue: this.form.discountValue,
      startsAt: this.form.startsAt || null,
      endsAt: this.form.endsAt,
      couponCode: this.form.couponCode.trim().toUpperCase() || null,
      minSpend: this.form.minSpend,
      isActive: this.form.isActive,
      featured: this.form.featured,
      productIds: this.form.productIds,
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
      this.loadPage(this.page());
      this.notices.success(editId ? 'Flash sale saved' : 'Flash sale added', saved.name);
      this.cancelEdit();
    });
  }

  protected async remove(s: FlashSaleResponse): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete flash sale "${s.name}"?`,
      message: 'It stops showing in the shop straight away.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(s.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return EMPTY; }))
      .subscribe(() => {
        this.loadPage(this.page());
        this.notices.success('Flash sale deleted', s.name);
      });
  }

  protected shortDate(iso: string | null): string {
    if (!iso) return '';
    try {
      const dt = new Date(iso);
      return dt.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: '2-digit' });
    } catch {
      return iso;
    }
  }

  /** ISO instant → value the datetime-local input accepts ("YYYY-MM-DDTHH:mm"). */
  private toLocalInput(iso: string | null): string {
    if (!iso) return '';
    // Already a bare local string? keep first 16 chars. Otherwise convert.
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(iso) && !iso.endsWith('Z') && !/[+-]\d{2}:\d{2}$/.test(iso)) {
      return iso.slice(0, 16);
    }
    try {
      const dt = new Date(iso);
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}`;
    } catch {
      return '';
    }
  }
}
