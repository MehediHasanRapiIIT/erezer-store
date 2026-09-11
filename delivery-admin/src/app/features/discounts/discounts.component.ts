import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, of, startWith, switchMap } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  DiscountRequest,
  DiscountResponse,
  DiscountScope,
  DiscountService,
  DiscountSwitches,
  DiscountType,
} from '../../core/services/discount.service';
import { CategoryService } from '../../core/services/category.service';
import { ProductService } from '../../core/services/product.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { CategoryResponse, ProductResponse } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

/** The three per-scope switches, keyed by their field on DiscountSwitches. */
type ScopeSwitch = 'discountsGlobalEnabled' | 'discountsCategoryEnabled' | 'discountsProductEnabled';

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
          @if (perms.can('discounts.create')) {
            <button (click)="startCreate()"
              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
              + New discount
            </button>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            <!-- On/off switches. Suspends rules without deleting them. -->
            <section class="rounded-xl border bg-white overflow-hidden"
              [class.border-red-300]="!masterOn()" [class.border-gray-200]="masterOn()">
              <div class="flex items-center justify-between gap-4 px-5 py-4"
                [class.bg-red-50]="!masterOn()">
                <div>
                  <h2 class="font-bold text-gray-900">
                    Automatic discounts are
                    <span [class.text-emerald-600]="masterOn()" [class.text-red-600]="!masterOn()">
                      {{ masterOn() ? 'ON' : 'OFF' }}
                    </span>
                  </h2>
                  <p class="text-xs text-gray-500 mt-0.5">
                    @if (masterOn()) {
                      Rules below apply at checkout and on the storefront.
                    } @else {
                      Every rule below is suspended. Nothing is deleted, and the storefront shows full prices.
                    }
                    Sale prices, coupons, flash sales and bundles are separate and keep working.
                  </p>
                  @if (!perms.can('discounts.switches')) {
                    <p class="text-xs text-gray-400 mt-1">Needs the “Turn discounts on or off” permission.</p>
                  }
                </div>
                <button type="button" (click)="toggleMaster()" [disabled]="savingSettings() || !perms.can('discounts.switches')"
                  class="relative inline-flex h-7 w-13 flex-shrink-0 rounded-full transition-colors disabled:opacity-50"
                  [class.bg-emerald-500]="masterOn()" [class.bg-gray-300]="!masterOn()"
                  [attr.aria-label]="masterOn() ? 'Switch all discounts off' : 'Switch all discounts on'"
                  style="width:3.25rem">
                  <span class="absolute top-1 h-5 w-5 rounded-full bg-white shadow transition-all"
                    [style.left]="masterOn() ? '1.75rem' : '0.25rem'"></span>
                </button>
              </div>

              <div class="grid gap-px bg-gray-100 sm:grid-cols-3 border-t border-gray-200"
                [class.opacity-50]="!masterOn()" [class.pointer-events-none]="!masterOn()">
                @for (sw of scopeSwitches; track sw.key) {
                  <label class="flex items-start gap-3 bg-white px-5 py-3"
                    [class.cursor-pointer]="perms.can('discounts.switches')">
                    <input type="checkbox" class="mt-0.5 h-4 w-4 rounded border-gray-300"
                      [checked]="scopeOn(sw.key)" (change)="toggleScope(sw.key)"
                      [disabled]="savingSettings() || !perms.can('discounts.switches')" />
                    <span>
                      <span class="block text-sm font-medium text-gray-800">{{ sw.label }}</span>
                      <span class="block text-xs text-gray-500">{{ sw.hint }}</span>
                    </span>
                  </label>
                }
              </div>
            </section>

            <p class="rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700">
              Discounts apply automatically at checkout. <strong>Stackable</strong> discounts combine with
              other stackable ones; a non-stackable discount applies on its own. When discounts overlap and
              don't stack, the one with the <strong>highest priority</strong> wins.
              To keep one product or a whole collection at full price, use
              <strong>Never discount</strong> on that product or category.
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
                          @if (perms.can('discounts.edit')) {
                          <button (click)="startEdit(d)" class="act-btn act-btn-edit" title="Edit">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M16.862 4.487l1.687-1.688a1.875 1.875 0 112.652 2.652L10.582 16.07a4.5 4.5 0 01-1.897 1.13L6 18l.8-2.685a4.5 4.5 0 011.13-1.897l8.932-8.931z"/></svg>
                            Edit
                          </button>
                          }
                          @if (perms.can('discounts.delete')) {
                          <button (click)="remove(d)" class="act-btn act-btn-delete" title="Delete">
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                            Delete
                          </button>
                          }
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
                    <!-- Type to search: the server finds the product, 8 at a time. -->
                    <div class="text-xs font-medium text-gray-600 sm:col-span-2">
                      Product
                      @if (form.targetId != null) {
                        <div class="mt-1 flex items-center justify-between gap-2 rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-sm">
                          <span class="truncate text-gray-800">{{ productName(form.targetId) }}</span>
                          <button type="button" (click)="form.targetId = null" class="text-xs text-blue-600 underline">Change</button>
                        </div>
                      } @else {
                        <input type="search" [ngModel]="productQuery()" (ngModelChange)="onProductQuery($event)"
                          [ngModelOptions]="{ standalone: true }" placeholder="Type to search products…"
                          aria-label="Search products"
                          class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                        <div class="mt-1 max-h-56 overflow-y-auto rounded-lg border border-gray-200 divide-y divide-gray-50">
                          @for (p of productResults(); track p.id) {
                            <button type="button" (click)="pickProduct(p)"
                              class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-normal text-gray-800 hover:bg-gray-50">
                              <span class="flex-1 truncate">
                                {{ p.name }}
                                @if (p.productCode) { <span class="ml-1 font-mono text-xs text-gray-400">{{ p.productCode }}</span> }
                              </span>
                              <span class="text-xs text-gray-400">৳{{ p.price }}</span>
                            </button>
                          } @empty {
                            <p class="px-3 py-3 text-center text-xs font-normal text-gray-400">
                              {{ searchingProducts() ? 'Searching…' : 'No products match.' }}
                            </p>
                          }
                        </div>
                      }
                    </div>
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
  private readonly destroyRef = inject(DestroyRef);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly discounts  = signal<DiscountResponse[]>([]);
  readonly categories = signal<CategoryResponse[]>([]);
  readonly loading    = signal(false);

  // Product search for product-scoped discounts (server search, 8 at a time)
  readonly productQuery = signal('');
  readonly productResults = signal<ProductResponse[]>([]);
  readonly searchingProducts = signal(false);
  private readonly productQuery$ = new Subject<string>();
  /** Names of the products discounts point at, looked up by id instead of loading every product. */
  private readonly productNames = signal<ReadonlyMap<number, string>>(new Map());
  readonly saving     = signal(false);
  readonly creating   = signal(false);
  readonly editingId  = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: DiscountForm = { ...EMPTY_FORM };

  // ── on/off switches ───────────────────────────────────────────────────────

  readonly settings = signal<DiscountSwitches | null>(null);
  readonly savingSettings = signal(false);

  protected readonly scopeSwitches: { key: ScopeSwitch; label: string; hint: string }[] = [
    { key: 'discountsGlobalEnabled',   label: 'Store-wide rules',  hint: 'Rules that apply to every product.' },
    { key: 'discountsCategoryEnabled', label: 'Category rules',    hint: 'Rules targeting one category.' },
    { key: 'discountsProductEnabled',  label: 'Product rules',     hint: 'Rules targeting one product.' },
  ];

  /** Null means on, so a settings row saved before this feature keeps discounting. */
  private isOn(flag: boolean | null | undefined): boolean {
    return flag !== false;
  }

  protected masterOn(): boolean {
    return this.isOn(this.settings()?.discountsEnabled);
  }

  protected scopeOn(key: ScopeSwitch): boolean {
    return this.isOn(this.settings()?.[key]);
  }

  protected toggleMaster(): void {
    this.saveSettings({ discountsEnabled: !this.masterOn() });
  }

  protected toggleScope(key: ScopeSwitch): void {
    this.saveSettings({ [key]: !this.scopeOn(key) } as Partial<DiscountSwitches>);
  }

  /**
   * Sends only the switch being flipped; the others stay as they are on the
   * server. The panel is only interactive once the switches have loaded.
   */
  private saveSettings(change: Partial<DiscountSwitches>): void {
    const current = this.settings();
    if (!current || this.savingSettings() || !this.perms.can('discounts.switches')) return;
    const next = { ...current, ...change };
    this.savingSettings.set(true);
    // Optimistic: the switch flips immediately, and reverts if the save fails.
    this.settings.set(next);
    this.api.updateSwitches(change).subscribe({
      next: (saved) => {
        this.settings.set(saved);
        this.savingSettings.set(false);
        this.notices.success('Discount settings saved');
      },
      error: (err) => {
        this.settings.set(current);
        this.savingSettings.set(false);
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  ngOnInit(): void {
    this.reload();
    this.api.getSwitches()
      .pipe(catchError(() => of(null)))
      .subscribe((s) => this.settings.set(s));
    this.categoryApi.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => this.categories.set(list));
    this.productQuery$.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) => {
        this.searchingProducts.set(true);
        return this.productApi.browse(q.trim(), 0, 8).pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((page) => {
      this.searchingProducts.set(false);
      this.productResults.set(page?.content ?? []);
    });
  }

  reload(): void {
    this.loading.set(true);
    this.api.list().pipe(catchError(() => of([] as DiscountResponse[]))).subscribe((list) => {
      this.discounts.set(list);
      this.loading.set(false);
      this.lookUpProductNames(list);
    });
  }

  protected onProductQuery(q: string): void {
    this.productQuery.set(q);
    this.productQuery$.next(q);
  }

  protected pickProduct(p: ProductResponse): void {
    this.rememberName(p.id, DiscountsComponent.label(p));
    this.form.targetId = p.id;
  }

  /** "Classic Hoodie · EZ-HD-101": the name with the product code. */
  private static label(p: ProductResponse): string {
    return p.productCode ? `${p.name} · ${p.productCode}` : p.name;
  }

  protected productName(id: number): string {
    return this.productNames().get(id) ?? `Product #${id}`;
  }

  private rememberName(id: number, name: string): void {
    this.productNames.update((names) => new Map(names).set(id, name));
  }

  /** Fetches the name of each product a discount targets that isn't known yet. */
  private lookUpProductNames(list: DiscountResponse[]): void {
    const known = this.productNames();
    const ids = [...new Set(list.filter((d) => d.scope === 'PRODUCT' && d.targetId != null).map((d) => d.targetId!))]
      .filter((id) => !known.has(id));
    for (const id of ids) {
      this.productApi.getProduct(id).pipe(catchError(() => of(null)))
        .subscribe((p) => { if (p) this.rememberName(p.id, DiscountsComponent.label(p)); });
    }
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
      this.notices.success(editId ? 'Discount saved' : 'Discount added', saved.name);
      this.lookUpProductNames([saved]);
      this.cancelEdit();
    });
  }

  protected async remove(d: DiscountResponse): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete discount "${d.name}"?`,
      message: 'Prices go back to normal for whatever it covered.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(d.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return EMPTY; }))
      .subscribe(() => {
        this.discounts.update((list) => list.filter((x) => x.id !== d.id));
        this.notices.success('Discount deleted', d.name);
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
    return d.targetId != null ? this.productName(d.targetId) : 'Product';
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
