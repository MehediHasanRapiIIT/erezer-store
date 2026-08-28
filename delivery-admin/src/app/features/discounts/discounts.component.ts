import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  DiscountRequest,
  DiscountResponse,
  DiscountScope,
  DiscountService,
  DiscountType,
} from '../../core/services/discount.service';
import { CategoryService } from '../../core/services/category.service';
import { ProductService } from '../../core/services/product.service';
import { CategoryResponse, ProductResponse } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

interface DiscountForm {
  name: string;
  scope: DiscountScope;
  discountType: DiscountType;
  discountValue: number | null;
  targetId: number | null;
  stackable: boolean;
  priority: number;
  validFrom: string;
  validTo: string;
  description: string;
  isActive: boolean;
}

const EMPTY_FORM: DiscountForm = {
  name: '',
  scope: 'GLOBAL',
  discountType: 'PERCENT',
  discountValue: 10,
  targetId: null,
  stackable: false,
  priority: 0,
  validFrom: '',
  validTo: '',
  description: '',
  isActive: true,
};

@Component({
  selector: 'app-discounts',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Discounts</h1>
            <span class="text-xs text-gray-400">{{ discounts().length }} total</span>
          </div>
          <button (click)="startCreate()"
            class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
            + New discount
          </button>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            <p class="rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700">
              Discounts apply automatically at checkout. <strong>Stackable</strong> discounts combine with
              other stackable ones; a non-stackable discount applies on its own. When discounts overlap and
              don't stack, the one with the <strong>highest priority</strong> wins.
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
                    <th class="px-4 py-2.5 text-left">Scope</th>
                    <th class="px-4 py-2.5 text-left">Target</th>
                    <th class="px-4 py-2.5 text-right">Value</th>
                    <th class="px-4 py-2.5 text-center">Stackable</th>
                    <th class="px-4 py-2.5 text-right">Priority</th>
                    <th class="px-4 py-2.5 text-left">Window</th>
                    <th class="px-4 py-2.5 text-center">Active</th>
                    <th class="px-4 py-2.5"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  @if (loading()) {
                    <tr><td colspan="9" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                  }
                  @for (d of discounts(); track d.id) {
                    <tr [class.bg-blue-50]="editingId() === d.id">
                      <td class="px-4 py-2.5 font-medium">{{ d.name }}</td>
                      <td class="px-4 py-2.5">{{ scopeLabel(d.scope) }}</td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">{{ targetLabel(d) }}</td>
                      <td class="px-4 py-2.5 text-right">
                        @if (d.discountType === 'PERCENT') { {{ d.discountValue ?? 0 }}% }
                        @else { ৳ {{ d.discountValue ?? 0 }} }
                      </td>
                      <td class="px-4 py-2.5 text-center">{{ d.stackable ? 'Yes' : 'No' }}</td>
                      <td class="px-4 py-2.5 text-right">{{ d.priority }}</td>
                      <td class="px-4 py-2.5 text-xs text-gray-500">
                        @if (d.validFrom || d.validTo) {
                          {{ shortDate(d.validFrom) || '—' }} → {{ shortDate(d.validTo) || '—' }}
                        } @else { — }
                      </td>
                      <td class="px-4 py-2.5 text-center">
                        <span class="inline-block h-2.5 w-2.5 rounded-full"
                          [class.bg-emerald-500]="d.isActive"
                          [class.bg-gray-300]="!d.isActive"></span>
                      </td>
                      <td class="px-4 py-2.5">
                        <div class="flex items-center justify-end gap-2">
                          <button (click)="startEdit(d)" class="act-btn act-btn-edit" title="Edit">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                            Edit
                          </button>
                          <button (click)="remove(d)" class="act-btn act-btn-delete" title="Delete">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                            Delete
                          </button>
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="9" class="px-4 py-6 text-center text-gray-400">No discounts yet.</td></tr>
                    }
                  }
                </tbody>
              </table>
            </section>

            <!-- Form -->
            @if (creating() || editingId() !== null) {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold">
                  {{ editingId() ? 'Edit discount' : 'New discount' }}
                </h2>
                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <label class="text-xs font-medium text-gray-600 sm:col-span-2">
                    Name
                    <input [(ngModel)]="form.name" placeholder="Summer sale"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Scope
                    <select [(ngModel)]="form.scope" (ngModelChange)="onScopeChange()"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                      <option value="GLOBAL">All products (global)</option>
                      <option value="CATEGORY">Category</option>
                      <option value="PRODUCT">Product</option>
                    </select>
                  </label>

                  @if (form.scope === 'CATEGORY') {
                    <label class="text-xs font-medium text-gray-600">
                      Category
                      <select [(ngModel)]="form.targetId"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                        <option [ngValue]="null">Select category…</option>
                        @for (c of categories(); track c.id) {
                          <option [ngValue]="c.id">{{ c.name }}</option>
                        }
                      </select>
                    </label>
                  } @else if (form.scope === 'PRODUCT') {
                    <label class="text-xs font-medium text-gray-600">
                      Product
                      <select [(ngModel)]="form.targetId"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm bg-white">
                        <option [ngValue]="null">Select product…</option>
                        @for (p of products(); track p.id) {
                          <option [ngValue]="p.id">{{ p.name }}</option>
                        }
                      </select>
                    </label>
                  }

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
                  <label class="text-xs font-medium text-gray-600">
                    Priority <span class="font-normal text-gray-400">(higher wins)</span>
                    <input type="number" step="1" [(ngModel)]="form.priority"
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
                    <input type="checkbox" [(ngModel)]="form.stackable" />
                    Stackable (combines with other stackable discounts)
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
                    {{ saving() ? 'Saving…' : 'Save discount' }}
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
export class DiscountsComponent implements OnInit {
  private readonly api = inject(DiscountService);
  private readonly categoryApi = inject(CategoryService);
  private readonly productApi = inject(ProductService);

  readonly discounts  = signal<DiscountResponse[]>([]);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly products   = signal<ProductResponse[]>([]);
  readonly loading    = signal(false);
  readonly saving     = signal(false);
  readonly creating   = signal(false);
  readonly editingId  = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: DiscountForm = { ...EMPTY_FORM };

  ngOnInit(): void {
    this.reload();
    this.categoryApi.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => this.categories.set(list));
    this.productApi.getProducts()
      .pipe(catchError(() => of([] as ProductResponse[])))
      .subscribe((list) => this.products.set(list));
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().pipe(catchError(() => of([] as DiscountResponse[]))).subscribe((list) => {
      this.discounts.set(list);
      this.loading.set(false);
    });
  }

  protected onScopeChange(): void {
    // Target only applies to PRODUCT/CATEGORY scopes.
    if (this.form.scope === 'GLOBAL') this.form.targetId = null;
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.form = { ...EMPTY_FORM };
  }

  protected startEdit(d: DiscountResponse): void {
    this.creating.set(false);
    this.editingId.set(d.id);
    this.errorMessage.set('');
    this.form = {
      name: d.name,
      scope: d.scope,
      discountType: d.discountType,
      discountValue: d.discountValue,
      targetId: d.targetId,
      stackable: d.stackable,
      priority: d.priority ?? 0,
      validFrom: d.validFrom ?? '',
      validTo:   d.validTo ?? '',
      description: d.description ?? '',
      isActive: d.isActive,
    };
  }

  protected cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM };
  }

  protected save(): void {
    if (!this.form.name.trim()) {
      this.errorMessage.set('Name is required.');
      return;
    }
    if (this.form.scope !== 'GLOBAL' && this.form.targetId == null) {
      this.errorMessage.set(`Select a ${this.form.scope === 'PRODUCT' ? 'product' : 'category'}.`);
      return;
    }
    this.saving.set(true);
    this.errorMessage.set('');
    const payload: DiscountRequest = {
      name: this.form.name.trim(),
      scope: this.form.scope,
      discountType: this.form.discountType,
      discountValue: this.form.discountValue,
      targetId: this.form.scope === 'GLOBAL' ? null : this.form.targetId,
      stackable: this.form.stackable,
      priority: this.form.priority ?? 0,
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
      if (editId) {
        this.discounts.update((list) => list.map((d) => d.id === editId ? saved : d));
      } else {
        this.discounts.update((list) => [...list, saved]);
      }
      this.cancelEdit();
    });
  }

  protected remove(d: DiscountResponse): void {
    if (!confirm(`Delete discount "${d.name}"?`)) return;
    this.api.delete(d.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return of(null); }))
      .subscribe(() => {
        this.discounts.update((list) => list.filter((x) => x.id !== d.id));
      });
  }

  protected scopeLabel(s: DiscountScope): string {
    return s === 'GLOBAL' ? 'Global' : s === 'CATEGORY' ? 'Category' : 'Product';
  }

  protected targetLabel(d: DiscountResponse): string {
    if (d.scope === 'GLOBAL') return 'All products';
    if (d.scope === 'CATEGORY') {
      return this.categories().find((c) => c.id === d.targetId)?.name ?? `Category #${d.targetId}`;
    }
    return this.products().find((p) => p.id === d.targetId)?.name ?? `Product #${d.targetId}`;
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
}
