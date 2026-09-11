import { Component, signal, computed, inject, OnInit, OnDestroy } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, takeUntil } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { ProductService } from '../../../core/services/product.service';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse, ProductResponse } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { salePercent } from '../../../core/utils/price.util';
import { PermissionService } from '../../../core/services/permission.service';
import { NoticeService } from '../../../core/services/notice.service';
import { ACCESS } from '../../../core/access/admin-pages';

/**
 * The Products page. Search, the category filter and paging are all done by
 * the server, so a search covers every product, not just the page on screen.
 */
@Component({
  selector: 'app-products-list',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent],
  templateUrl: './products-list.component.html',
})
export class ProductsListComponent implements OnInit, OnDestroy {
  private productService = inject(ProductService);
  private readonly notices = inject(NoticeService);
  private categoryService = inject(CategoryService);
  private router = inject(Router);
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
