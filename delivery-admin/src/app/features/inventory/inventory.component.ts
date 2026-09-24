import { Component, signal, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import { StockService } from '../../core/services/stock.service';
import {
  BulkStockItem, CategoryResponse, InventorySummary, StockResponse, StockUpdateRequest,
} from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';
import { PermissionService } from '../../core/services/permission.service';
import { NoticeService } from '../../core/services/notice.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { CategoryService } from '../../core/services/category.service';

/**
 * The Inventory page. The list is searched and paged by the server; the
 * restock alerts come from their own server list, so they cover every
 * product, not just the page on screen.
 */
@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [FormsModule, SidebarComponent, PagerComponent],
  templateUrl: './inventory.component.html',
})
export class InventoryComponent implements OnInit, OnDestroy {
  private stockService = inject(StockService);
  private readonly categoryService = inject(CategoryService);
  private readonly notices = inject(NoticeService);
  private readonly confirmer = inject(ConfirmService);
  protected readonly perms = inject(PermissionService);

  readonly pageSize = 20;

  /** The page on screen. */
  products = signal<StockResponse[]>([]);
  page = signal(0);
  totalElements = signal(0);
  /** One page of the low or out-of-stock products, whatever page of the list is showing. */
  private alerts = signal<StockResponse[]>([]);
  readonly alertsPageSize = 20;
  alertsPage = signal(0);
  /** How many products need restocking across every alerts page. */
  alertsTotal = signal(0);
  alertsLoading = signal(false);
  private latestAlertsRequest = 0;
  summary = signal<InventorySummary | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');
  searchQuery = signal('');
  private searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  // Stock update panel state
  activeProductId = signal<number | null>(null);
  operation = signal<'SET' | 'INCREMENT' | 'DECREMENT'>('SET');
  quantity = signal<number>(0);
  selectedUnit = signal<string>('units');
  threshold = signal<number | null>(null);
  updateLoading = signal(false);
  updateError = signal('');
  updateSuccess = signal(false);

  // Low stock alerts
  alertsDismissed = signal(false);

  // ── Bulk update ───────────────────────────────────────────────────────────
  bulkMode = signal(false);
  /** Which way to update: the products you tick, a whole category, or everything. */
  bulkTab = signal<'products' | 'category' | 'all'>('products');
  /** Ticked products and the new stock figure for each; kept while you page. */
  private readonly picks = signal<Map<number, number>>(new Map());
  bulkLoading = signal(false);
  bulkError = signal('');

  /** Category tab. */
  categories = signal<CategoryResponse[]>([]);
  categoryId = signal<number | null>(null);
  categoryOp = signal<'INCREMENT' | 'DECREMENT' | 'SET'>('INCREMENT');
  categoryQty = signal(0);

  /** Whole-shop tab. */
  allOp = signal<'INCREMENT' | 'DECREMENT' | 'SET'>('INCREMENT');
  allQty = signal(0);
  /** Products in the shop, for "this will change N products". */
  shopProductCount = signal(0);

  readonly unitOptions = ['units', 'kg', 'g', 'litre', 'ml', 'packets', 'pieces', 'boxes', 'bottles', 'bags'];

  readonly bulkTabs: { id: 'products' | 'category' | 'all'; label: string }[] = [
    { id: 'products', label: 'Chosen products' },
    { id: 'category', label: 'By category' },
    { id: 'all', label: 'All products' },
  ];

  /** Columns in the table right now, for rows that span all of them. */
  columnCount(): number {
    return this.bulkMode() && this.bulkTab() === 'products' && this.perms.can('inventory.edit') ? 8 : 7;
  }

  lowStockAlerts = computed(() => (this.alertsDismissed() ? [] : this.alerts()));

