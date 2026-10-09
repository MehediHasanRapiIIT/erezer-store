import { Component, computed, inject, input, OnChanges, output, signal, SimpleChanges } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { catchError, concat, of, toArray } from 'rxjs';
import {
  ProductOption,
  VariantRequest,
  VariantResponse,
  VariantService,
} from '../../../core/services/variant.service';
import { parseApiError } from '../../../core/utils/api-error.util';
import { PermissionService } from '../../../core/services/permission.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { NoticeService } from '../../../core/services/notice.service';
import {
  SIZE_OPTIONS, SizeGridComponent, SizeRow, emptySizeRows, pickedSizes,
} from '../shared/size-grid.component';
import { FITS, Fit, fitLabel } from '../shared/fit-sizes.component';
import { OptionsEditorComponent, cleanOptions, combinationCount, optionsProblem } from '../shared/options-editor.component';

/** One fit in the "Fit" box: offered or not, its price, and whether the price was touched. */
interface FitDraft {
  picked: boolean;
  price: number | null;
  priceTouched: boolean;
}

interface VariantForm {
  /** For a product that comes in fits: which fit a new size belongs to. */
  fit: string;
  size: string;
  sku: string;
  stockQuantity: number | null;
  priceOverride: number | null;
}

const EMPTY_FORM: VariantForm = {
  fit: '', size: '', sku: '', stockQuantity: 0, priceOverride: null,
};


