import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { PagerComponent } from '../../shared/pager/pager.component';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ProductMultiPickerComponent } from '../../shared/product-picker/product-multi-picker.component';
import { BundleRequest, BundleResponse, BundleService } from '../../core/services/bundle.service';
import { UploadService } from '../../core/services/upload.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

interface BundleForm {
  name: string;
  label: string;
  description: string;
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
  buyCount: 2,
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
                      <td class="px-4 py-2.5 text-center text-xs text-gray-600">Buy {{ b.buyCount }} Get {{ b.getCount }}</td>
                      <td class="px-4 py-2.5 text-right">
                        <span class="font-semibold">৳{{ b.bundlePrice }}</span>
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
                    <input [(ngModel)]="form.name" placeholder="Classic Polo Bundle (Buy 2 Get 1 Free)"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Eyebrow label
                    <input [(ngModel)]="form.label" placeholder="Limited time"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>

                  <label class="text-xs font-medium text-gray-600">
                    Buy count
                    <input type="number" min="1" [(ngModel)]="form.buyCount"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Get free count
                    <input type="number" min="0" [(ngModel)]="form.getCount"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <div class="flex items-end pb-2 text-xs text-gray-500">
                    = <strong class="mx-1">{{ slots() }}</strong> slots to fill
                  </div>

                  <label class="text-xs font-medium text-gray-600">
                    Bundle price (৳) <span class="text-red-500">*</span>
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.bundlePrice"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <label class="text-xs font-medium text-gray-600">
                    Compare-at price (৳) <span class="font-normal text-gray-400">(strikethrough)</span>
                    <input type="number" step="0.01" min="0" [(ngModel)]="form.compareAtPrice"
                      class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  </label>
                  <div class="flex items-end pb-2 text-xs" [class.text-emerald-600]="savings() > 0" [class.text-gray-400]="savings() <= 0">
                    @if (savings() > 0) { Save ৳{{ savings() }} } @else { — }
                  </div>

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

  protected form: BundleForm = { ...EMPTY_FORM, imageUrls: [], productIds: [] };

  protected readonly slots = computed(() => (this.form.buyCount ?? 0) + (this.form.getCount ?? 0));
  protected readonly savings = computed(() => {
    const c = this.form.compareAtPrice ?? 0;
    const b = this.form.bundlePrice ?? 0;
    return c > b ? c - b : 0;
  });

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
    this.form = { ...EMPTY_FORM, imageUrls: [], productIds: [] };
  }

  protected startEdit(b: BundleResponse): void {
    this.creating.set(false);
    this.editingId.set(b.id);
    this.errorMessage.set('');
    this.form = {
      name: b.name,
      label: b.label ?? '',
      description: b.description ?? '',
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
    this.form = { ...EMPTY_FORM, imageUrls: [], productIds: [] };
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
    if (!this.form.buyCount || this.form.buyCount < 1) { this.errorMessage.set('Buy count must be at least 1.'); return; }
    if (this.form.getCount == null || this.form.getCount < 0) { this.errorMessage.set('Get count must be 0 or more.'); return; }
    if (this.form.bundlePrice == null || this.form.bundlePrice <= 0) { this.errorMessage.set('Enter a bundle price greater than 0.'); return; }
    if (this.form.productIds.length === 0) { this.errorMessage.set('Select at least one eligible product.'); return; }

    this.saving.set(true);
    this.errorMessage.set('');
    const payload: BundleRequest = {
      name: this.form.name.trim(),
      label: this.form.label.trim() || null,
      description: this.form.description.trim() || null,
      buyCount: this.form.buyCount!,
      getCount: this.form.getCount!,
      bundlePrice: this.form.bundlePrice!,
      compareAtPrice: this.form.compareAtPrice,
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
