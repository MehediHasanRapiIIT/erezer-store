import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { StockService } from '../../core/services/stock.service';
import { BulkStockItem, InventorySummary, StockResponse, StockUpdateRequest } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  templateUrl: './inventory.component.html',
})
export class InventoryComponent implements OnInit {
  private stockService = inject(StockService);

  products = signal<StockResponse[]>([]);
  summary = signal<InventorySummary | null>(null);
  isLoading = signal(true);
  errorMessage = signal('');
  searchQuery = signal('');

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

  // Bulk update state
  bulkMode = signal(false);
  bulkSelections = signal<number[]>([]);
  bulkItems = signal<Map<number, { quantity: number; unit: string }>>(new Map());
  bulkLoading = signal(false);
  bulkError = signal('');
  bulkSuccess = signal(false);

  readonly unitOptions = ['units', 'kg', 'g', 'litre', 'ml', 'packets', 'pieces', 'boxes', 'bottles', 'bags'];

  filtered = computed(() => {
    const q = this.searchQuery().toLowerCase();
    if (!q) return this.products();
    return this.products().filter(
      (p) =>
        p.productName.toLowerCase().includes(q) ||
        (p.sku ?? '').toLowerCase().includes(q)
    );
  });

  lowStockAlerts = computed(() => {
    if (this.alertsDismissed()) return [];
    return this.products().filter(
      (p) => p.stockStatus === 'LOW_STOCK' || p.stockStatus === 'OUT_OF_STOCK'
    );
  });

  ngOnInit(): void {
    this.loadData();
  }

  private loadData(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');

    this.stockService.getAllStock().subscribe({
      next: (data) => {
        this.products.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });

    this.stockService.getSummary().subscribe({
      next: (s) => this.summary.set(s),
      error: () => {},
    });
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
        this.stockService.getSummary().subscribe({ next: (s) => this.summary.set(s), error: () => {} });
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
        // Merge updated results back into products list
        const resultMap = new Map(results.map(r => [r.productId, r]));
        this.products.update(list =>
          list.map(p => resultMap.has(p.productId) ? { ...p, ...resultMap.get(p.productId)! } : p)
        );
        this.stockService.getSummary().subscribe({ next: (s) => this.summary.set(s), error: () => {} });
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
