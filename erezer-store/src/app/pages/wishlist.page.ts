import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProductCardComponent } from '../components/shared/product-card.component';
import { EcommerceStore } from '../core/store/ecommerce.store';

@Component({
  standalone: true,
  imports: [RouterLink, ProductCardComponent],
  template: `
    <section class="space-y-6">
      <header class="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 class="app-section-title">Wishlist</h1>
          <p class="app-muted mt-1">Your saved favorites, ready when you are.</p>
        </div>
        <a routerLink="/shop" class="text-sm underline underline-offset-4">Continue shopping</a>
      </header>

      <section class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        @for (product of store.wishlistProducts(); track product.id) {
          <app-product-card [product]="product" />
        } @empty {
          <article class="app-card p-6">
            <p class="app-muted">No items in wishlist yet. Save products to revisit them later.</p>
          </article>
        }
      </section>
    </section>
  `
})
export class WishlistPage {
  protected readonly store = inject(EcommerceStore);
}
