import { Component, DestroyRef, inject, input, OnInit, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subject, catchError, debounceTime, distinctUntilChanged, forkJoin, of, switchMap } from 'rxjs';
import { ProductService } from '../../core/services/product.service';
import { CategoryService } from '../../core/services/category.service';
import { CategoryResponse, PageResponse, ProductResponse } from '../../core/models/api.models';

/** The shop search's largest page; "Select all" fetches the matches this many at a time. */
const MAX_PAGE = 60;

/**
 * Tick products for a flash sale or bundle. The server searches (name, brand,
 * description) and filters by category, 8 at a time, so the page never
 * downloads the whole catalogue.
 */
@Component({
  selector: 'app-product-multi-picker',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="mb-2 flex flex-wrap items-center justify-between gap-2">
      <span class="text-xs font-semibold text-gray-700">
        {{ label() }} <span class="font-normal text-gray-400">({{ selected().length }} selected)</span>
      </span>
      <div class="flex items-center gap-2">
        <button type="button" (click)="selectAllMatching()" [disabled]="selectingAll() || total() === 0"
          class="rounded-lg border border-gray-200 bg-white px-2.5 py-1 text-xs font-medium text-gray-700 hover:border-blue-300 hover:bg-blue-50 hover:text-blue-700 disabled:opacity-50">
          {{ selectingAll() ? 'Selecting…' : 'Select all (filtered)' }}
        </button>
        <button type="button" (click)="selectedChange.emit([])"
          class="rounded-lg border border-gray-200 bg-white px-2.5 py-1 text-xs font-medium text-gray-600 hover:border-red-300 hover:bg-red-50 hover:text-red-600">
          Clear
        </button>
      </div>
    </div>
    <div class="mb-2 flex flex-col gap-2 sm:flex-row">
      <input type="search" [ngModel]="search()" (ngModelChange)="onSearch($event)"
        [ngModelOptions]="{ standalone: true }" placeholder="Search products…" aria-label="Search products"
        class="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
      <select [ngModel]="categoryFilter()" (ngModelChange)="onCategory($event)"
        [ngModelOptions]="{ standalone: true }" aria-label="Category"
        class="rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm sm:w-52">
        <option value="all">All categories</option>
        @for (c of categories(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
      </select>
    </div>
    <div class="rounded-lg border border-gray-200 divide-y divide-gray-50" [class.opacity-60]="loading()">
      @for (p of products(); track p.id) {
        <label class="flex items-center gap-3 px-3 py-2 text-sm hover:bg-gray-50 cursor-pointer">
          <input type="checkbox" [checked]="isSelected(p.id)" (change)="toggle(p.id)" />
          @if (p.imageUrl) { <img [src]="p.imageUrl" [alt]="p.name" class="h-8 w-8 rounded object-cover" /> }
          <span class="flex-1">{{ p.name }}</span>
          <span class="text-xs text-gray-400">৳{{ p.price }}</span>
        </label>
      } @empty {
        <p class="px-3 py-4 text-center text-xs text-gray-400">{{ loading() ? 'Loading…' : 'No products match.' }}</p>
      }
    </div>
    @if (totalPages() > 1) {
      <div class="mt-2 flex items-center justify-between text-xs text-gray-500">
        <button type="button" (click)="goTo(page() - 1)" [disabled]="page() === 0 || loading()"
          class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Prev</button>
        <span>Page {{ page() + 1 }} / {{ totalPages() }} · {{ total() }} products</span>
        <button type="button" (click)="goTo(page() + 1)" [disabled]="page() >= totalPages() - 1 || loading()"
          class="rounded-lg border border-gray-200 px-2.5 py-1 hover:bg-gray-50 disabled:opacity-40">Next</button>
      </div>
    }
  `,
})
export class ProductMultiPickerComponent implements OnInit {
  private readonly productApi = inject(ProductService);
  private readonly categoryApi = inject(CategoryService);
  private readonly destroyRef = inject(DestroyRef);

  readonly label = input('Products');
  readonly selected = input<number[]>([]);
  readonly selectedChange = output<number[]>();

  private readonly pageSize = 8;
  readonly search = signal('');
  readonly categoryFilter = signal('all');
  readonly page = signal(0);
  readonly products = signal<ProductResponse[]>([]);
  readonly total = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(false);
  readonly selectingAll = signal(false);
  readonly categories = signal<CategoryResponse[]>([]);

  private readonly search$ = new Subject<string>();
  /** Each emission loads the current page; switchMap drops an answer a newer request has overtaken. */
  private readonly load$ = new Subject<void>();

  ngOnInit(): void {
    this.load$.pipe(
      switchMap(() => {
        this.loading.set(true);
        return this.productApi.browse(this.search().trim(), this.page(), this.pageSize, this.categoryId())
          .pipe(catchError(() => of(null)));
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((result) => {
      this.loading.set(false);
      if (!result) return;
      this.products.set(result.content);
      this.total.set(result.totalElements);
      this.totalPages.set(result.totalPages);
    });
    this.search$.pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reset());
    this.categoryApi.getCategories()
      .pipe(catchError(() => of([] as CategoryResponse[])))
      .subscribe((list) => this.categories.set([...list].sort((a, b) => a.name.localeCompare(b.name))));
    this.load$.next();
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.search$.next(q);
  }

  protected onCategory(value: string): void {
    this.categoryFilter.set(value);
    this.reset();
  }

  protected goTo(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.page.set(p);
    this.load$.next();
  }

  protected isSelected(id: number): boolean {
    return this.selected().includes(id);
  }

  protected toggle(id: number): void {
    const ids = this.selected();
    this.selectedChange.emit(ids.includes(id) ? ids.filter((x) => x !== id) : [...ids, id]);
  }

  /** Adds every product matching the search and category, not just the page on screen. */
  protected selectAllMatching(): void {
    const q = this.search().trim();
    const category = this.categoryId();
    this.selectingAll.set(true);
    this.productApi.browse(q, 0, MAX_PAGE, category).pipe(
      switchMap((first) => first.totalPages <= 1 ? of([first]) : forkJoin([
        of(first),
        ...Array.from({ length: first.totalPages - 1 }, (_, i) => this.productApi.browse(q, i + 1, MAX_PAGE, category)),
      ])),
      catchError(() => of([] as PageResponse<ProductResponse>[])),
    ).subscribe((pages) => {
      const ids = new Set(this.selected());
      pages.forEach((p) => p.content.forEach((x) => ids.add(x.id)));
      this.selectedChange.emit([...ids]);
      this.selectingAll.set(false);
    });
  }

  private categoryId(): number | null {
    const value = this.categoryFilter();
    return value === 'all' ? null : Number(value);
  }

  private reset(): void {
    this.page.set(0);
    this.load$.next();
  }
}
