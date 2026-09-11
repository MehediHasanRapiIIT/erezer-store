import { Component, signal, computed, inject, OnDestroy, OnInit } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';
import { PermissionService } from '../../../core/services/permission.service';

/** The Categories page. Search and paging are done by the server. */
@Component({
  selector: 'app-categories-list',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent],
  templateUrl: './categories-list.component.html',
})
export class CategoriesListComponent implements OnInit, OnDestroy {
  private categoryService = inject(CategoryService);
  private router = inject(Router);
  protected readonly perms = inject(PermissionService);

  searchQuery = signal('');
  private searchSubject = new Subject<string>();
  /** 1-based, for the page buttons; the server's pages start at 0. */
  currentPage = signal(1);
  readonly pageSize = 10;

  /** The page on screen. */
  categories = signal<CategoryResponse[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  isLoading = signal(false);
  errorMessage = signal('');
  deleteConfirmId = signal<number | null>(null);
  isDeleting = signal(false);
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  pageNumbers = computed(() => {
    const total = this.totalPages();
    const current = this.currentPage();
    if (total <= 5) return Array.from({ length: total }, (_, i) => i + 1);
    const pages: number[] = [];
    if (current > 2) pages.push(1);
    if (current > 3) pages.push(-1);
    for (let i = Math.max(1, current - 1); i <= Math.min(total, current + 1); i++) pages.push(i);
    if (current < total - 2) pages.push(-1);
    if (current < total - 1) pages.push(total);
    return [...new Set(pages)];
  });

  ngOnInit(): void {
    this.loadPage(1);
    this.searchSubject.pipe(debounceTime(300), distinctUntilChanged()).subscribe(() => this.loadPage(1));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  onSearchChange(query: string): void {
    this.searchQuery.set(query);
    this.searchSubject.next(query);
  }

  loadCategories(): void {
    this.loadPage(this.currentPage());
  }

  /** One page from the server (1-based), for the current search. */
  loadPage(page: number): void {
    const request = ++this.latestRequest;
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.categoryService.getCategoriesPage(this.searchQuery().trim(), page - 1, this.pageSize).subscribe({
      next: (data) => {
        if (request !== this.latestRequest) return;
        // The last category on the last page was just deleted: show the page before.
        if (data.content.length === 0 && page > 1) {
          this.loadPage(page - 1);
          return;
        }
        this.categories.set(data.content);
        this.totalElements.set(data.totalElements);
        this.totalPages.set(data.totalPages);
        this.currentPage.set(data.number + 1);
        this.isLoading.set(false);
      },
      error: (err) => {
        if (request !== this.latestRequest) return;
        this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  setPage(p: number) {
    if (p >= 1 && p <= this.totalPages()) this.loadPage(p);
  }

  minVal(a: number, b: number): number {
    return Math.min(a, b);
  }

  iconBgClass(name: string): string {
    const map: Record<string, string> = {
      groceries: 'bg-green-500', fruits: 'bg-lime-500', vegetables: 'bg-emerald-500',
      meat: 'bg-red-500', fish: 'bg-blue-400', dairy: 'bg-blue-500',
      bakery: 'bg-pink-500', spices: 'bg-orange-500', oils: 'bg-yellow-500',
      grains: 'bg-amber-500', pulses: 'bg-stone-500', beverages: 'bg-cyan-500',
      electronics: 'bg-indigo-500', fashion: 'bg-purple-500', home: 'bg-teal-500',
      seasonal: 'bg-gray-500',
    };
    const key = name.toLowerCase().split(' ')[0];
    return map[key] ?? 'bg-blue-500';
  }

  toggleActive(cat: CategoryResponse): void {
    // The server replaces every field of a category on save, so the others go
    // back unchanged; leaving them out would wipe the image, the home-page
    // section and "Never discount".
    this.categoryService.updateCategory(cat.id, {
      name: cat.name,
      isActive: !cat.isActive,
      imageUrl: cat.imageUrl ?? null,
      slug: cat.slug ?? null,
      showOnHome: !!cat.showOnHome,
      homeSortOrder: cat.homeSortOrder ?? 0,
      discountExcluded: !!cat.discountExcluded,
    }).subscribe({
      next: (updated) => {
        this.categories.update(list =>
          list.map(c => c.id === cat.id ? { ...c, isActive: updated.isActive } : c)
        );
      },
      error: (err) => this.errorMessage.set(parseApiError(err)),
    });
  }

  editCategory(id: number) {
    this.router.navigate(['/categories', id, 'edit']);
  }

  confirmDelete(id: number) {
    this.deleteConfirmId.set(id);
  }

  cancelDelete() {
    this.deleteConfirmId.set(null);
  }

  deleteCategory(id: number) {
    this.isDeleting.set(true);
    this.categoryService.deleteCategory(id).subscribe({
      next: () => {
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