  ngOnInit(): void {
    this.loadPage(0);
    this.loadAlerts(0);
    this.loadSummary();
    this.categoryService.getCategories().subscribe({ next: (list) => this.categories.set(list), error: () => {} });
    // The shop's product count, so a whole-shop change can say what it will touch.
    this.stockService.getStockPage('', 0, 1).subscribe({
      next: (p) => this.shopProductCount.set(p.totalElements), error: () => {},
    });
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  /** One page from the server, for the current search. */
  loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.stockService.getStockPage(this.searchQuery().trim(), page, this.pageSize).subscribe({
      next: (data) => {
        if (request !== this.latestRequest) return;
        this.products.set(data.content);
        this.page.set(data.number);
        this.totalElements.set(data.totalElements);
        this.isLoading.set(false);
      },
      error: (err) => {
        if (request !== this.latestRequest) return;
        this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  /** One page of restock alerts from the server. */
  loadAlerts(page: number): void {
    const request = ++this.latestAlertsRequest;
    this.alertsLoading.set(true);
    this.stockService.getLowStock(page, this.alertsPageSize).subscribe({
      next: (data) => {
        if (request !== this.latestAlertsRequest) return;
        this.alertsLoading.set(false);
        // The last alert on this page was restocked: show the page before it.
        if (data.content.length === 0 && page > 0) {
          this.loadAlerts(Math.min(page - 1, Math.max(data.totalPages - 1, 0)));
          return;
        }
        this.alerts.set(data.content);
        this.alertsPage.set(data.number);
        this.alertsTotal.set(data.totalElements);
      },
      error: () => {
        if (request === this.latestAlertsRequest) this.alertsLoading.set(false);
      },
    });
  }

  private loadSummary(): void {
    this.stockService.getSummary().subscribe({ next: (s) => this.summary.set(s), error: () => {} });
  }

  /** After a stock change: the alerts page on screen and the summary cards. */
  private loadAlertsAndSummary(): void {
    this.loadAlerts(this.alertsPage());
    this.loadSummary();
  }

  dismissAlerts(): void {
    this.alertsDismissed.set(true);
  }

  // ---- Single update ----

  openUpdatePanel(product: StockResponse): void {
    this.activeProductId.set(product.productId);
    this.operation.set('SET');
    this.quantity.set(product.stockQuantity);
    this.selectedUnit.set(product.unit || 'units');
    this.threshold.set(product.lowStockThreshold ?? null);
    this.updateError.set('');
    this.updateSuccess.set(false);
  }

  closeUpdatePanel(): void {
    this.activeProductId.set(null);
    this.updateError.set('');
    this.updateSuccess.set(false);
  }

  submitUpdate(): void {
    const id = this.activeProductId();
    if (!id || this.quantity() < 0) return;

    const request: StockUpdateRequest = {
      operation: this.operation(),
      quantity: this.quantity(),
      unit: this.selectedUnit(),
      lowStockThreshold: this.threshold() ?? undefined,
    };

    this.updateLoading.set(true);
    this.updateError.set('');
    this.updateSuccess.set(false);

    this.stockService.updateStock(id, request).subscribe({
      next: (updated) => {
        this.products.update((list) =>
          list.map((p) =>
            p.productId === id
              ? { ...p, stockQuantity: updated.stockQuantity, stockStatus: updated.stockStatus }
              : p
          )
        );
        this.loadAlertsAndSummary();
        this.updateLoading.set(false);
        this.updateSuccess.set(true);
        this.notices.success('Stock updated', `${updated.productName}: ${updated.stockQuantity} ${updated.unit}`);
        this.alertsDismissed.set(false); // refresh alerts
        setTimeout(() => this.closeUpdatePanel(), 1200);
      },
      error: (err) => {
        this.updateError.set(parseApiError(err));
        this.updateLoading.set(false);
      },
    });
  }

  activeProduct = computed(() =>
    this.products().find((p) => p.productId === this.activeProductId()) ?? null
  );

  // ---- Bulk update ----

  toggleBulkMode(): void {
    this.bulkMode.update((v) => !v);
    if (!this.bulkMode()) {
      this.picks.set(new Map());
      this.bulkError.set('');
    } else {
      this.closeUpdatePanel();
    }
  }

  /** How many products are ticked, across every page. */
  pickedCount(): number {
    return this.picks().size;
  }

  isPicked(productId: number): boolean {
    return this.picks().has(productId);
  }

  togglePick(product: StockResponse): void {
    const map = new Map(this.picks());
    if (map.has(product.productId)) {
      map.delete(product.productId);
    } else {
      map.set(product.productId, product.stockQuantity);
    }
    this.picks.set(map);
  }

  pickedQty(productId: number): number {
    return this.picks().get(productId) ?? 0;
  }

  setPickedQty(productId: number, quantity: number): void {
    const map = new Map(this.picks());
    map.set(productId, Math.max(0, Math.trunc(quantity || 0)));
    this.picks.set(map);
  }

  /** True when every product on the page on screen is ticked. */
  allOnPagePicked(): boolean {
    const rows = this.products();
    return rows.length > 0 && rows.every((p) => this.picks().has(p.productId));
  }

  togglePageSelection(): void {
    const map = new Map(this.picks());
    if (this.allOnPagePicked()) {
      this.products().forEach((p) => map.delete(p.productId));
    } else {
      this.products().forEach((p) => map.set(p.productId, map.get(p.productId) ?? p.stockQuantity));
    }
    this.picks.set(map);
  }

  clearPicks(): void {
    this.picks.set(new Map());
  }

  /** Applies the figure typed against each ticked product. */
  async applyPicked(): Promise<void> {
    const picks = this.picks();
    if (picks.size === 0 || this.bulkLoading()) return;
    const ok = await this.confirmer.ask({
      title: `Update stock for ${picks.size} product${picks.size === 1 ? '' : 's'}?`,
      message: 'Each ticked product is set to the number you typed for it.',
      confirmLabel: 'Update stock',
    });
    if (!ok) return;

    const updates: BulkStockItem[] = [...picks].map(([productId, quantity]) => ({ productId, quantity }));
    this.bulkLoading.set(true);
    this.bulkError.set('');
    this.stockService.bulkUpdateStock({ updates }).subscribe({
      next: (results) => {
        this.bulkLoading.set(false);
        this.picks.set(new Map());
        this.notices.success('Stock updated', `${results.length} product${results.length === 1 ? '' : 's'}`);
        this.afterBulkChange();
      },
      error: (err) => {
        this.bulkError.set(parseApiError(err));
        this.bulkLoading.set(false);
      },
    });
  }

  /** Products in the chosen category, for the "this will change N" line. */
  categoryProductCount(): number {
    return this.categories().find((c) => c.id === this.categoryId())?.productCount ?? 0;
  }

  private operationWords(op: 'INCREMENT' | 'DECREMENT' | 'SET', qty: number): string {
    if (op === 'INCREMENT') return `Add ${qty} to`;
    if (op === 'DECREMENT') return `Remove ${qty} from`;
    return `Set stock to ${qty} for`;
  }

  /** What the category tab will do, in words. */
  categoryPreview(): string {
    const name = this.categories().find((c) => c.id === this.categoryId())?.name;
    if (!name) return 'Choose a category.';
    const n = this.categoryProductCount();
    return `${this.operationWords(this.categoryOp(), this.categoryQty())} ${n} product${n === 1 ? '' : 's'} in ${name}.`;
  }

  /** What the whole-shop tab will do, in words. */
  allPreview(): string {
    const n = this.shopProductCount();
    return `${this.operationWords(this.allOp(), this.allQty())} all ${n} product${n === 1 ? '' : 's'} in the shop.`;
  }

  async applyCategory(): Promise<void> {
    const categoryId = this.categoryId();
    if (!categoryId || this.bulkLoading()) return;
    const name = this.categories().find((c) => c.id === categoryId)?.name ?? 'this category';
    await this.applyScope(
      { scope: 'CATEGORY', categoryId, operation: this.categoryOp(), quantity: this.categoryQty() },
      this.categoryOp(), this.categoryQty(), this.categoryProductCount(), name);
  }

  async applyAll(): Promise<void> {
    if (this.bulkLoading()) return;
    await this.applyScope(
      { scope: 'ALL', operation: this.allOp(), quantity: this.allQty() },
      this.allOp(), this.allQty(), this.shopProductCount(), 'the whole shop');
  }

  private async applyScope(
    request: { scope: 'CATEGORY' | 'ALL'; categoryId?: number; operation: 'SET' | 'INCREMENT' | 'DECREMENT'; quantity: number },
    op: 'SET' | 'INCREMENT' | 'DECREMENT', qty: number, count: number, where: string): Promise<void> {
    if (op !== 'SET' && qty <= 0) {
      this.bulkError.set('Enter how many units to add or remove.');
      return;
    }
    if (count === 0) {
      this.bulkError.set('There are no products to update.');
      return;
    }
    const ok = await this.confirmer.ask({
      title: `${this.operationWords(op, qty)} ${count} product${count === 1 ? '' : 's'}?`,
      message: op === 'SET'
        ? `Every product in ${where} will have exactly ${qty} in stock, whatever it has now. This cannot be undone.`
        : op === 'DECREMENT'
          ? `Anything with less than ${qty} in stock will be set to 0.`
          : `This adds ${qty} to what each product already has.`,
      confirmLabel: op === 'INCREMENT' ? 'Add stock' : op === 'DECREMENT' ? 'Remove stock' : 'Set stock',
      danger: op !== 'INCREMENT',
    });
    if (!ok) return;

    this.bulkLoading.set(true);
    this.bulkError.set('');
    this.stockService.adjustStock(request).subscribe({
      next: (result) => {
        this.bulkLoading.set(false);
        this.notices.success('Stock updated', result.message);
        this.afterBulkChange();
      },
      error: (err) => {
        this.bulkError.set(parseApiError(err));
        this.bulkLoading.set(false);
      },
    });
  }

  /** Everything on screen that a stock change can move. */
  private afterBulkChange(): void {
    this.loadPage(this.page());
    this.alertsDismissed.set(false);
    this.loadAlertsAndSummary();
    this.stockService.getStockPage('', 0, 1).subscribe({
      next: (p) => this.shopProductCount.set(p.totalElements), error: () => {},
    });
  }

  // ---- Display helpers ----

  stockBadgeClass(status: string): string {
    if (status === 'IN_STOCK') return 'bg-emerald-100 text-emerald-700';
    if (status === 'LOW_STOCK') return 'bg-orange-100 text-orange-700';
    return 'bg-red-100 text-red-600';
  }

  stockLabel(status: string): string {
    if (status === 'IN_STOCK') return 'In Stock';
    if (status === 'LOW_STOCK') return 'Low Stock';
    return 'Out of Stock';
  }

  stockQtyClass(status: string): string {
    if (status === 'IN_STOCK') return 'text-gray-800';
    if (status === 'LOW_STOCK') return 'text-orange-500 font-bold';
    return 'text-red-500 font-bold';
  }

  projectedQty(product: StockResponse): number {
    if (this.operation() === 'SET') return this.quantity();
    if (this.operation() === 'INCREMENT') return product.stockQuantity + this.quantity();
    return product.stockQuantity - this.quantity();
  }
}
