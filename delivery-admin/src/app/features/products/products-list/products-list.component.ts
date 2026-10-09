import { Component, signal, computed, inject, OnInit, OnDestroy } from '@angular/core';
import { SizeChartPickerComponent } from '../../../shared/size-chart-picker/size-chart-picker.component';
import { RouterLink, Router, ActivatedRoute } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { BulkProductAction, ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse, ProductResponse } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { salePercent } from '../../../core/utils/price.util';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import { ConfirmService } from '../../../core/services/confirm.service';
import { ShippingService } from '../../../core/services/shipping.service';
import { ACCESS } from '../../../core/access/admin-pages';
import { VariantService } from '../../../core/services/variant.service';
import { FITS, groupByFit } from '../shared/fit-sizes.component';

/**
 * The Products page. Search, the category filter and paging are all done by
 * the server, so a search covers every product, not just the page on screen.
 */
@Component({
  selector: 'app-products-list',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent, SizeChartPickerComponent],
  templateUrl: './products-list.component.html',
})
export class ProductsListComponent implements OnInit, OnDestroy {
  private productService = inject(ProductService);
  private readonly notices = inject(NoticeService);
  private categoryService = inject(CategoryService);
  private readonly shippingService = inject(ShippingService);
  private readonly confirmer = inject(ConfirmService);
  private readonly variantService = inject(VariantService);
  private router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly perms = inject(PermissionService);
  protected readonly ACCESS = ACCESS;
  private destroy$ = new Subject<void>();
  private searchSubject = new Subject<string>();

  readonly pageSize = 10;

  searchQuery = signal('');
  /** Only products in this category; null for all. */
  categoryId = signal<number | null>(null);
  categories = signal<CategoryResponse[]>([]);
  currentPage = signal(0); // 0-indexed for backend
  totalElements = signal(0);
  totalPages = signal(0);

  products = signal<ProductResponse[]>([]);
  isLoading = signal(false);
  errorMessage = signal('');
  deleteConfirmId = signal<number | null>(null);
  isDeleting = signal(false);

  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  // ── Fits for many products ─────────────────────────────────────────────────
  fitMode = signal(false);
  fitTab = signal<'products' | 'category'>('products');
  readonly fitOptions = FITS;
  /** The fits to give: Drop Shoulder, Regular Fit, both, or neither. */
  fitChoice = signal<Set<string>>(new Set(['DROP_SHOULDER']));
  fitCategoryId = signal<number | null>(null);
  fitSaving = signal(false);
  fitError = signal('');
  /** A product's sizes under their fit, for the Stock column. */
  protected readonly fitGroups = groupByFit;

  // ── Delivery charge ────────────────────────────────────────────────────────
  // What a product costs to deliver, set here rather than on the product form:
  // a new product uses its area's price until someone says otherwise.
  chargeMode = signal(false);
  chargeTab = signal<'products' | 'category' | 'all'>('products');
  /** Products ticked for a charge, kept while paging. */
  picks = signal<Set<number>>(new Set());
  /**
   * What to set, as typed. A number input hands back a number, or null when the
   * box is empty — and null is not zero here: zero means delivered free.
   */
  chargeAmount = signal<number | null>(null);
  /** Take the charge away instead, so the area's price decides again. */
  useAreaPrice = signal(false);
  chargeCategoryId = signal<number | null>(null);
  chargeSaving = signal(false);
  chargeError = signal('');

  showingFrom = computed(() => this.currentPage() * this.pageSize + 1);
  showingTo = computed(() => Math.min((this.currentPage() + 1) * this.pageSize, this.totalElements()));

