import { Component, signal, computed, inject, OnInit } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ReviewService } from '../../core/services/review.service';
import { ProductService } from '../../core/services/product.service';
import { PageResponse, ProductResponse, ReviewResponse } from '../../core/models/api.models';
import { parseApiError } from '../../core/utils/api-error.util';

@Component({
  selector: 'app-reviews',
  standalone: true,
  imports: [FormsModule, SidebarComponent, DecimalPipe],
  templateUrl: './reviews.component.html',
})
export class ReviewsComponent implements OnInit {
  private reviewService = inject(ReviewService);
  private productService = inject(ProductService);

  // Product selector
  products = signal<ProductResponse[]>([]);
  selectedProductId = signal<number | null>(null);
  productSearch = signal('');

  // Reviews
  reviews = signal<ReviewResponse[]>([]);
  totalElements = signal(0);
  totalPages = signal(0);
  currentPage = signal(0);
  readonly pageSize = 10;

  isLoadingProducts = signal(true);
  isLoadingReviews = signal(false);
  errorMessage = signal('');

  // Delete confirm
  deleteConfirmId = signal<string | null>(null);
  isDeleting = signal(false);

  // Rating summary
  avgRating = signal(0);
  totalReviews = signal(0);
  starBreakdown = signal<{ [key: number]: number }>({});

  filteredProducts = computed(() => {
    const q = this.productSearch().toLowerCase();
    if (!q) return this.products();
    return this.products().filter(p => p.name.toLowerCase().includes(q));
  });

  selectedProduct = computed(() =>
    this.products().find(p => p.id === this.selectedProductId()) ?? null
  );

  ngOnInit(): void {
    this.productService.getProducts().subscribe({
      next: (data) => { this.products.set(data); this.isLoadingProducts.set(false); },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.isLoadingProducts.set(false); },
    });
  }

  selectProduct(id: number): void {
    this.selectedProductId.set(id);
    this.currentPage.set(0);
    this.loadReviews();
    this.loadSummary();
  }

  loadReviews(): void {
    const id = this.selectedProductId();
    if (!id) return;
    this.isLoadingReviews.set(true);
    this.errorMessage.set('');
    this.reviewService.getReviews(id, this.currentPage(), this.pageSize).subscribe({
      next: (page: PageResponse<ReviewResponse>) => {
        this.reviews.set(page.content);
        this.totalElements.set(page.totalElements);
        this.totalPages.set(page.totalPages);
        this.isLoadingReviews.set(false);
      },
      error: (err) => { this.errorMessage.set(parseApiError(err)); this.isLoadingReviews.set(false); },
    });
  }

  loadSummary(): void {
    const id = this.selectedProductId();
    if (!id) return;
    this.reviewService.getRatingSummary(id).subscribe({
      next: (s) => {
        this.avgRating.set(s.avgRating);
        this.totalReviews.set(s.totalReviews);
        this.starBreakdown.set(s.starBreakdown);
      },
      error: () => {},
    });
  }

  setPage(p: number): void {
    if (p < 0 || p >= this.totalPages()) return;
    this.currentPage.set(p);
    this.loadReviews();
  }

  confirmDelete(reviewId: string): void {
    this.deleteConfirmId.set(reviewId);
  }

  cancelDelete(): void {
    this.deleteConfirmId.set(null);
  }

  deleteReview(reviewId: string): void {
    const productId = this.selectedProductId();
    if (!productId) return;
    this.isDeleting.set(true);
    this.reviewService.deleteReview(productId, reviewId).subscribe({
      next: () => {
        this.reviews.update(list => list.filter(r => r.reviewId !== reviewId));
        this.totalElements.update(n => n - 1);
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
        this.loadSummary();
      },
      error: (err) => {
        this.errorMessage.set(parseApiError(err));
        this.deleteConfirmId.set(null);
        this.isDeleting.set(false);
      },
    });
  }

  starPercent(star: number): number {
    const total = this.totalReviews();
    if (!total) return 0;
    return Math.round(((this.starBreakdown()[star] ?? 0) / total) * 100);
  }

  starArray(rating: number): number[] {
    return Array.from({ length: 5 }, (_, i) => i + 1);
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric' });
  }

  pageNumbers(): number[] {
    const total = this.totalPages();
    const current = this.currentPage();
    if (total <= 7) return Array.from({ length: total }, (_, i) => i);
    const pages: number[] = [];
    if (current > 1) pages.push(0);
    if (current > 2) pages.push(-1);
    for (let i = Math.max(0, current - 1); i <= Math.min(total - 1, current + 1); i++) pages.push(i);
    if (current < total - 3) pages.push(-1);
    if (current < total - 2) pages.push(total - 1);
    return [...new Set(pages)];
  }
}
