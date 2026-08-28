import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  FlashSaleDiscountType,
  FlashSaleRequest,
  FlashSaleResponse,
  FlashSaleService,
} from '../../core/services/flash-sale.service';
import { ProductService } from '../../core/services/product.service';
import { ProductResponse } from '../../core/models/api.models';
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
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Flash Sales</h1>
            <span class="text-xs text-gray-400">{{ sales().length }} total</span>
          </div>
          <button (click)="startCreate()"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
            + New flash sale
          </button>
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
                          <button (click)="startEdit(s)" title="Edit"
                            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-700 shadow-sm transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700">
                            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z" />
                            </svg>
                            Edit
                          </button>
                          <button (click)="remove(s)" title="Delete"
                            class="inline-flex items-center gap-1.5 rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-600 shadow-sm transition hover:border-red-300 hover:bg-red-50 hover:text-red-600">
                            <svg class="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8">
                              <path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                            </svg>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="8" class="px-4 py-6 text-center text-gray-400">No flash sales yet.</td></tr>
                    }
                  }
                </tbody>
              </table>
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
                  <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
                    <span class="text-xs font-semibold text-gray-700">
                      Products <span class="font-normal text-gray-400">({{ form.productIds.length }} selected)</span>
                    </span>
                    <div class="flex items-center gap-2">
                      <button type="button" (click)="selectAllFiltered()"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700">
                        Select all (filtered)
                      </button>
                      <button type="button" (click)="clearSelection()"
                        class="rounded-lg border border-gray-200 bg-white px-2.5 py-1 text-xs font-medium text-gray-600 hover:border-red-300 hover:bg-red-50 hover:text-red-600">
                        Clear
                      </button>
                    </div>
                  </div>
                  <div class="mb-2 flex flex-col gap-2 sm:flex-row">
                    <input [ngModel]="productSearch()" (ngModelChange)="onProductSearch($event)"
                      [ngModelOptions]="{ standalone: true }" placeholder="Search products…"
                      class="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    <select [ngModel]="categoryFilter()" (ngModelChange)="onCategoryFilter($event)"
                      [ngModelOptions]="{ standalone: true }"
                      class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm sm:w-52">
                      <option value="all">All categories</option>
                      @for (c of categoryOptions(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
                    </select>
                  </div>
                  <div class="rounded-lg border border-gray-200 divide-y divide-gray-50">
                    @for (p of pagedProducts(); track p.id) {
                      <label class="flex items-center gap-3 px-3 py-2 text-sm hover:bg-gray-50 cursor-pointer">
                        <input type="checkbox" [checked]="isSelected(p.id)" (change)="toggleProduct(p.id)" />
                        @if (p.imageUrl) {
                          <img [src]="p.imageUrl" [alt]="p.name" class="h-8 w-8 rounded object-cover" />
                        }
                        <span class="flex-1">{{ p.name }}</span>
                        <span class="text-xs text-gray-400">৳{{ p.price }}</span>
                      </label>
                    } @empty {
                      <p class="px-3 py-4 text-center text-xs text-gray-400">No products match.</p>
                    }
                  </div>
                  @if (totalProductPages() > 1) {
                    <div class="mt-2 flex items-center justify-between text-xs text-gray-500">
                      <button type="button" (click)="productGoTo(productPage() - 1)" [disabled]="productPage() === 0"
                        class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Prev</button>
                      <span>Page {{ productPage() + 1 }} / {{ totalProductPages() }} · {{ filteredProducts().length }} products</span>
                      <button type="button" (click)="productGoTo(productPage() + 1)" [disabled]="productPage() >= totalProductPages() - 1"
                        class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Next</button>
                    </div>
                  }
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
export class FlashSaleComponent implements OnInit {
  private readonly api = inject(FlashSaleService);
  private readonly productApi = inject(ProductService);

  readonly sales       = signal<FlashSaleResponse[]>([]);
  readonly products    = signal<ProductResponse[]>([]);
  readonly loading     = signal(false);
  readonly saving      = signal(false);
  readonly creating    = signal(false);
  readonly editingId   = signal<string | null>(null);
  readonly errorMessage = signal<string>('');
  readonly productSearch = signal<string>('');

  protected form: FlashSaleForm = { ...EMPTY_FORM, productIds: [] };

  readonly productPage = signal(0);
  readonly categoryFilter = signal<string>('all');
  private readonly productPageSize = 8;

  /** Categories present among the loaded products (for the picker filter). */
  protected readonly categoryOptions = computed(() => {
    const map = new Map<number, string>();
    for (const p of this.products()) {
      if (p.categoryId != null) map.set(p.categoryId, p.categoryName ?? ('Category ' + p.categoryId));
    }
    return [...map.entries()].map(([id, name]) => ({ id, name })).sort((a, b) => a.name.localeCompare(b.name));
  });

  protected readonly filteredProducts = computed(() => {
    const q = this.productSearch().trim().toLowerCase();
    const cat = this.categoryFilter();
    let list = this.products();
    if (cat !== 'all') list = list.filter((p) => String(p.categoryId) === cat);
    if (q) list = list.filter((p) => p.name.toLowerCase().includes(q) || (p.brand ?? '').toLowerCase().includes(q));
    return list;
  });

  protected onCategoryFilter(v: string): void {
    this.categoryFilter.set(v);
    this.productPage.set(0);
  }

  protected readonly totalProductPages = computed(() =>
    Math.max(1, Math.ceil(this.filteredProducts().length / this.productPageSize)));

  protected readonly pagedProducts = computed(() => {
    const start = this.productPage() * this.productPageSize;
    return this.filteredProducts().slice(start, start + this.productPageSize);
  });

  protected onProductSearch(q: string): void {
    this.productSearch.set(q);
    this.productPage.set(0);
  }

  protected productGoTo(p: number): void {
    if (p < 0 || p >= this.totalProductPages()) return;
    this.productPage.set(p);
  }

  ngOnInit(): void {
    this.reload();
    this.productApi.getProducts()
      .pipe(catchError(() => of([] as ProductResponse[])))
      .subscribe((list) => this.products.set(list));
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().pipe(catchError(() => of([] as FlashSaleResponse[]))).subscribe((list) => {
      this.sales.set(list);
      this.loading.set(false);
    });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.productSearch.set('');
    this.form = { ...EMPTY_FORM, productIds: [] };
  }

  protected startEdit(s: FlashSaleResponse): void {
    this.creating.set(false);
    this.editingId.set(s.id);
    this.errorMessage.set('');
    this.productSearch.set('');
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

  // ── Product selection helpers ──────────────────────────────────────────────
  protected isSelected(id: number): boolean {
    return this.form.productIds.includes(id);
  }

  protected toggleProduct(id: number): void {
    this.form.productIds = this.isSelected(id)
      ? this.form.productIds.filter((x) => x !== id)
      : [...this.form.productIds, id];
  }

  protected selectAllFiltered(): void {
    const ids = new Set(this.form.productIds);
    for (const p of this.filteredProducts()) ids.add(p.id);
    this.form.productIds = [...ids];
  }

  protected clearSelection(): void {
    this.form.productIds = [];
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
      if (editId) {
        this.sales.update((list) => list.map((s) => s.id === editId ? saved : s));
      } else {
        this.sales.update((list) => [...list, saved]);
      }
      this.cancelEdit();
    });
  }

  protected remove(s: FlashSaleResponse): void {
    if (!confirm(`Delete flash sale "${s.name}"?`)) return;
    this.api.delete(s.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return of(null); }))
      .subscribe(() => {
        this.sales.update((list) => list.filter((x) => x.id !== s.id));
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