@Component({
  selector: 'app-variant-manager',
  standalone: true,
  imports: [FormsModule, SizeGridComponent, OptionsEditorComponent],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5">
      <header class="mb-3 flex items-center justify-between">
        <div>
          <h2 class="font-bold text-gray-900">Variants</h2>
          <p class="text-xs text-gray-500">What the customer chooses before buying: options such as colour, the fit, and the size. Each variant has its own stock, price and SKU.</p>
        </div>
        @if (perms.can('products.variants')) {
          <div class="flex items-center gap-2">
            <button
              type="button"
              (click)="startSeveral()"
              [disabled]="freeSizes().length === 0"
              [title]="freeSizes().length === 0 ? 'This product has every size already' : ''"
              class="px-3 py-1.5 text-xs font-semibold text-blue-700 border border-blue-200 bg-blue-50 hover:bg-blue-100 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed">
              + Add several sizes
            </button>
            <button
              type="button"
              (click)="startCreate()"
              class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg">
              + Add one size
            </button>
          </div>
        }
      </header>

      @if (error()) {
        <p class="mb-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
      }

      <!-- Options: colour, and anything else the shop defines -->
      @if (canList() && !loading() && perms.can('products.variants')) {
        <fieldset class="mb-4 rounded-lg border border-gray-200 p-3" data-testid="options-box">
          <legend class="px-1 text-sm font-semibold text-gray-800">Options</legend>
          <p class="mb-2 text-xs text-gray-500">
            Colour, or anything else this product comes in: Sleeve, Material, Pack… Customers pick one of each. Sizes and fits are set below.
          </p>
          <app-options-editor [(options)]="optionsDraft" [disabled]="saving()" />
          @if (optionsChanged()) {
            <p class="mt-2 text-xs text-gray-500" data-testid="options-effect">{{ optionsEffect() }}</p>
          }
          <div class="mt-3 flex flex-wrap items-center justify-end gap-2">
            @if (missingCombinations() > 0 && !optionsChanged()) {
              <button type="button" (click)="restoreMissing()" [disabled]="saving()" data-testid="options-restore"
                class="mr-auto text-xs font-medium text-blue-600 hover:underline">
                Put back {{ missingCombinations() }} deleted {{ missingCombinations() === 1 ? 'variant' : 'variants' }}
              </button>
            }
            @if (optionsChanged()) {
              <button type="button" (click)="resetOptionsDraft()" [disabled]="saving()"
                class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">Undo</button>
            }
            <button type="button" (click)="saveOptions()" [disabled]="saving() || !optionsChanged()" data-testid="save-options"
              class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed">
              {{ saving() ? 'Saving…' : 'Save options' }}
            </button>
          </div>
        </fieldset>
      }

      <!-- Fit: Drop Shoulder, Regular Fit, both or neither -->
      @if (canList() && !loading() && perms.can('products.variants')) {
        <fieldset class="mb-4 rounded-lg border border-gray-200 p-3" data-testid="fit-box">
          <legend class="px-1 text-sm font-semibold text-gray-800">Fit</legend>
          <p class="mb-2 text-xs text-gray-500">
            Tick the fits this product comes in: one, both, or neither. Customers pick the fit, then the size. Each fit has its own stock.
          </p>
          <div class="grid grid-cols-1 gap-2 sm:grid-cols-2">
            @for (f of fits; track f.value) {
              <div class="rounded-lg border p-3"
                [class.border-blue-500]="fitDraft()[f.value].picked" [class.bg-blue-50]="fitDraft()[f.value].picked"
                [class.border-gray-200]="!fitDraft()[f.value].picked">
                <label class="flex cursor-pointer items-center gap-2 text-sm font-semibold text-gray-800">
                  <input type="checkbox" [attr.data-testid]="'fit-' + f.value"
                    [checked]="fitDraft()[f.value].picked" [disabled]="saving() || !hasSizes()"
                    (change)="toggleFit(f.value)"
                    class="h-4 w-4 cursor-pointer rounded border-gray-300 text-blue-600" />
                  {{ f.label }}
                </label>
                @if (fitDraft()[f.value].picked) {
                  <label class="mt-2 block text-xs font-medium text-gray-600">
                    {{ f.label }} price <span class="font-normal text-gray-400">(optional)</span>
                    <span class="relative mt-1 block">
                      <span class="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-xs text-gray-400">৳</span>
                      <input type="number" min="0" step="any" [attr.data-testid]="'fit-price-' + f.value"
                        placeholder="same as product price"
                        [ngModel]="fitDraft()[f.value].price" (ngModelChange)="setFitPrice(f.value, $event)"
                        [disabled]="saving() || !perms.can('products.price')"
                        class="w-full rounded-lg border border-gray-200 bg-white py-1.5 pl-6 pr-2 text-sm font-normal outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400" />
                    </span>
                    @if (mixedPrices(f.value)) {
                      <span class="mt-1 block font-normal text-gray-400">Its sizes have different prices now. Typing a price here sets it on all of them.</span>
                    }
                  </label>
                }
              </div>
            }
          </div>
          @if (!hasSizes()) {
            <p class="mt-2 text-xs text-gray-500">Add the product's sizes first: a fit's stock is kept size by size.</p>
          } @else {
            <p class="mt-2 text-xs text-gray-400">
              Leave a fit's price empty to use the product's price. The product's sale discount comes off a fit's own price too: the same percentage, or the same amount.
            </p>
          }
          <div class="mt-3 flex items-center justify-end gap-2">
            @if (fitsChanged()) {
              <button type="button" (click)="resetFitDraft()" [disabled]="saving()"
                class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">Undo</button>
            }
            <button type="button" (click)="saveFits()" [disabled]="saving() || !fitsChanged()" data-testid="save-fits"
              class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-40 disabled:cursor-not-allowed">
              {{ saving() ? 'Saving…' : 'Save fits' }}
            </button>
          </div>
        </fieldset>
      }

      @if (!canList()) {
        <p class="rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-500">
          Showing variants needs the “See products”, “Manage sizes and colours” or “See stock” permission.
        </p>
      } @else if (loading()) {
        <p class="text-sm text-gray-400">Loading variants…</p>
      } @else if (variants().length === 0 && !editingId() && !creating() && !addingSeveral()) {
        <p class="rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-500">
          No variants yet. Add one to expose size options on the storefront.
        </p>
      }

      <!-- The same stock or price for many variants at once -->
      @if (variants().length > 1 && perms.can('products.variants')) {
        <div class="mb-3 flex flex-wrap items-end gap-2 rounded-lg bg-gray-50 px-3 py-2" data-testid="fill-all">
          <p class="mr-1 pb-1.5 text-xs font-semibold text-gray-700">Set for many:</p>
          @if (fillChoices().length > 0) {
            <label class="text-[11px] font-medium text-gray-500">
              Which variants
              <select [ngModel]="fillFilter()" (ngModelChange)="fillFilter.set($event)" data-testid="fill-filter"
                class="mt-0.5 block rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-xs text-gray-700">
                <option value="">All {{ variants().length }} variants</option>
                @for (c of fillChoices(); track c.id) { <option [value]="c.id">{{ c.label }}</option> }
              </select>
            </label>
          }
          <label class="text-[11px] font-medium text-gray-500">
            Stock
            <input type="number" min="0" [ngModel]="fillStock()" (ngModelChange)="fillStock.set(numberOrNull($event))" placeholder="leave as is"
              [disabled]="!perms.can('inventory.edit')" data-testid="fill-stock"
              class="mt-0.5 block w-28 rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-xs disabled:bg-gray-100" />
          </label>
          <label class="text-[11px] font-medium text-gray-500">
            Own price (৳)
            <input type="number" min="0" step="any" [ngModel]="fillPrice()" (ngModelChange)="fillPrice.set(numberOrNull($event))" placeholder="leave as is"
              [disabled]="!perms.can('products.price')" data-testid="fill-price"
              class="mt-0.5 block w-28 rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-xs disabled:bg-gray-100" />
          </label>
          <button type="button" (click)="fillMany()" [disabled]="saving() || (fillStock() == null && fillPrice() == null)" data-testid="fill-apply"
            class="rounded-lg bg-blue-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-blue-700 disabled:opacity-40">
            Apply to {{ fillTargets().length }}
          </button>
        </div>
      }

      <!-- Variant list -->
      @if (variants().length > 0) {
        <table class="w-full text-sm" data-testid="variants-table">
          <thead>
            <tr class="border-b border-gray-100 bg-gray-50 text-xs uppercase text-gray-400">
              @if (hasOptions()) { <th class="px-3 py-2 text-left">{{ optionsHeading() }}</th> }
              @if (hasFits()) { <th class="px-3 py-2 text-left">Fit</th> }
              <th class="px-3 py-2 text-left">Size</th>
              <th class="px-3 py-2 text-left">SKU</th>
              <th class="px-3 py-2 text-right">Stock</th>
              <th class="px-3 py-2 text-right">Price override</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody class="divide-y divide-gray-50">
            @for (v of sortedVariants(); track v.id) {
              <tr [class.bg-blue-50]="editingId() === v.id" data-testid="variant-row">
                @if (hasOptions()) {
                  <td class="px-3 py-2 font-medium text-gray-800" data-testid="variant-options">
                    <span class="inline-flex flex-wrap items-center gap-1.5">
                      @for (o of v.options ?? []; track o.optionId) {
                        <span class="inline-flex items-center gap-1">
                          @if (o.hex) { <span class="inline-block h-3 w-3 rounded-full border border-gray-300" [style.background]="o.hex"></span> }
                          {{ o.value }}
                        </span>
                      } @empty { — }
                    </span>
                  </td>
                }
                @if (hasFits()) { <td class="px-3 py-2 font-medium text-gray-800">{{ v.fitLabel || '—' }}</td> }
                <td class="px-3 py-2">{{ v.size || '—' }}</td>
                <td class="px-3 py-2 font-mono text-xs text-gray-600">{{ v.sku || '—' }}</td>
                <td class="px-3 py-2 text-right">{{ v.stockQuantity ?? 0 }}</td>
                <td class="px-3 py-2 text-right">{{ v.priceOverride != null ? '৳ ' + v.priceOverride : '—' }}</td>
                <td class="px-3 py-2">
                  @if (perms.can('products.variants')) {
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
                  }
                </td>
              </tr>
            }
          </tbody>
        </table>
      }

      <!-- Several sizes at once -->
      @if (addingSeveral() && perms.can('products.variants')) {
        <div class="mt-4 rounded-lg border border-blue-100 bg-blue-50/60 p-4" data-testid="several-sizes">
          <h3 class="mb-1 text-sm font-semibold">Add several sizes</h3>
          <p class="mb-3 text-xs text-gray-500">Tick the sizes to add and type the stock for each. Sizes this product already has can't be ticked again.</p>
          @if (hasFits()) {
            <label class="mb-3 block text-xs font-medium text-gray-600">
              Add them to
              <select [ngModel]="severalFit()" (ngModelChange)="severalFit.set($event); severalRows.set(blankRows())" data-testid="several-fit"
                class="ml-1 rounded-lg border border-gray-200 bg-white px-2 py-1.5 text-sm">
                @for (f of currentFits(); track f) { <option [value]="f">{{ labelOf(f) }}</option> }
              </select>
            </label>
          }
          <app-size-grid [(rows)]="severalRows" [taken]="takenSizes()" [disabled]="saving()"
            [canSetStock]="perms.can('inventory.edit')" [canSetPrice]="perms.can('products.price')" />
          <div class="mt-4 flex justify-end gap-2">
            <button type="button" (click)="cancelSeveral()"
              class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
              Cancel
            </button>
            <button type="button" (click)="saveSeveral()" [disabled]="saving() || severalCount() === 0"
              class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
              {{ saving() ? 'Saving…' : severalCount() > 0 ? 'Add ' + severalCount() + (severalCount() === 1 ? ' size' : ' sizes') : 'Add sizes' }}
            </button>
          </div>
        </div>
      }

      <!-- Inline edit / create form -->
      @if ((creating() || editingId() !== null) && perms.can('products.variants')) {
        <div class="mt-4 rounded-lg border border-blue-100 bg-blue-50/60 p-4">
          <h3 class="mb-3 text-sm font-semibold">
            {{ editingId() !== null ? 'Edit variant' : 'New variant' }}
          </h3>
          <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
            @if (hasFits()) {
              <label class="text-xs font-medium text-gray-600">
                Fit
                <select [(ngModel)]="form.fit" [disabled]="editingId() !== null" data-testid="variant-fit"
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm disabled:bg-gray-50 disabled:text-gray-500">
                  @for (f of currentFits(); track f) { <option [value]="f">{{ labelOf(f) }}</option> }
                </select>
              </label>
            }
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
                [disabled]="!perms.can('inventory.edit')"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm disabled:bg-gray-50 disabled:text-gray-500" />
              @if (!perms.can('inventory.edit')) {
                <span class="mt-1 block text-xs font-normal text-gray-400">Needs the “Change stock” permission.</span>
              }
            </label>
            <label class="text-xs font-medium text-gray-600">
              Price override (optional)
              <input type="number" step="0.01" min="0" [(ngModel)]="form.priceOverride"
                [disabled]="!perms.can('products.price')"
                class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm disabled:bg-gray-50 disabled:text-gray-500" />
              @if (!perms.can('products.price')) {
                <span class="mt-1 block text-xs font-normal text-gray-400">Needs the “Change prices” permission.</span>
              }
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
  /** The product's options were saved: anything else on the page that shows them should look again. */
  readonly optionsSaved = output<void>();

  private readonly api = inject(VariantService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly variants  = signal<VariantResponse[]>([]);
  readonly loading   = signal(false);
  readonly saving    = signal(false);
  readonly creating  = signal(false);
  readonly editingId = signal<number | null>(null);
  readonly error     = signal<string>('');

  protected readonly sizeOptions = SIZE_OPTIONS;
  protected readonly fits = FITS;

  // ── options: colour, and anything else the shop defines ───────────────────
  /** The options as saved. */
  readonly options = signal<ProductOption[]>([]);
  /** The "Options" box as it is being edited; saved with "Save options". */
  readonly optionsDraft = signal<ProductOption[]>([]);
  readonly hasOptions = computed(() => this.options().length > 0);
  readonly optionsHeading = computed(() => this.options().map((o) => o.name).join(' / '));
  readonly optionsChanged = computed(() => JSON.stringify(cleanOptions(this.optionsDraft())) !== JSON.stringify(cleanOptions(this.options())));
  /** A fit or a size to multiply by: without one a product has nothing to give a fit to. */
  readonly hasSizes = computed(() => this.variants().some((v) => !!v.size));
  /** Variants deleted on purpose: how many combinations the product could have and doesn't. */
  readonly missingCombinations = computed(() => {
    if (!this.hasOptions()) return 0;
    const slots = new Set(this.variants().map((v) => `${v.fit ?? ''}~${(v.size ?? '').toUpperCase()}`)).size || 1;
    return Math.max(0, combinationCount(this.options()) * slots - this.variants().length);
  });

  /** What saving the edited options will do, in a sentence. */
  protected optionsEffect(): string {
    const problem = optionsProblem(this.optionsDraft());
    if (problem) return problem;
    const draft = cleanOptions(this.optionsDraft());
    const combos = combinationCount(draft);
    const slots = new Set(this.variants().map((v) => `${v.fit ?? ''}~${(v.size ?? '').toUpperCase()}`)).size || 1;
    if (draft.length === 0) return 'The options are removed. Variants that then differ in nothing are merged, and their stock added together.';
    const first = this.options().length === 0 && this.variants().length > 0
      ? ` What the product has now becomes ${draft.map((o) => o.values[0].value).join(' / ')}, keeping its stock; the rest start with none.` : '';
    return `${combos} ${combos === 1 ? 'combination' : 'combinations'}${slots > 1 ? ` × ${slots} sizes = ${combos * slots} variants` : ''}.${first}`;
  }

  resetOptionsDraft(): void {
    this.optionsDraft.set(structuredClone(this.options()));
  }

  /** Saves the "Options" box, asking first when a choice or an option - and its stock - would go. */
  async saveOptions(restore = false): Promise<void> {
    const problem = optionsProblem(this.optionsDraft());
    if (problem) { this.error.set(problem); return; }
    const draft = cleanOptions(this.optionsDraft());
    const keptOptions = new Set(draft.map((o) => o.id));
    const keptValues = new Set(draft.flatMap((o) => o.values.map((v) => v.id)));
    const goneOptions = this.options().filter((o) => !keptOptions.has(o.id));
    const goneValues = this.options().filter((o) => keptOptions.has(o.id)).flatMap((o) => o.values.filter((v) => !keptValues.has(v.id)));
    if (goneValues.length > 0) {
      const names = goneValues.map((v) => v.value).join(', ');
      const affected = this.variants().filter((v) => (v.options ?? []).some((o) => goneValues.some((g) => g.id === o.valueId)));
      const stock = affected.reduce((sum, v) => sum + (v.stockQuantity ?? 0), 0);
      const ok = await this.confirmer.ask({
        title: `Remove ${names}?`,
        message: `${affected.length} ${affected.length === 1 ? 'variant' : 'variants'} with ${stock} in stock ${affected.length === 1 ? 'is' : 'are'} removed from this product. This cannot be undone.`,
        confirmLabel: `Remove ${names}`, danger: true,
      });
      if (!ok) return;
    } else if (goneOptions.length > 0) {
      const names = goneOptions.map((o) => o.name).join(' and ');
      const ok = await this.confirmer.ask({
        title: `Remove the option ${names}?`,
        message: 'Variants that then differ in nothing are merged into one, and their stock is added together.',
        confirmLabel: `Remove ${names}`, danger: true,
      });
      if (!ok) return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api.setOptions(this.productId(), draft, restore).pipe(catchError((err) => {
      this.error.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((result) => {
      this.saving.set(false);
      if (!result) return;
      this.cancelEdit();
      this.cancelSeveral();
      this.options.set(result.options);
      this.optionsDraft.set(structuredClone(result.options));
      this.variants.set(result.variants);
      this.resetFitDraft();
      this.fillFilter.set('');
      this.optionsSaved.emit();
      this.notices.success(restore ? 'Variants put back' : 'Options saved',
        result.options.length ? `${result.options.map((o) => o.name).join(', ')} · ${result.variants.length} variants` : 'No options');
    });
  }

  restoreMissing(): void {
    void this.saveOptions(true);
  }

  // ── the same stock or price for many variants ─────────────────────────────
  /** A choice to narrow "Set for many" to ("Black"), or '' for every variant. */
  readonly fillFilter = signal('');
  readonly fillStock = signal<number | null>(null);
  readonly fillPrice = signal<number | null>(null);
  /** Every choice and fit the variants can be narrowed by. */
  readonly fillChoices = computed(() => [
    ...this.options().flatMap((o) => o.values.map((v) => ({ id: `o:${v.id}`, label: `${o.name}: ${v.value}` }))),
    ...this.currentFits().map((f) => ({ id: `f:${f}`, label: `Fit: ${fitLabel(f)}` })),
  ]);
  readonly fillTargets = computed(() => {
    const filter = this.fillFilter();
    if (!filter) return this.variants();
    return this.variants().filter((v) => filter.startsWith('f:')
      ? v.fit === filter.slice(2)
      : (v.options ?? []).some((o) => `o:${o.valueId}` === filter));
  });

  protected numberOrNull(raw: unknown): number | null {
    if (raw === '' || raw == null) return null;
    const n = +(raw as number);
    return Number.isFinite(n) && n >= 0 ? n : null;
  }

  /** Puts the typed stock and price on each of the chosen variants, one after another. */
  fillMany(): void {
    const targets = this.fillTargets();
    const stock = this.fillStock();
    const price = this.fillPrice();
    if (targets.length === 0 || (stock == null && price == null)) return;
    this.saving.set(true);
    this.error.set('');
    const saves = targets.map((v) => this.api.update(this.productId(), v.id, {
      size: v.size, sku: v.sku,
      stockQuantity: stock ?? v.stockQuantity ?? 0,
      priceOverride: price ?? v.priceOverride,
    }));
    concat(...saves).pipe(toArray(), catchError((err) => {
      this.error.set(parseApiError(err));
      return of(null);
    })).subscribe((saved) => {
      this.saving.set(false);
      // Whatever happened, show what the product has now.
      this.reload();
      if (!saved) return;
      this.fillStock.set(null);
      this.fillPrice.set(null);
      this.notices.success(`${saved.length} ${saved.length === 1 ? 'variant' : 'variants'} updated`,
        [stock != null ? `stock ${stock}` : '', price != null ? `price ৳${price}` : ''].filter(Boolean).join(', '));
    });
  }

  /** The fits the product comes in right now, Drop Shoulder first; empty when it has none. */
  readonly currentFits = computed(() =>
    FITS.map((f) => f.value).filter((f) => this.variants().some((v) => v.fit === f)));
  readonly hasFits = computed(() => this.currentFits().length > 0);
  /** The "Fit" box as it is being edited; saved with "Save fits". */
  readonly fitDraft = signal<Record<Fit, FitDraft>>(blankFitDraft());
  readonly fitsChanged = computed(() => FITS.some((f) => {
    const draft = this.fitDraft()[f.value];
    return draft.picked !== this.currentFits().includes(f.value) || (draft.picked && draft.priceTouched);
  }));
  /** Which fit "Add several sizes" adds to. */
  readonly severalFit = signal<string>('');

  protected labelOf(fit: string): string {
    return fitLabel(fit);
  }

  protected blankRows(): SizeRow[] {
    return emptySizeRows();
  }

  /** A fit's price is the one all its sizes share; null when they have none, or differ. */
  private sharedPrice(fit: Fit): number | null {
    const prices = this.variants().filter((v) => v.fit === fit).map((v) => v.priceOverride ?? null);
    return prices.length > 0 && prices.every((p) => p === prices[0]) ? prices[0] : null;
  }

  protected mixedPrices(fit: Fit): boolean {
    const prices = this.variants().filter((v) => v.fit === fit).map((v) => v.priceOverride ?? null);
    return prices.length > 1 && !prices.every((p) => p === prices[0]);
  }

  /** Puts the "Fit" box back to what the product has. */
  resetFitDraft(): void {
    const draft = blankFitDraft();
    for (const f of FITS) {
      draft[f.value] = { picked: this.currentFits().includes(f.value), price: this.sharedPrice(f.value), priceTouched: false };
    }
    this.fitDraft.set(draft);
  }

  protected toggleFit(fit: Fit): void {
    const d = this.fitDraft();
    this.fitDraft.set({ ...d, [fit]: { ...d[fit], picked: !d[fit].picked } });
  }

  protected setFitPrice(fit: Fit, raw: unknown): void {
    const d = this.fitDraft();
    const price = raw === '' || raw == null ? null : +(raw as number);
    this.fitDraft.set({ ...d, [fit]: { ...d[fit], price: Number.isFinite(price as number) ? price : null, priceTouched: true } });
  }

  /** Saves the "Fit" box, asking first when stock would be removed or merged. */
  async saveFits(): Promise<void> {
    const draft = this.fitDraft();
    const chosen = FITS.map((f) => f.value).filter((f) => draft[f].picked);
    const leaving = this.currentFits().filter((f) => !chosen.includes(f));
    for (const f of chosen) {
      const price = draft[f].price;
      if (draft[f].priceTouched && price != null && !(price > 0)) {
        this.error.set(`${fitLabel(f)}'s price has to be more than 0, or left empty.`);
        return;
      }
    }
    if (leaving.length > 0) {
      const names = leaving.map((f) => fitLabel(f)).join(' and ');
      const ok = await this.confirmer.ask(chosen.length === 0
        ? { title: 'Remove the fits from this product?',
            message: 'It goes back to plain sizes. The stock of the fits is added together, size by size.',
            confirmLabel: 'Remove fits', danger: true }
        : { title: `Remove ${names}?`,
            message: `${names} and its stock are removed from this product. This cannot be undone.`,
            confirmLabel: `Remove ${names}`, danger: true });
      if (!ok) return;
    }
    this.saving.set(true);
    this.error.set('');
    this.api.setFits(this.productId(), chosen.map((f) => ({ fit: f, price: draft[f].price, changePrice: draft[f].priceTouched })))
      .pipe(catchError((err) => {
        this.error.set(parseApiError(err));
        this.saving.set(false);
        return of(null);
      })).subscribe((list) => {
        this.saving.set(false);
        if (!list) return;
        this.cancelEdit();
        this.cancelSeveral();
        this.variants.set(list);
        this.resetFitDraft();
        this.notices.success('Fits saved', chosen.length ? chosen.map((f) => fitLabel(f)).join(' and ') : 'No fit');
      });
  }

  /**
   * Sizes in the order customers see them (S, M, L, XL, XXL) rather than the
   * server's alphabetical L, M, S, XL; anything else goes last, alphabetically.
   */
  readonly sortedVariants = computed(() => {
    const rank = (size: string | null) => {
      const i = SIZE_OPTIONS.indexOf((size ?? '').trim().toUpperCase());
      return i < 0 ? SIZE_OPTIONS.length : i;
    };
    // Drop Shoulder's sizes, then Regular Fit's.
    const fitRank = (fit: string | null | undefined) => {
      const i = FITS.findIndex((f) => f.value === fit);
      return i < 0 ? FITS.length : i;
    };
    // Each combination of options in turn, in the order the options list them.
    const combos = this.comboOrder();
    const comboRank = (key: string | null | undefined) => {
      const i = combos.indexOf(key ?? '');
      return i < 0 ? combos.length : i;
    };
    return [...this.variants()].sort((a, b) =>
      comboRank(a.optionKey) - comboRank(b.optionKey) || fitRank(a.fit) - fitRank(b.fit)
      || rank(a.size) - rank(b.size) || (a.size ?? '').localeCompare(b.size ?? ''));
  });

  /** Every combination's key in the order the options list them: all of Black, then all of White. */
  private readonly comboOrder = computed(() => {
    let combos: Record<string, string>[] = [{}];
    for (const o of this.options()) {
      combos = combos.flatMap((c) => o.values.map((v) => ({ ...c, [o.id as string]: v.id as string })));
    }
    return combos.map((c) => Object.keys(c).sort().map((k) => `${k}=${c[k]}`).join('|'));
  });

  /** "Add several sizes" is open. */
  readonly addingSeveral = signal(false);
  readonly severalRows = signal<SizeRow[]>(emptySizeRows());
  readonly severalCount = computed(() => this.severalRows().filter((r) => r.picked).length);
  /**
   * The sizes already there, which the grid won't offer again: of the fit being
   * added to, for a product that comes in fits.
   */
  readonly takenSizes = computed(() =>
    this.variants().filter((v) => !this.hasFits() || v.fit === this.severalFit())
      .map((v) => v.size).filter((s): s is string => !!s));
  /** Sizes that could still be added, to any fit. */
  readonly freeSizes = computed(() => {
    const groups = this.hasFits() ? this.currentFits() : [null];
    return SIZE_OPTIONS.filter((s) => groups.some((fit) =>
      !this.variants().some((v) => (fit === null || v.fit === fit) && (v.size ?? '').toUpperCase() === s)));
  });
  protected form: VariantForm = { ...EMPTY_FORM };

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['productId']) this.reload();
  }

  /** The variant list is refused unless the person may see products, manage sizes, or see stock. */
  protected canList(): boolean {
    return this.perms.canAny('products.view', 'products.edit', 'products.variants', 'inventory.view');
  }

  reload(): void {
    const id = this.productId();
    if (!id || !this.canList()) return;
    this.loading.set(true);
    this.api.getOptions(id).pipe(catchError(() => of([] as ProductOption[]))).subscribe((options) => {
      this.options.set(options);
      this.optionsDraft.set(structuredClone(options));
    });
    this.api.list(id).pipe(catchError(() => of([] as VariantResponse[]))).subscribe((list) => {
      this.variants.set(list);
      this.loading.set(false);
      this.resetFitDraft();
    });
  }

  startSeveral(): void {
    this.cancelEdit();
    this.error.set('');
    this.severalRows.set(emptySizeRows());
    this.severalFit.set(this.currentFits()[0] ?? '');
    this.addingSeveral.set(true);
  }

  cancelSeveral(): void {
    this.addingSeveral.set(false);
    this.severalRows.set(emptySizeRows());
  }

  /** All the ticked sizes in one request: every one is added, or none is. */
  saveSeveral(): void {
    const fit = this.hasFits() ? this.severalFit() : null;
    const rows = pickedSizes(this.severalRows()).map((r) => (fit ? { ...r, fit } : r));
    if (rows.length === 0) return;
    this.saving.set(true);
    this.error.set('');
    this.api.createMany(this.productId(), rows).pipe(catchError((err) => {
      this.error.set(parseApiError(err));
      this.saving.set(false);
      return of(null);
    })).subscribe((made) => {
      this.saving.set(false);
      if (!made) return;
      // With options the server adds each size to every combination, and may
      // turn a colour that had no size into the first one: read the list again.
      if (this.hasOptions()) this.reload(); else this.variants.update((list) => [...list, ...made]);
      const sizes = [...new Set(made.map((v) => v.size))];
      this.notices.success(sizes.length === 1 ? 'Size added' : `${sizes.length} sizes added`,
        sizes.join(', ') + (this.hasOptions() ? ' — in every combination' : ''));
      this.cancelSeveral();
    });
  }

  startCreate(): void {
    this.addingSeveral.set(false);
    this.editingId.set(null);
    this.creating.set(true);
    this.error.set('');
    this.form = { ...EMPTY_FORM, fit: this.currentFits()[0] ?? '' };
  }

  startEdit(v: VariantResponse): void {
    this.addingSeveral.set(false);
    this.creating.set(false);
    this.editingId.set(v.id);
    this.error.set('');
    this.form = {
      fit: v.fit ?? '',
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
      // A new size says which fit it is for; a size being changed keeps its fit.
      fit: this.hasFits() && this.editingId() == null ? this.form.fit || null : null,
      size: this.form.size.trim() || null,
      sku: this.form.sku.trim() || null,
      stockQuantity: this.form.stockQuantity ?? 0,
      priceOverride: this.form.priceOverride,
    };

    const productId = this.productId();
    const editId = this.editingId();
    // A new size of a product with options is added to every combination.
    if (editId == null && this.hasOptions()) {
      this.api.createMany(productId, [payload]).pipe(catchError((err) => {
        this.error.set(parseApiError(err));
        this.saving.set(false);
        return of(null);
      })).subscribe((made) => {
        this.saving.set(false);
        if (!made) return;
        this.reload();
        this.notices.success('Size added', `${payload.size ?? ''} — in every combination`);
        this.cancelEdit();
      });
      return;
    }
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
      this.notices.success(editId != null ? 'Size saved' : 'Size added', saved.size || saved.sku || '');
      this.cancelEdit();
    });
  }

  async remove(v: VariantResponse): Promise<void> {
    const label = v.name || v.size || String(v.id);
    const ok = await this.confirmer.ask({
      title: `Delete "${label}"?`,
      message: 'Its stock is removed with it.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(this.productId(), v.id)
      .pipe(catchError((err) => {
        this.error.set(parseApiError(err));
        return of(null);
      }))
      .subscribe(() => {
        this.variants.update((list) => list.filter((x) => x.id !== v.id));
        this.resetFitDraft();
        this.notices.success('Size deleted', label);
      });
  }
}

function blankFitDraft(): Record<Fit, FitDraft> {
  return {
    DROP_SHOULDER: { picked: false, price: null, priceTouched: false },
    REGULAR_FIT: { picked: false, price: null, priceTouched: false },
  };
}
