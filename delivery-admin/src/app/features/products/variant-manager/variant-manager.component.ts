import { Component, inject, input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import {
  VariantRequest,
  VariantResponse,
  VariantService,
} from '../../../core/services/variant.service';
import { parseApiError } from '../../../core/utils/api-error.util';

interface VariantForm {
  size: string;
  sku: string;
  stockQuantity: number | null;
  priceOverride: number | null;
}

const EMPTY_FORM: VariantForm = {
  size: '', sku: '', stockQuantity: 0, priceOverride: null,
};

/** Fixed clothing sizes for this store — no colour variants. */
const SIZE_OPTIONS = ['S', 'M', 'L', 'XL', 'XXL'];

@Component({
  selector: 'app-variant-manager',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5">
      <header class="mb-3 flex items-center justify-between">
        <div>
          <h2 class="font-bold text-gray-900">Variants</h2>
          <p class="text-xs text-gray-500">Size &amp; stock per variant. Customers must pick a size if any exist.</p>
        </div>
        <button
          type="button"
          (click)="startCreate()"
          class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
          + Add variant
        </button>
      </header>

      @if (error()) {
        <p class="mb-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="text-sm text-gray-400">Loading variants…</p>
      } @else if (variants().length === 0 && !editingId() && !creating()) {
        <p class="rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-500">
          No variants yet. Add one to expose size options on the storefront.
        </p>
      }

      <!-- Variant list -->
      @if (variants().length > 0) {
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
              <th class="px-3 py-2 text-left">Size</th>
              <th class="px-3 py-2 text-left">SKU</th>
              <th class="px-3 py-2 text-right">Stock</th>
              <th class="px-3 py-2 text-right">Price override</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            @for (v of variants(); track v.id) {
              <tr [class.bg-blue-50]="editingId() === v.id">
                <td class="px-3 py-2">{{ v.size || '—' }}</td>
                <td class="px-3 py-2 font-mono text-xs text-gray-600">{{ v.sku || '—' }}</td>
                <td class="px-3 py-2 text-right">{{ v.stockQuantity ?? 0 }}</td>
                <td class="px-3 py-2 text-right">{{ v.priceOverride != null ? '৳ ' + v.priceOverride : '—' }}</td>
                <td class="px-3 py-2">
                  <div class="flex items-center justify-end gap-2">
                    <button (click)="startEdit(v)" class="act-btn act-btn-edit" title="Edit">
                      <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                      Edit
                    </button>
                    <button (click)="remove(v)" class="act-btn act-btn-delete" title="Delete">
                      <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                      Delete
                    </button>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      }

      <!-- Inline edit / create form -->
      @if (creating() || editingId() !== null) {
        <div class="mt-4 rounded-lg border border-blue-100 bg-blue-50/60 p-4">
          <h3 class="mb-3 text-sm font-semibold">
            {{ editingId() !== null ? 'Edit variant' : 'New variant' }}
          </h3>
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
            <label class="text-xs font-medium text-gray-600">
              Size
              <select [(ngModel)]="form.size"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm">
                <option value="">Select size…</option>
                @for (s of sizeOptions; track s) {
                  <option [value]="s">{{ s }}</option>
                }
              </select>
            </label>
            <label class="text-xs font-medium text-gray-600">
              SKU <span class="font-normal text-gray-400">(auto-generated if blank)</span>
              <input [(ngModel)]="form.sku" placeholder="Auto from product SKU + size"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm font-mono" />
            </label>
            <label class="text-xs font-medium text-gray-600">
              Stock
              <input type="number" min="0" [(ngModel)]="form.stockQuantity"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
            </label>
            <label class="text-xs font-medium text-gray-600">
              Price override (optional)
              <input type="number" step="0.01" min="0" [(ngModel)]="form.priceOverride"
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
              {{ saving() ? 'Saving…' : 'Save variant' }}
            </button>
          </div>
        </div>
      }
    </section>
  `,
})
export class VariantManagerComponent implements OnChanges {
  readonly productId = input.required<number>();

  private readonly api = inject(VariantService);

  readonly variants  = signal<VariantResponse[]>([]);
  readonly loading   = signal(false);
  readonly saving    = signal(false);
  readonly creating  = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly error     = signal<string>('');

  protected readonly sizeOptions = SIZE_OPTIONS;
  protected form: VariantForm = { ...EMPTY_FORM };

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['productId']) this.reload();
  }

  reload(): void {
    const id = this.productId();
    if (!id) return;
    this.loading.set(true);
    this.api.list(id).pipe(catchError(() => of([] as VariantResponse[]))).subscribe((list) => {
      this.variants.set(list);
      this.loading.set(false);
    });
  }

  startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.error.set('');
    this.form = { ...EMPTY_FORM };
  }

  startEdit(v: VariantResponse): void {
    this.creating.set(false);
    this.editingId.set(v.id);
    this.error.set('');
    this.form = {
      size: v.size ?? '',
      sku: v.sku ?? '',
      stockQuantity: v.stockQuantity ?? 0,
      priceOverride: v.priceOverride,
    };
  }

  cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM };
  }

  save(): void {
    this.saving.set(true);
    this.error.set('');
    const payload: VariantRequest = {
      size: this.form.size.trim() || null,
      sku: this.form.sku.trim() || null,
      stockQuantity: this.form.stockQuantity ?? 0,
      priceOverride: this.form.priceOverride,
    };

    const productId = this.productId();
    const editId = this.editingId();
    const obs$ = editId != null
      ? this.api.update(productId, editId, payload)
      : this.api.create(productId, payload);

    obs$.pipe(catchError((err) => {
      this.error.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((saved) => {
      this.saving.set(false);
      if (!saved) return;
      if (editId != null) {
        this.variants.update((list) => list.map((v) => v.id === editId ? saved : v));
      } else {
        this.variants.update((list) => [...list, saved]);
      }
      this.cancelEdit();
    });
  }

  remove(v: VariantResponse): void {
    if (!confirm(`Delete variant "${v.name || v.size || v.id}"?`)) return;
    this.api.delete(this.productId(), v.id)
      .pipe(catchError((err) => {
        this.error.set(parseApiError(err));
        return of(null);
      }))
      .subscribe(() => {
        this.variants.update((list) => list.filter((x) => x.id !== v.id));
      });
  }
}
