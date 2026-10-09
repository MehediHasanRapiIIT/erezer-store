import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { PagerComponent } from '../../shared/pager/pager.component';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ProductMultiPickerComponent } from '../../shared/product-picker/product-multi-picker.component';
import { BundleRequest, BundleResponse, BundleService, BundleTier, BundleType } from '../../core/services/bundle.service';
import { UploadService } from '../../core/services/upload.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

interface BundleForm {
  name: string;
  label: string;
  description: string;
  offerType: BundleType;
  /** The steps of a quantity discount. */
  tiers: BundleTier[];
  buyCount: number | null;
  getCount: number | null;
  bundlePrice: number | null;
  compareAtPrice: number | null;
  isActive: boolean;
  featured: boolean;
  sortOrder: number | null;
  imageUrls: string[];
  productIds: number[];
}

const EMPTY_FORM: BundleForm = {
  name: '',
  label: 'Limited time',
  description: '',
  offerType: 'FIXED_PRICE',
  tiers: [],
  buyCount: 3,
  getCount: 1,
  bundlePrice: null,
  compareAtPrice: null,
  isActive: true,
  featured: false,
  sortOrder: 0,
  imageUrls: [],
  productIds: [],
};

@Component({
  selector: 'app-bundles',
  standalone: true,
  imports: [FormsModule, SidebarComponent, ProductMultiPickerComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Bundle Offers</h1>
            <span class="text-xs text-gray-400">{{ total() }} total</span>
          </div>
          <div class="flex items-center gap-2 min-w-0">
            <input
              type="search"
              [ngModel]="search()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by name, label or description…"
              aria-label="Search bundles"
              class="w-56 md:w-72 min-w-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 placeholder-gray-400" />
            @if (perms.can('bundles.create')) {
              <button (click)="startCreate()"
                class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">
                + New bundle
              </button>
            }
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-5xl mx-auto space-y-5">

            <p class="rounded-md bg-blue-50 px-3 py-2 text-xs text-blue-700">
              A bundle is a <strong>Buy X Get Y</strong> deal. The customer fills <strong>buy + get</strong> slots
              from the products you pick and pays the fixed <strong>bundle price</strong>. The storefront
              <strong>/bundles</strong> page lists active bundles; the landing widget shows the one marked
              <strong>Featured</strong>.
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
                    <th class="px-4 py-2.5 text-center">Offer</th>
                    <th class="px-4 py-2.5 text-right">Price</th>
                    <th class="px-4 py-2.5 text-center">Products</th>
                    <th class="px-4 py-2.5 text-center">Featured</th>
                    <th class="px-4 py-2.5 text-center">Active</th>
                    <th class="px-4 py-2.5"></th>
                  </tr>
                </thead>
                <tbody class="divide-y divide-gray-50">
                  @if (loading()) {
                    <tr><td colspan="7" class="px-4 py-6 text-center text-gray-400">Loading…</td></tr>
                  }
                  @for (b of bundles(); track b.id) {
                    <tr [class.bg-blue-50]="editingId() === b.id">
                      <td class="px-4 py-2.5 font-medium">{{ b.name }}</td>
                      <td class="px-4 py-2.5 text-center text-xs text-gray-600" data-testid="bundle-headline">{{ b.headline }}</td>
                      <td class="px-4 py-2.5 text-right">
                        @if (b.offerType === 'QUANTITY_DISCOUNT') {
                          <span class="text-xs text-gray-500">by quantity</span>
                        } @else {
                          <span class="font-semibold">৳{{ b.bundlePrice }}</span>
                        }
                        @if (b.compareAtPrice) { <span class="ml-1 text-xs text-gray-400 line-through">৳{{ b.compareAtPrice }}</span> }
                      </td>
                      <td class="px-4 py-2.5 text-center">{{ b.products.length }}</td>
                      <td class="px-4 py-2.5 text-center">
                        @if (b.featured) {
                          <span class="rounded bg-amber-100 px-1.5 py-0.5 text-[10px] font-semibold text-amber-700">★ Featured</span>
                        } @else { <span class="text-gray-300">—</span> }
                      </td>
                      <td class="px-4 py-2.5 text-center">
                        <span class="inline-block h-2.5 w-2.5 rounded-full"
                          [class.bg-emerald-500]="b.isActive" [class.bg-gray-300]="!b.isActive"></span>
                      </td>
                      <td class="px-4 py-2.5">
                        <div class="flex items-center justify-end gap-2">
                          @if (perms.can('bundles.edit')) {
                            <button (click)="startEdit(b)"
                              class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-700 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700">Edit</button>
                          }
                          @if (perms.can('bundles.delete')) {
                            <button (click)="remove(b)"
                              class="rounded-lg border border-gray-200 bg-white px-2.5 py-1.5 text-xs font-medium text-gray-600 hover:border-red-300 hover:bg-red-50 hover:text-red-600">Delete</button>
                          }
                        </div>
                      </td>
                    </tr>
                  } @empty {
                    @if (!loading()) {
                      <tr><td colspan="7" class="px-4 py-6 text-center text-gray-400">{{ searching() ? 'No bundles match your search.' : 'No bundles yet.' }}</td></tr>
                    }
                  }
                </tbody>
              </table>
              @if (total() > 0) {
                <div class="border-t border-gray-100">
                  <app-pager [page]="page()" [size]="pageSize" [total]="total()" [disabled]="loading()" (pageChange)="goToPage($event)" />
                </div>
              }
            </section>

            <!-- Form -->
            @if (creating() || editingId() !== null) {
              <section class="bg-white rounded-xl border border-gray-200 p-5">
                <h2 class="mb-4 text-base font-semibold">{{ editingId() ? 'Edit bundle' : 'New bundle' }}</h2>

                <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
                  <label class="text-xs font-medium text-gray-600 sm:col-span-2">
                    Name
                    <input [(ngModel)]="form.name" placeholder="e.g. T-Shirt Bundle" data-testid="bundle-name"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Eyebrow label
                    <input [(ngModel)]="form.label" placeholder="Limited time"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>

                  <!-- What kind of offer: each kind asks for different numbers below. -->
                  <fieldset class="col-span-2 sm:col-span-3" data-testid="bundle-type">
                    <legend class="mb-1.5 text-xs font-medium text-gray-600">Kind of offer</legend>
                    <div class="grid gap-2 sm:grid-cols-3">
                      @for (t of offerTypes; track t.value) {
                        <label class="flex cursor-pointer items-start gap-2.5 rounded-lg border p-3 transition-colors"
                          [class.border-blue-500]="form.offerType === t.value" [class.bg-blue-50]="form.offerType === t.value"
                          [class.border-gray-200]="form.offerType !== t.value">
                          <input type="radio" name="bundle-offer-type" class="mt-0.5 accent-blue-600" [attr.data-testid]="'bundle-type-' + t.value"
                            [checked]="form.offerType === t.value" (change)="setType(t.value)" />
                          <span>
                            <span class="block text-sm font-semibold text-gray-800">{{ t.label }}</span>
                            <span class="block text-xs font-normal text-gray-500">{{ t.example }}</span>
                          </span>
                        </label>
                      }
                    </div>
                  </fieldset>

                  @if (form.offerType === 'FIXED_PRICE') {
                    <label class="text-xs font-medium text-gray-600">
                      How many items <span class="text-red-500">*</span>
                      <input type="number" min="1" [(ngModel)]="form.buyCount" data-testid="bundle-items"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <label class="text-xs font-medium text-gray-600">
                      Price for all of them (৳) <span class="text-red-500">*</span>
                      <input type="number" step="0.01" min="0" [(ngModel)]="form.bundlePrice" data-testid="bundle-price"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <label class="text-xs font-medium text-gray-600">
                      Usual price (৳) <span class="font-normal text-gray-400">(shown crossed out)</span>
                      <input type="number" step="0.01" min="0" [(ngModel)]="form.compareAtPrice"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                  }

                  @if (form.offerType === 'BUY_X_GET_Y') {
                    <label class="text-xs font-medium text-gray-600">
                      Items the customer pays for <span class="text-red-500">*</span>
                      <input type="number" min="1" [(ngModel)]="form.buyCount" data-testid="bundle-buy"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <label class="text-xs font-medium text-gray-600">
                      Free items on top <span class="text-red-500">*</span>
                      <input type="number" min="1" [(ngModel)]="form.getCount" data-testid="bundle-get"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <div class="flex items-end pb-2 text-xs text-gray-500">
                      = <strong class="mx-1">{{ slots() }}</strong> items picked in all
                    </div>
                    <label class="text-xs font-medium text-gray-600">
                      Price for all of them (৳) <span class="text-red-500">*</span>
                      <input type="number" step="0.01" min="0" [(ngModel)]="form.bundlePrice" data-testid="bundle-price"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <label class="text-xs font-medium text-gray-600">
                      Usual price (৳) <span class="font-normal text-gray-400">(shown crossed out)</span>
                      <input type="number" step="0.01" min="0" [(ngModel)]="form.compareAtPrice"
                        class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                    </label>
                    <div></div>
                  }

                  @if (form.offerType === 'QUANTITY_DISCOUNT') {
                    <div class="col-span-2 sm:col-span-3 rounded-lg border border-gray-200 p-3" data-testid="bundle-tiers">
                      <div class="mb-2 flex items-center justify-between">
                        <p class="text-xs font-medium text-gray-600">Steps <span class="font-normal text-gray-400">— the more items, the bigger the percentage off</span></p>
                        @if (form.tiers.length < 6) {
                          <button type="button" (click)="addTier()" data-testid="bundle-add-tier"
                            class="rounded-lg border border-blue-200 px-2.5 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50">+ Step</button>
                        }
                      </div>
                      @for (tier of form.tiers; track $index; let ti = $index) {
                        <div class="mt-1.5 flex flex-wrap items-center gap-2 text-sm text-gray-700" data-testid="bundle-tier">
                          Buy
                          <input type="number" min="2" max="20" [(ngModel)]="tier.quantity" [attr.aria-label]="'Quantity of step ' + (ti + 1)"
                            class="w-20 rounded-lg border border-gray-200 px-2 py-1.5 text-sm" />
                          or more, save
                          <input type="number" min="1" max="99" step="0.5" [(ngModel)]="tier.percentOff" [attr.aria-label]="'Percent off for step ' + (ti + 1)"
                            class="w-20 rounded-lg border border-gray-200 px-2 py-1.5 text-sm" />
                          %
                          @if (form.tiers.length > 1) {
                            <button type="button" (click)="removeTier(ti)" class="text-xs font-medium text-red-500 hover:underline">Remove</button>
                          }
                        </div>
                      }
                      <p class="mt-2 text-[11px] text-gray-400">The customer picks any number of items from the smallest step up (at most 20 in one order). The percentage comes off the normal prices of what they picked.</p>
                    </div>
                  }

                  <!-- The offer in a sentence, as customers will understand it. -->
                  <p class="col-span-2 sm:col-span-3 rounded-lg bg-gray-50 px-3 py-2 text-xs text-gray-600" data-testid="bundle-preview">
                    <span class="font-semibold text-gray-800">How it reads:</span> {{ preview() }}
                    @if (savings() > 0) { <span class="ml-1 font-semibold text-emerald-600">Save ৳{{ savings() }}.</span> }
                  </p>

                  <label class="text-xs font-medium text-gray-600">
                    Sort order
                    <input type="number" [(ngModel)]="form.sortOrder"
                      class="mt-1 w-24 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600 mt-5">
                    <input type="checkbox" [(ngModel)]="form.isActive" /> Active
                  </label>
                  <label class="flex items-center gap-2 text-xs font-medium text-gray-600 mt-5">
                    <input type="checkbox" [(ngModel)]="form.featured" /> Featured on landing
                  </label>
                </div>

                <label class="mt-3 block text-xs font-medium text-gray-600">
                  Description
                  <textarea [(ngModel)]="form.description" rows="2" placeholder="Short blurb shown on the bundle page"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                </label>

                <!-- Gallery images -->
                <div class="mt-5">
                  <div class="mb-2 flex items-center justify-between">
                    <span class="text-xs font-semibold text-gray-700">Bundle images <span class="font-normal text-gray-400">({{ form.imageUrls.length }})</span></span>
                    <label class="cursor-pointer rounded-lg border border-gray-200 px-3 py-1.5 text-xs font-medium text-gray-700 hover:bg-gray-50">
                      <input type="file" accept="image/*" class="hidden" (change)="onImageSelected($event)" [disabled]="uploading()" />
                      {{ uploading() ? 'Uploading…' : '+ Add image' }}
                    </label>
                  </div>
                  <p class="picture-hint text-xs text-gray-500 mb-2" data-testid="picture-hint"><span class="font-semibold text-gray-700">Best size:</span> 1200 × 1200 px, square. Other shapes are trimmed to a square.</p>
                  <div class="flex flex-wrap gap-2">
                    @for (url of form.imageUrls; track url) {
                      <div class="group relative">
                        <img [src]="url" alt="" class="h-20 w-20 rounded-lg border border-gray-200 object-cover" />
                        <button type="button" (click)="removeImage(url)"
                          class="absolute -right-1.5 -top-1.5 rounded-full bg-red-500 px-1.5 text-xs text-white">✕</button>
                      </div>
                    } @empty {
                      <p class="text-xs text-gray-400">No images yet.</p>
                    }
                  </div>
                </div>

                <!-- Product picker -->
                <div class="mt-5">
                  <app-product-multi-picker label="Eligible products" [selected]="form.productIds"
                    (selectedChange)="form.productIds = $event" />
                </div>

                <div class="mt-4 flex justify-end gap-2">
                  <button type="button" (click)="cancelEdit()"
                    class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">Cancel</button>
                  <button type="button" (click)="save()" [disabled]="saving()"
                    class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                    {{ saving() ? 'Saving…' : 'Save bundle' }}
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
export class BundlesComponent implements OnInit, OnDestroy {
  private readonly api = inject(BundleService);
  private readonly uploadApi = inject(UploadService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  readonly bundles = signal<BundleResponse[]>([]);
  readonly loading = signal(false);
  readonly saving = signal(false);
  readonly uploading = signal(false);
  readonly creating = signal(false);
  readonly editingId = signal<string | null>(null);
  readonly errorMessage = signal<string>('');

  protected form: BundleForm = { ...EMPTY_FORM, tiers: [], imageUrls: [], productIds: [] };

  /** The kinds of offer, with an example of each for the picker. */
  protected readonly offerTypes: { value: BundleType; label: string; example: string }[] = [
    { value: 'FIXED_PRICE', label: 'Fixed-price bundle', example: 'Any 3 for ৳999' },
    { value: 'BUY_X_GET_Y', label: 'Buy X Get Y free', example: 'Buy 2 Get 1 Free' },
    { value: 'QUANTITY_DISCOUNT', label: 'Quantity discount', example: 'Buy 2 save 10%, buy 3 save 15%' },
  ];

  // Read from the form each time: the form is a plain object, not a signal.
  protected slots(): number {
    return (this.form.buyCount ?? 0) + (this.form.offerType === 'BUY_X_GET_Y' ? (this.form.getCount ?? 0) : 0);
  }

  protected savings(): number {
    if (this.form.offerType === 'QUANTITY_DISCOUNT') return 0;
    const c = this.form.compareAtPrice ?? 0;
    const b = this.form.bundlePrice ?? 0;
    return c > b ? c - b : 0;
  }

  protected setType(type: BundleType): void {
    this.form.offerType = type;
    this.errorMessage.set('');
    if (type === 'BUY_X_GET_Y' && !this.form.getCount) this.form.getCount = 1;
    if (type === 'QUANTITY_DISCOUNT' && this.form.tiers.length === 0) {
      this.form.tiers = [{ quantity: 2, percentOff: 10 }, { quantity: 3, percentOff: 15 }];
    }
  }

  protected addTier(): void {
    const last = this.form.tiers[this.form.tiers.length - 1];
    this.form.tiers.push({ quantity: last?.quantity ? last.quantity + 1 : 2, percentOff: last?.percentOff ? last.percentOff + 5 : 10 });
  }

  protected removeTier(index: number): void {
    this.form.tiers.splice(index, 1);
  }

  /** The offer in a sentence, from what is typed so far. */
  protected preview(): string {
    const f = this.form;
    const price = f.bundlePrice ? `৳${f.bundlePrice}` : 'the price you set';
    if (f.offerType === 'FIXED_PRICE') {
      return `Customers pick any ${f.buyCount || '…'} of the products below and pay ${price} for all of them.`;
    }
    if (f.offerType === 'BUY_X_GET_Y') {
      return `Buy ${f.buyCount || '…'} Get ${f.getCount || '…'} Free: customers pick ${this.slots() || '…'} of the products below and pay ${price} for all of them.`;
    }
    const steps = [...f.tiers].filter((t) => t.quantity && t.percentOff).sort((a, b) => a.quantity! - b.quantity!)
      .map((t) => `${t.quantity} or more save ${t.percentOff}%`);
    return steps.length
      ? `Customers pick as many of the products below as they like: ${steps.join(', ')}.`
      : 'Add a step, such as 2 or more save 10%.';
  }

  /** What is wrong with the numbers of the chosen kind of offer, or '' when they are fine. */
  private offerProblem(): string {
    const f = this.form;
    if (f.offerType === 'QUANTITY_DISCOUNT') {
      if (f.tiers.length === 0) return 'Add at least one step, such as 2 items for 10% off.';
      const sorted = [...f.tiers].sort((a, b) => (a.quantity ?? 0) - (b.quantity ?? 0));
      for (let i = 0; i < sorted.length; i++) {
        const t = sorted[i];
        if (!t.quantity || t.quantity < 2) return 'Each step needs a quantity of 2 or more.';
        if (t.quantity > 20) return 'A step can be for at most 20 items.';
        if (!t.percentOff || t.percentOff <= 0 || t.percentOff >= 100) return 'Each step needs a percentage above 0 and below 100.';
        if (i > 0 && t.quantity === sorted[i - 1].quantity) return `Two steps are for ${t.quantity} items. Keep one.`;
        if (i > 0 && t.percentOff <= sorted[i - 1].percentOff!) return `Buying ${t.quantity} must save more than buying ${sorted[i - 1].quantity}.`;
      }
      return '';
    }
    if (!f.buyCount || f.buyCount < 1) {
      return f.offerType === 'FIXED_PRICE' ? 'Say how many items are in the bundle.' : 'Say how many items the customer pays for.';
    }
    if (f.offerType === 'BUY_X_GET_Y' && (!f.getCount || f.getCount < 1)) {
      return 'Say how many items are free. For none, choose a fixed-price bundle.';
    }
    if (f.bundlePrice == null || f.bundlePrice <= 0) return 'Give the price of the bundle.';
    return '';
  }

  protected readonly pageSize = 20;
  /** Zero-based page on screen, and the number of bundles across all pages. */
  readonly page = signal(0);
  readonly total = signal(0);
  /** Typed search text, sent to the server after a short pause. */
  readonly search = signal('');
  protected readonly searching = computed(() => this.search().trim().length > 0);

  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  ngOnInit(): void {
    this.loadPage(0);
    // Typing searches all bundles after a short pause, from the first page.
    this.searchSubject.pipe(map((q) => q.trim()), debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.searchSubject.next(q);
  }

  protected goToPage(page: number): void {
    this.loadPage(page);
  }

  /** One page from the server, for the current search. */
  private loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.api.list(page, this.pageSize, this.search())
      .pipe(catchError(() => of(null)))
      .subscribe((res) => {
        if (request !== this.latestRequest) return;
        this.loading.set(false);
        if (!res) return;
        // The last row of this page went away (e.g. it was deleted): show the page before it.
        if (res.content.length === 0 && page > 0) {
          this.loadPage(Math.min(page - 1, Math.max(res.totalPages - 1, 0)));
          return;
        }
        this.bundles.set(res.content);
        this.page.set(res.number);
        this.total.set(res.totalElements);
      });
  }

  protected startCreate(): void {
    this.editingId.set(null);
    this.creating.set(true);
    this.errorMessage.set('');
    this.form = { ...EMPTY_FORM, tiers: [], imageUrls: [], productIds: [] };
  }

  protected startEdit(b: BundleResponse): void {
    this.creating.set(false);
    this.editingId.set(b.id);
    this.errorMessage.set('');
    this.form = {
      name: b.name,
      label: b.label ?? '',
      description: b.description ?? '',
      offerType: b.offerType ?? (b.getCount > 0 ? 'BUY_X_GET_Y' : 'FIXED_PRICE'),
      tiers: (b.tiers ?? []).map((t) => ({ ...t })),
      buyCount: b.buyCount,
      getCount: b.getCount,
      bundlePrice: b.bundlePrice,
      compareAtPrice: b.compareAtPrice,
      isActive: b.isActive,
      featured: b.featured ?? false,
      sortOrder: b.sortOrder ?? 0,
      imageUrls: [...b.images],
      productIds: b.products.map((p) => p.id),
    };
  }

  protected cancelEdit(): void {
    this.creating.set(false);
    this.editingId.set(null);
    this.form = { ...EMPTY_FORM, tiers: [], imageUrls: [], productIds: [] };
  }

  // ── Images ──────────────────────────────────────────────────────────────
  protected onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.uploadApi.uploadImage(file)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return of(''); }))
      .subscribe((url) => {
        this.uploading.set(false);
        input.value = '';
        if (url) this.form.imageUrls = [...this.form.imageUrls, url];
      });
  }

  protected removeImage(url: string): void {
    this.form.imageUrls = this.form.imageUrls.filter((u) => u !== url);
  }

  protected save(): void {
    if (!this.form.name.trim()) { this.errorMessage.set('Name is required.'); return; }
    const problem = this.offerProblem();
    if (problem) { this.errorMessage.set(problem); return; }
    if (this.form.productIds.length === 0) { this.errorMessage.set('Select at least one eligible product.'); return; }

    this.saving.set(true);
    this.errorMessage.set('');
    const payload: BundleRequest = {
      name: this.form.name.trim(),
      label: this.form.label.trim() || null,
      description: this.form.description.trim() || null,
      offerType: this.form.offerType,
      tiers: this.form.offerType === 'QUANTITY_DISCOUNT' ? this.form.tiers : [],
      buyCount: this.form.offerType === 'QUANTITY_DISCOUNT' ? null : this.form.buyCount!,
      getCount: this.form.offerType === 'BUY_X_GET_Y' ? this.form.getCount! : 0,
      bundlePrice: this.form.offerType === 'QUANTITY_DISCOUNT' ? null : this.form.bundlePrice!,
      compareAtPrice: this.form.offerType === 'QUANTITY_DISCOUNT' ? null : this.form.compareAtPrice,
      isActive: this.form.isActive,
      featured: this.form.featured,
      sortOrder: this.form.sortOrder ?? 0,
      imageUrls: this.form.imageUrls,
      productIds: this.form.productIds,
    };

    const editId = this.editingId();
    const obs$ = editId ? this.api.update(editId, payload) : this.api.create(payload);
    obs$.pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); this.saving.set(false); return of(null); }))
      .subscribe((saved) => {
        this.saving.set(false);
        if (!saved) return;
        this.loadPage(this.page());
        this.notices.success(editId ? 'Bundle saved' : 'Bundle added', saved.name);
        this.cancelEdit();
      });
  }

  protected async remove(b: BundleResponse): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete bundle "${b.name}"?`,
      message: 'It stops showing in the shop straight away.',
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.api.delete(b.id)
      .pipe(catchError((err) => { this.errorMessage.set(parseApiError(err)); return EMPTY; }))
      .subscribe(() => {
        this.loadPage(this.page());
        this.notices.success('Bundle deleted', b.name);
      });
  }
}
