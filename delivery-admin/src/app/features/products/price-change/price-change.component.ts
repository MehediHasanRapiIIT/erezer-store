import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../../shared/pager/pager.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/api.models';
import {
  PriceChangePreview,
  PriceChangeRequest,
  PriceChangeService,
  PriceMode,
  SaleMode,
} from '../../../core/services/price-change.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NoticeService } from '../../../core/services/notice.service';

const PRICE_MODES: { value: PriceMode; label: string; unit: '৳' | '%' | null }[] = [
  { value: 'KEEP',          label: 'Keep prices as they are', unit: null },
  { value: 'SET',           label: 'Set every price to',      unit: '৳' },
  { value: 'RAISE_AMOUNT',  label: 'Raise by an amount',      unit: '৳' },
  { value: 'LOWER_AMOUNT',  label: 'Lower by an amount',      unit: '৳' },
  { value: 'RAISE_PERCENT', label: 'Raise by a percentage',   unit: '%' },
  { value: 'LOWER_PERCENT', label: 'Lower by a percentage',   unit: '%' },
];

const SALE_MODES: { value: SaleMode; label: string }[] = [
  { value: 'KEEP',   label: "Keep each product's sale % (worked out on the new price)" },
  { value: 'SET',    label: 'Set one sale % for every product' },
  { value: 'REMOVE', label: 'Remove the sale' },
];

/**
 * Change prices across a category: choose the change, see every product's old
 * and new prices, untick any to leave out, then apply. Nothing is saved until
 * Apply, and then all together or not at all.
 */
