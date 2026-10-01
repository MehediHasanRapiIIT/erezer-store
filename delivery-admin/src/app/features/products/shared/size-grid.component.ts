import { Component, computed, input, model, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { VariantRequest } from '../../../core/services/variant.service';

/** The shop's clothing sizes, in the order customers see them. */
export const SIZE_OPTIONS = ['S', 'M', 'L', 'XL', 'XXL'];

/** One size in the grid: ticked or not, with its stock and an optional price of its own. */
export interface SizeRow {
  size: string;
  picked: boolean;
  stock: number | null;
  price: number | null;
}

export function emptySizeRows(): SizeRow[] {
  return SIZE_OPTIONS.map((size) => ({ size, picked: false, stock: null, price: null }));
}

/** The ticked rows, as the server takes them. */
export function pickedSizes(rows: SizeRow[]): VariantRequest[] {
  return rows.filter((r) => r.picked).map((r) => ({
    size: r.size,
    stockQuantity: r.stock ?? 0,
    priceOverride: r.price != null && r.price > 0 ? r.price : null,
  }));
}

/**
 * Several sizes at once: tick them, type the stock for each, done. Used when
 * adding a product, and on the edit page's "Add several sizes", where the sizes
 * the product already has are shown but can't be ticked again.
 */
@Component({
  selector: 'app-size-grid',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div>
      <table class="w-full text-sm" data-testid="size-grid">
        <thead>
          <tr class="border-b border-gray-100 text-left text-xs uppercase text-gray-400">
            <th class="w-10 py-2 pl-1">
              <input type="checkbox" aria-label="Tick every size"
                [checked]="allPicked()" [disabled]="disabled() || free().length === 0"
                (change)="pickAll(!allPicked())"
                class="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600" />
            </th>
            <th class="py-2">Size</th>
            <th class="py-2">Stock</th>
            <th class="py-2">Own price <span class="font-normal normal-case text-gray-400">(optional)</span></th>
          </tr>
        </thead>
        <tbody class="divide-y divide-gray-50">
          @for (row of rows(); track row.size; let i = $index) {
            <tr [class.bg-blue-50]="row.picked" [class.opacity-50]="isTaken(row.size)">
              <td class="py-2 pl-1">
                <input type="checkbox" [attr.aria-label]="'Size ' + row.size"
                  [attr.data-testid]="'size-' + row.size"
                  [checked]="row.picked" [disabled]="disabled() || isTaken(row.size)"
                  (change)="patch(i, { picked: !row.picked })"
                  class="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600" />
              </td>
              <td class="py-2 font-semibold text-gray-800">
                {{ row.size }}
                @if (isTaken(row.size)) {
                  <span class="ml-1 text-xs font-normal text-gray-400">already added</span>
                }
              </td>
              <td class="py-2 pr-2">
                <input type="number" min="0" step="1" placeholder="0"
                  [attr.aria-label]="'Stock for ' + row.size" [attr.data-testid]="'stock-' + row.size"
                  [ngModel]="row.stock" (ngModelChange)="setStock(i, $event)"
                  [disabled]="disabled() || !row.picked || !canSetStock()"
                  class="w-24 rounded-lg border border-gray-200 px-2.5 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400" />
              </td>
              <td class="py-2">
                <div class="relative w-28">
                  <span class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-gray-400">৳</span>
                  <input type="number" min="0" step="any" placeholder="same"
                    [attr.aria-label]="'Own price for ' + row.size"
                    [ngModel]="row.price" (ngModelChange)="patch(i, { price: $event === '' || $event == null ? null : +$event })"
                    [disabled]="disabled() || !row.picked || !canSetPrice()"
                    class="w-full rounded-lg border border-gray-200 py-1.5 pl-6 pr-2 text-sm outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400" />
                </div>
              </td>
            </tr>
          }
        </tbody>
      </table>

      <!-- One stock figure for every ticked size, for when they all start the same. -->
      @if (canSetStock() && pickedCount() > 1) {
        <div class="mt-3 flex flex-wrap items-center gap-2 text-xs text-gray-600">
          <span>Same stock for all {{ pickedCount() }} ticked:</span>
          <input type="number" min="0" step="1" aria-label="Stock for every ticked size"
            [ngModel]="sameStock()" (ngModelChange)="sameStock.set($event === '' || $event == null ? null : +$event)"
            [disabled]="disabled()"
            class="w-20 rounded-lg border border-gray-200 px-2.5 py-1 text-sm outline-none focus:ring-2 focus:ring-blue-300" />
          <button type="button" (click)="applySameStock()" [disabled]="disabled() || sameStock() == null"
            class="rounded-lg border border-gray-200 px-2.5 py-1 font-medium text-gray-700 hover:bg-gray-50 disabled:opacity-40">Apply</button>
        </div>
      }

      @if (!canSetStock()) {
        <p class="mt-2 text-xs text-gray-400">Stock needs the “Change stock” permission; sizes start at 0.</p>
      }
      @if (!canSetPrice()) {
        <p class="mt-1 text-xs text-gray-400">A size's own price needs the “Change prices” permission.</p>
      }
    </div>
  `,
})
export class SizeGridComponent {
  readonly rows = model<SizeRow[]>(emptySizeRows());
  /** Sizes the product already has; shown, but they can't be ticked again. */
  readonly taken = input<string[]>([]);
  readonly canSetStock = input(true);
  readonly canSetPrice = input(true);
  readonly disabled = input(false);

  readonly sameStock = signal<number | null>(null);

  private readonly takenSet = computed(() => new Set(this.taken().map((s) => s.trim().toUpperCase())));
  readonly free = computed(() => this.rows().filter((r) => !this.isTaken(r.size)));
  readonly pickedCount = computed(() => this.rows().filter((r) => r.picked).length);
  readonly allPicked = computed(() => this.free().length > 0 && this.free().every((r) => r.picked));

  isTaken(size: string): boolean {
    return this.takenSet().has(size.toUpperCase());
  }

  patch(index: number, change: Partial<SizeRow>): void {
    this.rows.update((rows) => rows.map((r, i) => (i === index ? { ...r, ...change } : r)));
  }

  setStock(index: number, value: unknown): void {
    const n = value === '' || value == null ? null : Math.max(0, Math.trunc(+value));
    this.patch(index, { stock: Number.isFinite(n as number) ? n : null });
  }

  pickAll(on: boolean): void {
    this.rows.update((rows) => rows.map((r) => (this.isTaken(r.size) ? r : { ...r, picked: on })));
  }

  applySameStock(): void {
    const stock = this.sameStock();
    if (stock == null) return;
    const n = Math.max(0, Math.trunc(stock));
    this.rows.update((rows) => rows.map((r) => (r.picked ? { ...r, stock: n } : r)));
  }
}
