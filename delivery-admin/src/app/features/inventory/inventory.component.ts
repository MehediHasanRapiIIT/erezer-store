import { Component, signal, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import { StockService } from '../../core/services/stock.service';
import { BulkStockItem, InventorySummary, StockResponse, StockUpdateRequest } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';
import { PermissionService } from '../../core/services/permission.service';

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
  protected readonly perms = inject(PermissionService);

  readonly pageSize = 20;

  /** The page on screen. */
  products = signal<StockResponse[]>([]);
  page = signal(0);
  totalElements = signal(0);
  /** Every low or out-of-stock product, whatever page is showing. */
  private alerts = signal<StockResponse[]>([]);
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

  // Bulk update state (works on the page on screen)
  bulkMode = signal(false);
  bulkSelections = signal<number[]>([]);
  bulkItems = signal<Map<number, { quantity: number; unit: string }>>(new Map());
  bulkLoading = signal(false);
  bulkError = signal('');
  bulkSuccess = signal(false);

  readonly unitOptions = ['units', 'kg', 'g', 'litre', 'ml', 'packets', 'pieces', 'boxes', 'bottles', 'bags'];

  lowStockAlerts = computed(() => (this.alertsDismissed() ? [] : this.alerts()));

  ngOnInit(): void {
    this.loadPage(0);
    this.loadAlertsAndSummary();
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

  private loadAlertsAndSummary(): void {
    this.stockService.getLowStock().subscribe({ next: (list) => this.alerts.set(list), error: () => {} });
    this.stockService.getSummary().subscribe({ next: (s) => this.summary.set(s), error: () => {} });
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
    this.bulkMode.update(v => !v);
    if (!this.bulkMode()) {
      this.bulkSelections.set([]);
      this.bulkItems.set(new Map());
      this.bulkError.set('');
      this.bulkSuccess.set(false);
    }
  }

  isBulkSelected(productId: number): boolean {
    return this.bulkSelections().includes(productId);
  }

  toggleBulkSelection(productId: number): void {
    const current = this.bulkSelections();
    if (current.includes(productId)) {
      this.bulkSelections.set(current.filter(id => id !== productId));
    } else {
      this.bulkSelections.set([...current, productId]);
      // Pre-fill with current stock
      const product = this.products().find(p => p.productId === productId);
      if (product) {
        const map = new Map(this.bulkItems());
        map.set(productId, { quantity: product.stockQuantity, unit: product.unit || 'units' });
        this.bulkItems.set(map);
      }
    }
  }

  /** Selects every product on the page on screen. */
  selectAllForBulk(): void {
    if (this.bulkSelections().length === this.products().length) {
      this.bulkSelections.set([]);
    } else {
      const ids = this.products().map(p => p.productId);
      this.bulkSelections.set(ids);
      const map = new Map<number, { quantity: number; unit: string }>();
      this.products().forEach(p => map.set(p.productId, { quantity: p.stockQuantity, unit: p.unit || 'units' }));
      this.bulkItems.set(map);
    }
  }

  getBulkQty(productId: number): number {
    return this.bulkItems().get(productId)?.quantity ?? 0;
  }

  getBulkUnit(productId: number): string {
    return this.bulkItems().get(productId)?.unit ?? 'units';
  }

  setBulkQty(productId: number, qty: number): void {
    const map = new Map(this.bulkItems());
    const existing = map.get(productId) ?? { quantity: 0, unit: 'units' };
    map.set(productId, { ...existing, quantity: qty });
    this.bulkItems.set(map);
  }

  setBulkUnit(productId: number, unit: string): void {
    const map = new Map(this.bulkItems());
    const existing = map.get(productId) ?? { quantity: 0, unit: 'units' };
    map.set(productId, { ...existing, unit });
    this.bulkItems.set(map);
  }

  submitBulkUpdate(): void {
    const selections = this.bulkSelections();
    if (selections.length === 0) return;

    const updates: BulkStockItem[] = selections.map(id => ({
      productId: id,
      quantity: this.getBulkQty(id),
      unit: this.getBulkUnit(id),
    }));

    this.bulkLoading.set(true);
    this.bulkError.set('');
    this.bulkSuccess.set(false);

    this.stockService.bulkUpdateStock({ updates }).subscribe({
      next: (results) => {
        // Merge updated results back into the page on screen
        const resultMap = new Map(results.map(r => [r.productId, r]));
        this.products.update(list =>
          list.map(p => resultMap.has(p.productId) ? { ...p, ...resultMap.get(p.productId)! } : p)
        );
        this.loadAlertsAndSummary();
        this.bulkLoading.set(false);
        this.bulkSuccess.set(true);
        this.bulkSelections.set([]);
        this.alertsDismissed.set(false);
        setTimeout(() => { this.bulkSuccess.set(false); this.toggleBulkMode(); }, 1500);
      },
      error: (err) => {
        this.bulkError.set(parseApiError(err));
        this.bulkLoading.set(false);
      },
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
