import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { RouterLink, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../../shared/sidebar/sidebar.component';
import { CategoryService } from '../../../core/services/category.service';
import { CategoryResponse } from '../../../core/models/api.models';
import { parseApiError } from '../../../core/utils/api-error.util';

@Component({
  selector: 'app-categories-list',
  standalone: true,
  imports: [RouterLink, FormsModule, SidebarComponent],
  templateUrl: './categories-list.component.html',
})
export class CategoriesListComponent implements OnInit {
  private categoryService = inject(CategoryService);
  private router = inject(Router);

  searchQuery = signal('');
  currentPage = signal(1);
  readonly pageSize = 5;

  categories = signal<CategoryResponse[]>([]);
  isLoading = signal(false);
  errorMessage = signal('');
  deleteConfirmId = signal<number | null>(null);
  isDeleting = signal(false);

  filtered = computed(() => {
    const q = this.searchQuery().toLowerCase();
    if (!q) return this.categories();
    return this.categories().filter((c) => c.name.toLowerCase().includes(q));
  });

  paginated = computed(() => {
    const start = (this.currentPage() - 1) * this.pageSize;
    return this.filtered().slice(start, start + this.pageSize);
  });

  totalPages = computed(() => Math.ceil(this.filtered().length / this.pageSize));

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
    this.loadCategories();
  }

  loadCategories(): void {
    this.isLoading.set(true);
    this.errorMessage.set('');
    this.categoryService.getCategories().subscribe({
      next: (data) => {
        this.categories.set(data);
        this.isLoading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.isLoading.set(false);
      },
    });
  }

  setPage(p: number) {
    if (p >= 1 && p <= this.totalPages()) this.currentPage.set(p);
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
    this.categoryService.updateCategory(cat.id, {
      name: cat.name,
      isActive: !cat.isActive,
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
        this.categories.update((list) => list.filter((c) => c.id !== id));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
    });
  }
}