@Component({
  selector: 'app-price-change',
  standalone: true,
  imports: [FormsModule, RouterLink, SidebarComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div>
            <h1 class="text-lg font-bold text-gray-900">Change prices by category</h1>
            <p class="text-xs text-gray-400">Nothing is saved until you apply. One product at a time still works on its edit page.</p>
          </div>
          <a routerLink="/products" class="text-sm text-blue-600 underline">Back to products</a>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto space-y-5">

            <!-- The change -->
            <section class="bg-white rounded-xl border border-gray-200 p-5 space-y-4">
              <label class="block text-sm font-medium text-gray-700">
                Category
                <select [ngModel]="categoryId()" (ngModelChange)="categoryId.set($event); clearPreview()"
                  class="mt-1 block w-full max-w-sm rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                  <option [ngValue]="null">Choose a category…</option>
                  @for (c of categories(); track c.id) {
                    <option [ngValue]="c.id">{{ c.name }} ({{ c.productCount }} products)</option>
                  }
                </select>
              </label>

              <div class="grid gap-4 md:grid-cols-2">
                <fieldset class="rounded-lg border border-gray-100 p-4">
                  <legend class="px-1 text-sm font-semibold text-gray-800">Price</legend>
                  <div class="flex flex-wrap items-center gap-2">
                    <select [ngModel]="priceMode()" (ngModelChange)="priceMode.set($event); clearPreview()" aria-label="Price change"
                      class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm">
                      @for (m of priceModes; track m.value) { <option [value]="m.value">{{ m.label }}</option> }
                    </select>
                    @if (priceUnit(); as unit) {
                      <div class="flex items-center gap-1">
                        @if (unit === '৳') { <span class="text-sm text-gray-500">৳</span> }
                        <input type="number" min="0" step="1" [ngModel]="priceValue()" aria-label="Amount"
                          (ngModelChange)="priceValue.set($event); clearPreview()"
                          class="w-28 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                        @if (unit === '%') { <span class="text-sm text-gray-500">%</span> }
                      </div>
                    }
                  </div>
                  <p class="mt-2 text-xs text-gray-400">
                    New prices are rounded to whole taka. Sizes with their own price get the same change;
                    with "Set every price to", each size keeps its difference from the product price.
                  </p>
                </fieldset>

                <fieldset class="rounded-lg border border-gray-100 p-4">
                  <legend class="px-1 text-sm font-semibold text-gray-800">Sale discount</legend>
                  <div class="space-y-1.5">
                    @for (m of saleModes; track m.value) {
                      <label class="flex items-center gap-2 text-sm text-gray-700">
                        <input type="radio" name="saleMode" [value]="m.value" [ngModel]="saleMode()"
                          (ngModelChange)="saleMode.set($event); clearPreview()" />
                        {{ m.label }}
                      </label>
                    }
                  </div>
                  @if (saleMode() === 'SET') {
                    <div class="mt-2 flex items-center gap-1">
                      <input type="number" min="1" max="99" step="1" [ngModel]="salePercent()" aria-label="Sale percentage"
                        (ngModelChange)="salePercent.set($event); clearPreview()"
                        class="w-24 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                      <span class="text-sm text-gray-500">% off</span>
                    </div>
                  }
                  <p class="mt-2 text-xs text-gray-400">As today, a size with its own price sells at that price, without the sale.</p>
                </fieldset>
              </div>

              @if (error()) {
                <p class="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{{ error() }}</p>
              }
              <button type="button" (click)="showPreview()" [disabled]="!categoryId() || loading()"
                class="rounded-lg bg-gray-900 px-4 py-2 text-sm font-semibold text-white hover:bg-black disabled:opacity-50">
                {{ loading() ? 'Working it out…' : 'Show preview' }}
              </button>
            </section>

            <!-- Preview: one page at a time; ticks are remembered across pages and searches -->
            @if (preview(); as p) {
              <section class="bg-white rounded-xl border border-gray-200 overflow-hidden">
                <div class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-100 px-5 py-3">
                  <div class="text-sm text-gray-700">
                    <strong>{{ p.categoryName }}</strong>: {{ p.productCount }} products,
                    {{ p.changedCount }} would change
                    @if (p.problemCount) { , <span class="text-red-600">{{ p.problemCount }} can't be changed</span> }
                    · <strong>{{ tickedCount() }}</strong> ticked
                  </div>
                  <div class="flex flex-wrap items-center gap-2 text-xs">
                    <input type="search" [ngModel]="search()" (ngModelChange)="onSearch($event)"
                      placeholder="Find a product by name, code or SKU…" aria-label="Find a product"
                      class="w-60 rounded-lg border border-gray-200 px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
                    <button type="button" (click)="tickAll(true)" class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50">
                      Tick all ({{ changeable().size }})
                    </button>
                    <button type="button" (click)="tickAll(false)" class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50">Untick all</button>
                  </div>
                </div>
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
                        <th class="w-10 px-4 py-2.5"></th>
                        <th class="px-4 py-2.5 text-left">Product</th>
                        <th class="px-4 py-2.5 text-left">Price</th>
                        <th class="px-4 py-2.5 text-left">Sale price</th>
                        <th class="px-4 py-2.5 text-left">Sizes with their own price</th>
                      </tr>
                    </thead>
                    <tbody class="divide-y divide-gray-50">
                      @for (r of p.rows; track r.productId) {
                        <tr [class.bg-red-50]="r.problem" [class.opacity-60]="!r.problem && !isTicked(r.productId)">
                          <td class="px-4 py-2.5 text-center">
                            <input type="checkbox" [checked]="isTicked(r.productId)" [disabled]="!!r.problem || !r.changed"
                              (change)="toggle(r.productId)" [attr.aria-label]="'Change ' + r.name" />
                          </td>
                          <td class="px-4 py-2.5">
                            <div class="flex items-center gap-2">
                              @if (r.imageUrl) { <img [src]="r.imageUrl" alt="" class="h-8 w-8 rounded object-cover" /> }
                              <div>
                                <p class="font-medium text-gray-800">{{ r.name }}</p>
                                @if (r.productCode) { <p class="font-mono text-xs text-gray-400">Code: {{ r.productCode }}</p> }
                                @if (r.problem) { <p class="text-xs font-medium text-red-600">{{ r.problem }}</p> }
                                @else if (!r.changed) { <p class="text-xs text-gray-400">No change</p> }
                              </div>
                            </div>
                          </td>
                          <td class="px-4 py-2.5 whitespace-nowrap tabular-nums">
                            <span class="text-gray-400">{{ money(r.oldPrice) }}</span> →
                            <span class="font-semibold text-gray-900">{{ money(r.newPrice) }}</span>
                          </td>
                          <td class="px-4 py-2.5 whitespace-nowrap tabular-nums">
                            @if (r.oldSalePrice == null && r.newSalePrice == null) {
                              <span class="text-gray-300">—</span>
                            } @else {
                              <span class="text-gray-400">{{ r.oldSalePrice == null ? 'none' : money(r.oldSalePrice) }}</span> →
                              <span class="font-semibold text-gray-900">{{ r.newSalePrice == null ? 'none' : money(r.newSalePrice) }}</span>
                            }
                          </td>
                          <td class="px-4 py-2.5 text-xs tabular-nums text-gray-600">
                            @for (s of r.sizes; track s.variantId) {
                              <div class="whitespace-nowrap">{{ s.size }}: {{ money(s.oldPrice) }} → <strong>{{ money(s.newPrice) }}</strong></div>
                            } @empty { <span class="text-gray-300">—</span> }
                          </td>
                        </tr>
                      } @empty {
                        <tr><td colspan="5" class="px-4 py-6 text-center text-gray-400">
                          {{ search().trim() ? 'No product matches your search.' : 'This category has no products.' }}
                        </td></tr>
                      }
                    </tbody>
                  </table>
                </div>
                <app-pager [page]="p.page" [size]="p.size" [total]="p.totalRows" [disabled]="loading()"
                  (pageChange)="loadPage($event)" />
                <div class="flex flex-wrap items-center justify-between gap-3 border-t border-gray-100 bg-gray-50 px-5 py-3">
                  <p class="text-xs text-gray-500">
                    Past orders keep their prices. The Activity log will list every old and new price.
                  </p>
                  <button type="button" (click)="apply(p)" [disabled]="tickedCount() === 0 || applying()"
                    class="rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white hover:bg-blue-700 disabled:opacity-50">
                    {{ applying() ? 'Saving…' : 'Apply to ' + tickedCount() + ' product' + (tickedCount() === 1 ? '' : 's') }}
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
export class PriceChangeComponent implements OnInit {
  private readonly api = inject(PriceChangeService);
  private readonly categoryApi = inject(CategoryService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  protected readonly priceModes = PRICE_MODES;
  protected readonly saleModes = SALE_MODES;

  readonly categories = signal<CategoryResponse[]>([]);
  readonly categoryId = signal<number | null>(null);
  readonly priceMode = signal<PriceMode>('RAISE_PERCENT');
  readonly priceValue = signal<number | null>(null);
  readonly saleMode = signal<SaleMode>('KEEP');
  readonly salePercent = signal<number | null>(null);

  readonly preview = signal<PriceChangePreview | null>(null);
  /** Products that would change and can be changed, over the whole category (from the server). */
  readonly changeable = signal<ReadonlySet<number>>(new Set());
  /** The ones the person unticked. Kept by product, so it survives changing page or searching. */
  private readonly unticked = signal<ReadonlySet<number>>(new Set());
  readonly search = signal('');
  private readonly search$ = new Subject<string>();
  private readonly pageSize = 20;
  /** Numbers each preview request, so an answer overtaken by a newer one is ignored. */
  private latestRequest = 0;
  readonly loading = signal(false);
  readonly applying = signal(false);
  readonly error = signal('');

  protected readonly priceUnit = computed(() => PRICE_MODES.find((m) => m.value === this.priceMode())?.unit ?? null);
  protected readonly tickedCount = computed(() => {
    const unticked = this.unticked();
    return [...this.changeable()].filter((id) => !unticked.has(id)).length;
  });

  ngOnInit(): void {
    const fromList = Number(this.route.snapshot.queryParamMap.get('categoryId'));
    if (fromList) this.categoryId.set(fromList);
    this.categoryApi.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => this.categories.set([...list].sort((a, b) => a.name.localeCompare(b.name))));
    // Only while a preview is shown; clearing the preview also clears the search.
    this.search$.pipe(debounceTime(300), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => { if (this.preview()) this.loadPage(0); });
  }

  /** Any change to the form throws the preview away, so a stale one can never be applied. */
  protected clearPreview(): void {
    this.latestRequest++;
    this.preview.set(null);
    this.changeable.set(new Set());
    this.unticked.set(new Set());
    this.search.set('');
    this.error.set('');
  }

  /** A fresh preview: every product that changes is ticked again, from the first page. */
  protected showPreview(): void {
    this.clearPreview();
    this.loadPage(0);
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.search$.next(q);
  }

  protected loadPage(page: number): void {
    const request = this.request();
    if (!request) return;
    const call = ++this.latestRequest;
    this.loading.set(true);
    this.error.set('');
    this.api.preview(request, this.search().trim(), page, this.pageSize).subscribe({
      next: (p) => {
        if (call !== this.latestRequest) return;
        this.preview.set(p);
        this.changeable.set(new Set(p.changeableIds));
        this.loading.set(false);
      },
      error: (err) => {
        if (call !== this.latestRequest) return;
        this.error.set(parseApiError(err));
        this.loading.set(false);
      },
    });
  }

  protected async apply(p: PriceChangePreview): Promise<void> {
    const request = this.request();
    const unticked = this.unticked();
    const productIds = [...this.changeable()].filter((id) => !unticked.has(id));
    if (!request || productIds.length === 0) return;
    const count = productIds.length === 1 ? '1 product' : `${productIds.length} products`;
    const ok = await this.confirmer.ask({
      title: `Change prices for ${count} in ${p.categoryName}?`,
      message: 'The Activity log keeps the old prices.',
      confirmLabel: 'Change prices',
    });
    if (!ok) return;
    this.applying.set(true);
    this.error.set('');
    this.api.apply({ ...request, productIds }).subscribe({
      next: (result) => {
        this.applying.set(false);
        this.clearPreview();
        this.notices.success('Prices changed',
          `Prices changed for ${result.changedCount} of ${result.productCount} ticked products in `
          + `${result.categoryName}. The Activity log lists every old and new price.`);
      },
      error: (err) => {
        this.applying.set(false);
        this.error.set(parseApiError(err));
      },
    });
  }

  protected isTicked(id: number): boolean {
    return this.changeable().has(id) && !this.unticked().has(id);
  }

  protected toggle(id: number): void {
    this.unticked.update((set) => {
      const next = new Set(set);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  /** Every page, not just the one on screen. */
  protected tickAll(on: boolean): void {
    this.unticked.set(on ? new Set() : new Set(this.changeable()));
  }

  protected money(amount: number | null): string {
    if (amount == null) return '—';
    return '৳' + amount.toLocaleString('en-US', { maximumFractionDigits: 2 });
  }

  private request(): PriceChangeRequest | null {
    const categoryId = this.categoryId();
    if (categoryId == null) return null;
    return {
      categoryId,
      priceMode: this.priceMode(),
      priceValue: this.priceUnit() ? this.priceValue() : null,
      saleMode: this.saleMode(),
      salePercent: this.saleMode() === 'SET' ? this.salePercent() : null,
    };
  }
}