  // Page numbers to show in pagination
  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    if (total <= 5) return Array.from({ length: total }, (_, i) => i);
    const pages: number[] = [];
    if (current > 1) pages.push(0);
    if (current > 2) pages.push(-1); // ellipsis
    for (let i = Math.max(0, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i);
    if (current < total - 3) pages.push(-1); // ellipsis
    if (current < total - 2) pages.push(total - 1);
    return [...new Set(pages)];
  });

  ngOnInit(): void {
    // Arrived from "Add several products": show the category just added to.
    const fromAddress = Number(this.route.snapshot.queryParamMap.get('categoryId'));
    if (fromAddress > 0) this.categoryId.set(fromAddress);
    this.loadPage(0);
    this.categoryService.getCategories().subscribe({
      next: (cats) => this.categories.set(cats),
      error: () => this.categories.set([]),
    });

    // Typing searches all products after a short pause, from the first page.
    this.searchSubject.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      takeUntil(this.destroy$)
    ).subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  onCategoryChange(id: number | null): void {
    this.categoryId.set(id);
    this.loadPage(0);
  }

  /** One page from the server, for the current search and category. */
  loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.productService.searchAdmin({
      q: this.searchQuery().trim(),
      categoryId: this.categoryId(),
      page,
      size: this.pageSize,
    }).subscribe({
      next: (data) => {
        if (request !== this.latestRequest) return;
        // The last product on the last page was just deleted: show the page before.
        if (data.content.length === 0 && page > 0) {
          this.loadPage(page - 1);
          return;
        }
        this.products.set(data.content);
        this.currentPage.set(data.number);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.isLoading.set(false);
      },
      error: (err) => {
        if (request !== this.latestRequest) return;
        this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  setPage(page: number): void {
    if (page >= 0 && page < this.totalPages()) this.loadPage(page);
  }

  stockQtyClass(status: string): string {
    if (status === 'IN_STOCK') return 'text-emerald-600';
    if (status === 'LOW_STOCK') return 'text-orange-500';
    return 'text-red-500';
  }

  stockLabel(status: string): string {
    if (status === 'IN_STOCK') return 'In Stock';
    if (status === 'LOW_STOCK') return 'Low Stock';
    return 'Out of Stock';
  }

  categoryBadgeClass(name: string | null): string {
    const map: Record<string, string> = {
      grains:  'bg-yellow-100 text-yellow-700 border border-yellow-200',
      oils:    'bg-green-100 text-green-700 border border-green-200',
      spices:  'bg-orange-100 text-orange-700 border border-orange-200',
      dairy:   'bg-blue-100 text-blue-700 border border-blue-200',
      bakery:  'bg-pink-100 text-pink-700 border border-pink-200',
      pulses:  'bg-gray-100 text-gray-600 border border-gray-200',
      meat:    'bg-red-100 text-red-700 border border-red-200',
      fruits:  'bg-lime-100 text-lime-700 border border-lime-200',
      vegetables: 'bg-emerald-100 text-emerald-700 border border-emerald-200',
      beverages:  'bg-cyan-100 text-cyan-700 border border-cyan-200',
    };
    const key = (name ?? '').toLowerCase();
    return map[key] ?? 'bg-gray-100 text-gray-600 border border-gray-200';
  }

  /** The status switch re-saves the product with its price unchanged, so "Edit products" is enough. */
  canToggleAvailability(_product: ProductResponse): boolean {
    return this.perms.can('products.edit');
  }

  toggleAvailability(product: ProductResponse): void {
    const dto = {
      categoryId: product.categoryId,
      name: product.name,
      // Required on every save; sent back unchanged.
      productCode: product.productCode,
      description: product.description,
      price: product.price,
      // Without this the save would remove the product's sale price.
      discountPercentage: salePercent(product.price, product.discountPrice),
      shopId: 1,
      isAvailable: !product.isAvailable,
    };
    this.productService.updateProduct(product.id, dto).subscribe({
      next: (updated) => {
        this.products.update(list =>
          list.map(p => p.id === product.id ? { ...p, isAvailable: updated.isAvailable } : p)
        );
        this.notices.success(
          updated.isAvailable ? 'Product is on sale in the shop' : 'Product hidden from the shop', product.name);
      },
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  toggleFeatured(product: ProductResponse): void {
    this.productService.setFeatured(product.id, !product.isFeatured).subscribe({
      next: (updated) => {
        this.products.update(list =>
          list.map(p => p.id === product.id ? { ...p, isFeatured: updated.isFeatured } : p)
        );
        this.notices.success(
          updated.isFeatured ? 'Featured on the home page' : 'No longer featured', product.name);
      },
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  /** Product id whose "Show qty" switch is saving. */
  readonly stockSaving = signal<number | null>(null);

  /**
   * Flips what this product's page shows. The switch mirrors what customers see
   * now (the category's setting included), and a click makes it this product's
   * own choice; "Same as category" is back on the product's edit page.
   */
  toggleStockQuantity(product: ProductResponse): void {
    const next = product.showStockQuantity ? 'LABEL' : 'QUANTITY';
    this.stockSaving.set(product.id);
    this.productService.setStockDisplay(product.id, next).subscribe({
      next: (updated) => {
        this.products.update(list => list.map(p => p.id === product.id
          ? { ...p, stockDisplay: updated.stockDisplay, showStockQuantity: updated.showStockQuantity } : p));
        this.stockSaving.set(null);
        this.notices.success(
          updated.showStockQuantity ? 'Product page shows the quantity' : 'Product page shows stock labels', product.name);
      },
      error: (err) => { this.stockSaving.set(null); this.errorMessage.set(parseApiError(err)); },
    });
  }

  // ── Delivery charge ────────────────────────────────────────────────────────

  readonly chargeTabs: { id: 'products' | 'category' | 'all'; label: string }[] = [
    { id: 'products', label: 'Chosen products' },
    { id: 'category', label: 'A category' },
    { id: 'all', label: 'The whole shop' },
  ];

  canSetCharges(): boolean {
    return this.perms.can('shipping.edit');
  }

  canSetFits(): boolean {
    return this.perms.can('products.variants');
  }

  toggleFitMode(): void {
    this.fitMode.update((on) => !on);
    this.fitError.set('');
    // One panel at a time: they share the ticks in the list.
    if (this.fitMode()) this.chargeMode.set(false);
    this.picks.set(new Set());
  }

  isFitChosen(fit: string): boolean {
    return this.fitChoice().has(fit);
  }

  toggleFitChoice(fit: string): void {
    const set = new Set(this.fitChoice());
    if (!set.delete(fit)) set.add(fit);
    this.fitChoice.set(set);
  }

  private chosenFits(): string[] {
    return FITS.map((f) => f.value).filter((f) => this.fitChoice().has(f));
  }

  /** What the Fits panel will do, in words. */
  fitPreview(): string {
    const fits = FITS.filter((f) => this.fitChoice().has(f.value)).map((f) => f.label);
    const what = fits.length === 0
      ? 'take the fits off: each product goes back to plain sizes, with the stock of its fits added together'
      : `come in ${fits.join(' and ')}`;
    const who = this.fitTab() === 'products'
      ? `The ${this.pickedCount()} ticked product${this.pickedCount() === 1 ? '' : 's'} will`
      : `Every product in ${this.categories().find((c) => c.id === this.fitCategoryId())?.name ?? 'the category you choose'} will`;
    return `${who} ${what}. A fit being added gets every size with no stock; one being removed goes with its stock. Products with no sizes are left alone.`;
  }

  async applyFits(): Promise<void> {
    if (this.fitSaving()) return;
    const fits = this.chosenFits();
    const byCategory = this.fitTab() === 'category';
    if (byCategory && !this.fitCategoryId()) { this.fitError.set('Choose a category.'); return; }
    if (!byCategory && this.pickedCount() === 0) { this.fitError.set('Tick at least one product in the list.'); return; }
    const labels = FITS.filter((f) => fits.includes(f.value)).map((f) => f.label).join(' and ') || 'no fit';
    const ok = await this.confirmer.ask({
      title: `Set the fit to ${labels}?`,
      message: this.fitPreview() + ' This cannot be undone.',
      confirmLabel: 'Set fits',
      danger: true,
    });
    if (!ok) return;
    this.fitSaving.set(true);
    this.fitError.set('');
    this.variantService.setFitsForMany(byCategory
      ? { scope: 'CATEGORY', categoryId: this.fitCategoryId()!, fits }
      : { scope: 'PRODUCTS', productIds: [...this.picks()], fits }).subscribe({
      next: (result) => {
        this.fitSaving.set(false);
        this.picks.set(new Set());
        this.notices.success('Fits saved', result.message);
        this.loadPage(this.currentPage());
      },
      error: (err) => {
        this.fitSaving.set(false);
        this.fitError.set(parseApiError(err));
      },
    });
  }

  toggleChargeMode(): void {
    this.chargeMode.update((on) => !on);
    if (this.chargeMode()) this.fitMode.set(false);
    if (!this.chargeMode()) {
      this.picks.set(new Set());
      this.chargeError.set('');
    }
  }

  /**
   * True while the ticking column is on screen: always for staff who may do
   * something to many products at once, otherwise only inside the fits and
   * delivery-charge tools.
   */
  pickingProducts(): boolean {
    return this.canBulk()
      || (this.chargeMode() && this.chargeTab() === 'products' && this.canSetCharges())
      || (this.fitMode() && this.fitTab() === 'products' && this.canSetFits());
  }

  // ── One action for the ticked products ─────────────────────────────────────

  /** Most products one action takes; the server refuses more. */
  readonly bulkLimit = 500;
  /** The switches in the "More actions" list, each with the permission it needs on one product. */
  readonly bulkSwitches: { action: BulkProductAction; label: string; perm: string; ask: string }[] = [
    { action: 'SHOW', label: 'Show in the shop', perm: 'products.edit', ask: 'put on sale in the shop' },
    { action: 'HIDE', label: 'Hide from the shop', perm: 'products.edit', ask: 'hidden from the shop' },
    { action: 'FEATURE', label: 'Feature on the home page', perm: 'products.feature', ask: 'featured on the home page' },
    { action: 'UNFEATURE', label: 'Stop featuring', perm: 'products.feature', ask: 'taken out of the featured products' },
    { action: 'NEW_ARRIVAL_ON', label: 'Mark as new arrival', perm: 'products.feature', ask: 'marked as new arrivals' },
    { action: 'NEW_ARRIVAL_OFF', label: 'Remove from new arrivals', perm: 'products.feature', ask: 'taken out of the new arrivals' },
    { action: 'NEVER_DISCOUNT_ON', label: 'Never discount', perm: 'discounts.switches', ask: 'kept out of automatic discounts' },
    { action: 'NEVER_DISCOUNT_OFF', label: 'Allow discounts again', perm: 'discounts.switches', ask: 'allowed in automatic discounts again' },
    { action: 'STOCK_SHOW_QUANTITY', label: 'Show stock quantity', perm: 'products.edit', ask: 'set to show how many are left' },
    { action: 'STOCK_SHOW_LABELS', label: 'Show stock labels only', perm: 'products.edit', ask: 'set to show "In stock" labels instead of the quantity' },
  ];
  /** The category chosen in the bar to move the ticked products to. */
  bulkCategoryId = signal<number | null>(null);
  /** The switch chosen in "More actions". */
  bulkSwitch = signal<BulkProductAction | null>(null);
  bulkSaving = signal(false);
  /** The size chart chosen in the bar for the ticked products; 0 for none of their own. */
  bulkChartId = signal(0);

  canBulk(): boolean {
    return this.canBulkEdit() || this.canBulkDelete() || this.allowedSwitches().length > 0;
  }
  canBulkEdit(): boolean { return this.perms.can('products.edit'); }
  canBulkDelete(): boolean { return this.perms.can('products.delete'); }
  /** The switches this person may flip. */
  allowedSwitches() {
    return this.bulkSwitches.filter((s) => this.perms.can(s.perm));
  }

  /** The bar of actions shows once something is ticked, and steps aside for the fits and delivery tools, which use the same ticks. */
  showBulkBar(): boolean {
    return this.canBulk() && this.pickedCount() > 0 && !this.fitMode() && !this.chargeMode();
  }

  private tickedWords(): string {
    const n = this.pickedCount();
    return `${n} product${n === 1 ? '' : 's'}`;
  }

  async bulkMove(): Promise<void> {
    const categoryId = this.bulkCategoryId();
    if (categoryId == null) return;
    const category = this.categories().find((c) => c.id === categoryId);
    const name = category?.label || category?.name || 'that category';
    const ok = await this.confirmer.ask({
      title: `Move ${this.tickedWords()} to ${name}?`,
      message: 'Their product codes stay as they are. Everything else about them stays the same too.',
      confirmLabel: 'Yes, move them',
    });
    if (ok) this.runBulk('MOVE_CATEGORY', categoryId);
  }

  async bulkApplySwitch(): Promise<void> {
    const chosen = this.bulkSwitches.find((s) => s.action === this.bulkSwitch());
    if (!chosen) return;
    const n = this.pickedCount();
    const ok = await this.confirmer.ask({
      title: `${chosen.label}: ${this.tickedWords()}?`,
      message: `The ${n === 1 ? 'ticked product' : n + ' ticked products'} will be ${chosen.ask}.`,
      confirmLabel: 'Yes, do it',
    });
    if (ok) this.runBulk(chosen.action);
  }

  async bulkSetChart(): Promise<void> {
    const none = this.bulkChartId() === 0;
    const ok = await this.confirmer.ask({
      title: none ? `Take the size chart off ${this.tickedWords()}?` : `Give ${this.tickedWords()} this size chart?`,
      message: none
        ? 'They will show the chart of their category, or the default chart.'
        : 'Their product pages will show the chosen chart, whatever their category says.',
      confirmLabel: 'Yes, do it',
    });
    if (ok) this.runBulk('SET_SIZE_CHART', undefined, this.bulkChartId());
  }

  async bulkDelete(): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete ${this.tickedWords()}?`,
      message: 'They are removed from the shop and from this list for good. This cannot be undone.',
      confirmLabel: `Yes, delete ${this.tickedWords()}`,
      danger: true,
    });
    if (ok) this.runBulk('DELETE');
  }

  private runBulk(action: BulkProductAction, categoryId?: number, sizeChartId?: number): void {
    if (this.pickedCount() > this.bulkLimit) {
      this.errorMessage.set(`Up to ${this.bulkLimit} products at a time. Untick some and try again.`);
      return;
    }
    this.bulkSaving.set(true);
    this.errorMessage.set('');
    this.productService.bulk(action, [...this.picks()], categoryId, sizeChartId).subscribe({
      next: (result) => {
        this.bulkSaving.set(false);
        this.notices.success(result.message);
        this.clearPicks();
        this.bulkSwitch.set(null);
        this.bulkCategoryId.set(null);
        this.bulkChartId.set(0);
        this.loadPage(this.currentPage());
      },
      error: (err) => {
        this.bulkSaving.set(false);
        // Nothing was changed: the server does all of it or none of it.
        this.errorMessage.set(parseApiError(err));
      },
    });
  }

  pickedCount(): number {
    return this.picks().size;
  }

  isPicked(productId: number): boolean {
    return this.picks().has(productId);
  }

  togglePick(productId: number): void {
    const picks = new Set(this.picks());
    if (!picks.delete(productId)) picks.add(productId);
    this.picks.set(picks);
  }

  clearPicks(): void {
    this.picks.set(new Set());
  }

  allOnPagePicked(): boolean {
    const rows = this.products();
    return rows.length > 0 && rows.every((p) => this.picks().has(p.id));
  }

  togglePageSelection(): void {
    const picks = new Set(this.picks());
    if (this.allOnPagePicked()) {
      this.products().forEach((p) => picks.delete(p.id));
    } else {
      this.products().forEach((p) => picks.add(p.id));
    }
    this.picks.set(picks);
  }

  /**
   * What a product costs to deliver, and where that comes from: its own charge,
   * its category's, or the customer's area price.
   */
  deliveryLabel(product: ProductResponse): string {
    if (product.shippingCharge != null) {
      return product.shippingCharge === 0 ? 'Free' : `৳${product.shippingCharge.toLocaleString()}`;
    }
    if (product.categoryShippingCharge != null) {
      return product.categoryShippingCharge === 0
        ? 'Free'
        : `৳${product.categoryShippingCharge.toLocaleString()}`;
    }
    return 'Area charge';
  }

  /** Why a row shows what it shows, for the cell's tooltip and its small print. */
  deliverySource(product: ProductResponse): string {
    if (product.shippingCharge != null) return 'Set on this product';
    if (product.categoryShippingCharge != null) {
      return `From ${product.categoryName ?? 'its category'}`;
    }
    return 'Inside Dhaka / Outside Dhaka';
  }

  deliveryClass(product: ProductResponse): string {
    if (product.shippingCharge != null) {
      return product.shippingCharge === 0
        ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
        : 'bg-indigo-50 text-indigo-700 border border-indigo-200';
    }
    if (product.categoryShippingCharge != null) {
      return 'bg-violet-50 text-violet-700 border border-violet-200';
    }
    return 'bg-gray-50 text-gray-500 border border-gray-200';
  }

  /** What the typed amount means, in words, before anything is applied. */
  chargePreview(): string {
    if (this.useAreaPrice()) return 'Delivery goes back to the area price — ৳60 inside Dhaka, ৳120 outside.';
    const amount = this.typedCharge();
    if (amount == null) return 'Type what delivery costs, or tick "Use the area price".';
    if (amount === 0) return 'Delivered free.';
    return `৳${amount.toLocaleString()} to deliver, whatever the customer's area.`;
  }

  /** The typed amount, or null when the box is empty or holds nonsense. */
  private typedCharge(): number | null {
    const amount = this.chargeAmount();
    if (amount == null || !Number.isFinite(amount) || amount < 0) return null;
    return Math.round(amount * 100) / 100;
  }

  async applyToPicked(): Promise<void> {
    const ids = [...this.picks()];
    if (ids.length === 0) return;
    await this.applyCharge(
      { scope: 'PRODUCTS', productIds: ids },
      `${ids.length} product${ids.length === 1 ? '' : 's'}`);
  }

  async applyToCategory(): Promise<void> {
    const categoryId = this.chargeCategoryId();
    if (!categoryId) {
      this.chargeError.set('Choose a category.');
      return;
    }
    const name = this.categories().find((c) => c.id === categoryId)?.name ?? 'this category';
    await this.applyCharge({ scope: 'CATEGORY', categoryId }, name);
  }

  async applyToWholeShop(): Promise<void> {
    await this.applyCharge({ scope: 'ALL' }, 'every product in the shop');
  }

  /**
   * Asks first, then saves. A category's charge is remembered on the category, so
   * the confirmation says products added later are covered too.
   */
  private async applyCharge(
    where: { scope: 'PRODUCTS' | 'CATEGORY' | 'ALL'; productIds?: number[]; categoryId?: number },
    what: string,
  ): Promise<void> {
    if (this.chargeSaving()) return;
    const clearing = this.useAreaPrice();
    const amount = clearing ? null : this.typedCharge();
    if (!clearing && amount == null) {
      this.chargeError.set('Type what delivery costs — 0 for free — or tick "Use the area price".');
      return;
    }

    const price = amount === 0 ? 'Free delivery' : `৳${amount?.toLocaleString()} delivery`;
    const ok = await this.confirmer.ask({
      title: clearing ? `Use the area price for ${what}?` : `${price} for ${what}?`,
      message: clearing
        ? 'Delivery goes back to ৳60 inside Dhaka and ৳120 outside.'
        : where.scope === 'CATEGORY'
          ? `Every product in ${what} is delivered at this charge, including ones you add later. A product with its own charge keeps it.`
          : `These products are delivered at this charge whatever the customer's area. An order pays the highest charge in it, once.`,
      confirmLabel: clearing ? 'Use the area price' : 'Set the charge',
      danger: where.scope === 'ALL',
    });
    if (!ok) return;

    this.chargeSaving.set(true);
    this.chargeError.set('');
    this.shippingService.setCharges({
      ...where,
      ...(clearing ? { useAreaPrice: true } : { charge: amount as number }),
    }).subscribe({
      next: (result) => {
        this.chargeSaving.set(false);
        this.notices.success('Delivery charge saved', result.message);
        this.picks.set(new Set());
        // The category's own charge may have changed, so both lists are refetched.
        this.categoryService.getCategories().subscribe({ next: (c) => this.categories.set(c), error: () => {} });
        if (where.scope === 'CATEGORY' && where.categoryId != null) {
          // Show that category, so the change is on screen: its products may all
          // be on later pages, and an unchanged list reads as a failed save.
          this.categoryId.set(where.categoryId);
          this.loadPage(0);
        } else {
          this.loadPage(this.currentPage());
        }
      },
      error: (err) => {
        this.chargeSaving.set(false);
        this.chargeError.set(parseApiError(err));
      },
    });
  }

  editProduct(id: number): void {
    this.router.navigate(['/products', id, 'edit']);
  }

  confirmDelete(id: number): void { this.deleteConfirmId.set(id); }
  cancelDelete(): void { this.deleteConfirmId.set(null); }

  deleteProduct(id: number): void {
    this.isDeleting.set(true);
    this.productService.deleteProduct(id).subscribe({
      next: () => {
        this.notices.success('Product deleted');
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
        this.loadPage(this.currentPage());
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
    });
  }
}
