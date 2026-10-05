import { CurrencyPipe } from '@angular/common';
import { Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Product } from '../../core/models';
import { EcommerceStore } from '../../core/store/ecommerce.store';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, CurrencyPipe],
  // A grid stretches this element to the height of its row; the card inside has
  // to be told to fill it, or a card with a shorter name stops short of its
  // neighbours and leaves a gap underneath.
  host: { class: 'flex flex-col' },
  template: `
    <article class="app-card group flex-1 overflow-hidden transition-all duration-300 hover:-translate-y-1.5 hover:shadow-2xl hover:shadow-black/10 dark:hover:shadow-black/40">
      <!-- Image -->
      <div class="relative overflow-hidden">
        <a [routerLink]="['/product', product().slug]" class="block">
          <img
            [src]="product().image"
            [alt]="product().name"
            class="h-52 w-full object-cover transition-transform duration-[800ms] ease-out group-hover:scale-105 sm:h-80"
            [class.group-hover:opacity-0]="product().hoverImage"
          />
          @if (product().hoverImage; as hover) {
            <img
              [src]="hover"
              [alt]="product().name"
              aria-hidden="true"
              class="absolute inset-0 h-52 w-full object-cover opacity-0 transition-opacity duration-500 group-hover:opacity-100 sm:h-80"
            />
          }
        </a>

        <!-- hover scrim -->
        <div class="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/35 via-transparent to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100"></div>

        <!-- badges -->
        @if (product().isFeatured && product().inStock > 0) {
          <span class="absolute left-2 top-2 rounded-full bg-black px-2 py-0.5 text-[10px] font-semibold sm:left-3 sm:top-3 sm:px-2.5 sm:py-1 sm:text-xs text-white dark:bg-white dark:text-black">Featured</span>
        }
        @if (product().inStock <= 0) {
          <span class="absolute left-2 top-2 rounded-full bg-red-600 px-2 py-0.5 text-[10px] font-semibold sm:left-3 sm:top-3 sm:px-2.5 sm:py-1 sm:text-xs text-white">Sold out</span>
        }

        <!-- wishlist -->
        <button
          type="button"
          (click)="store.toggleWishlist(product().id)"
          [attr.aria-label]="store.isWishlisted(product().id) ? 'Remove from wishlist' : 'Add to wishlist'"
          class="absolute right-2 top-2 inline-flex h-8 w-8 items-center justify-center rounded-full sm:right-3 sm:top-3 sm:h-9 sm:w-9 bg-white/90 text-neutral-900 shadow-sm backdrop-blur transition hover:scale-110 dark:bg-neutral-900/80 dark:text-white"
        >
          <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 sm:h-5 sm:w-5" viewBox="0 0 24 24"
            [attr.fill]="store.isWishlisted(product().id) ? 'currentColor' : 'none'"
            stroke="currentColor" stroke-width="1.6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M21 8.25c0-2.485-2.099-4.5-4.688-4.5-1.935 0-3.597 1.126-4.312 2.733-.715-1.607-2.377-2.733-4.313-2.733C5.1 3.75 3 5.765 3 8.25c0 7.22 9 12 9 12s9-4.78 9-12z" />
          </svg>
        </button>

        <!-- slide-up add to cart: always visible on touch, hover-reveal on desktop -->
        <div class="absolute inset-x-0 bottom-0 p-2 transition-all duration-300 sm:p-3 md:translate-y-full md:opacity-0 md:group-hover:translate-y-0 md:group-hover:opacity-100">
          <button
            type="button"
            (click)="quickAddToCart()"
            [disabled]="product().inStock <= 0"
            class="w-full rounded-full bg-white px-2 py-2 text-xs font-semibold text-black sm:px-4 sm:py-2.5 sm:text-sm shadow-lg transition hover:bg-neutral-100 disabled:opacity-50 dark:bg-neutral-100"
          >
            {{ product().inStock <= 0 ? 'Sold out' : 'Add to cart' }}
          </button>
        </div>
      </div>

      <!-- Body -->
      <div class="space-y-1.5 p-3 sm:space-y-2 sm:p-5">
        <p class="truncate text-[10px] font-medium uppercase tracking-[0.14em] text-neutral-500 dark:text-neutral-400 sm:text-[11px] sm:tracking-[0.18em]">
          {{ product().category }}
        </p>
        <div class="flex flex-col gap-1 sm:flex-row sm:items-start sm:justify-between sm:gap-3">
          <a [routerLink]="['/product', product().slug]" class="line-clamp-2 min-h-[2.5rem] text-sm font-semibold leading-5 tracking-tight underline-offset-4 hover:underline sm:line-clamp-none sm:min-h-0 sm:text-base sm:leading-normal">
            {{ product().name }}
          </a>
          <span class="shrink-0 text-sm font-semibold sm:text-base">{{ product().price | currency:'BDT':'৳' }}</span>
        </div>
        <p class="text-xs text-neutral-500 dark:text-neutral-400 sm:text-sm">
          {{ store.getAverageRating(product()).toFixed(1) }} / 5 · {{ store.getReviewCount(product().id) }} reviews
        </p>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  protected readonly store = inject(EcommerceStore);
  readonly product = input.required<Product>();

  protected quickAddToCart(): void {
    const selected = this.product();
    if (selected.inStock <= 0) return;
    this.store.addToCart(selected.id, selected.sizes[0], 1);
  }
}
