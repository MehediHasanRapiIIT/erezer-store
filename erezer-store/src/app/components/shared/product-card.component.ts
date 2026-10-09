import { CurrencyPipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
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
      <!-- Image: a picture of any shape fills the card.

           The frame is one shape on every card (4:5, upright like a garment
           photo), so rows line up. The whole picture is shown inside it —
           "contain", never "cover", which filled the frame by cutting the sides
           or the top off. Whatever the picture doesn't reach is filled by a soft,
           blurred copy of the same picture behind it, so a tall or a wide photo
           never leaves an empty band and nothing of it is lost. -->
      <div class="relative aspect-[4/5] overflow-hidden bg-neutral-100 dark:bg-neutral-900" data-testid="card-frame">
        <a [routerLink]="['/product', product().slug]" class="absolute inset-0 block">
          <img [src]="product().image" alt="" aria-hidden="true" loading="lazy" data-testid="card-backdrop"
            class="absolute inset-0 h-full w-full scale-125 object-cover opacity-70 blur-xl" />
          <img
            [src]="product().image"
            [alt]="product().name"
            data-testid="card-image"
            class="relative h-full w-full object-contain transition-opacity duration-500"
            [class.group-hover:opacity-0]="product().hoverImage"
          />
          @if (product().hoverImage; as hover) {
            <span class="absolute inset-0 opacity-0 transition-opacity duration-500 group-hover:opacity-100" aria-hidden="true">
              <img [src]="hover" alt="" loading="lazy" class="absolute inset-0 h-full w-full scale-125 object-cover opacity-70 blur-xl" />
              <img [src]="hover" alt="" class="relative h-full w-full object-contain" />
            </span>
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

        <!-- slide-up add to cart: always visible on a tablet, hover-reveal on desktop.
             Not on a phone, where it covered a third of a small picture: there the
             "Add to Cart" button is the last line of the card (below). -->
        <div class="absolute inset-x-0 bottom-0 hidden p-2 transition-all duration-300 sm:block sm:p-3 md:translate-y-full md:opacity-0 md:group-hover:translate-y-0 md:group-hover:opacity-100">
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
        <div class="flex items-start justify-between gap-2 sm:gap-3">
          <a [routerLink]="['/product', product().slug]" class="min-w-0 flex-1 sm:flex-initial line-clamp-2 min-h-[2.5rem] text-sm font-semibold leading-5 tracking-tight underline-offset-4 hover:underline sm:line-clamp-none sm:min-h-0 sm:text-base sm:leading-normal">
            {{ product().name }}
          </a>
          <!-- The regular price hangs under the price without taking a line of
               its own, so cards on sale and at full price keep their rows level. -->
          <span class="relative hidden shrink-0 text-right sm:block">
            <span class="block text-base font-semibold" data-testid="card-price-wide">{{ product().price | currency:'BDT':'৳' }}</span>
            @if (product().originalPrice; as was) {
              <span class="absolute right-0 top-full mt-1 whitespace-nowrap text-xs text-neutral-500 line-through dark:text-neutral-400" data-testid="card-was-wide">{{ was | currency:'BDT':'৳' }}</span>
            }
          </span>
        </div>
        <p class="flex flex-wrap items-baseline gap-x-1.5 sm:hidden">
          <span class="text-sm font-semibold" data-testid="card-price-phone">{{ product().price | currency:'BDT':'৳' }}</span>
          @if (product().originalPrice; as was) {
            <span class="text-xs text-neutral-500 line-through dark:text-neutral-400" data-testid="card-was-phone">{{ was | currency:'BDT':'৳' }}</span>
          }
        </p>
        <!-- Tablet and desktop: one spare line under the name. The crossed-out
             regular price hangs into its right end, and after "add to cart" on
             this card a short way to the checkout appears here, stopping short
             of that price. The line is always there, so the card keeps its
             height and the row stays level. (No rating here: the product page
             has the reviews.) -->
        <div class="hidden min-h-5 items-center justify-end gap-2 sm:flex" [class.sm:pr-20]="added() && !!product().originalPrice">
          @if (added()) {
            <a routerLink="/checkout" data-testid="card-checkout"
              class="animate-overlay-in -my-1 inline-flex shrink-0 items-center gap-1 rounded-full bg-emerald-600 px-2.5 py-1 text-[11px] font-semibold leading-4 text-white shadow-sm transition hover:bg-emerald-700 sm:px-3 sm:text-xs">
              <span class="sr-only">Added to cart.</span>
              Checkout
              <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5L21 12m0 0l-7.5 7.5M21 12H3" /></svg>
            </a>
          }
        </div>
        <!-- Phone: no rating line; in its place a proper "Add to Cart" button,
             joined after the first add by the way to the checkout. Both states
             are one line of the same height, so the rows of cards stay level. -->
        <div class="flex items-stretch gap-1.5 pt-0.5 sm:hidden">
          <button
            type="button"
            (click)="quickAddToCart()"
            [disabled]="product().inStock <= 0"
            data-testid="card-cart"
            class="h-9 min-w-0 flex-1 truncate rounded-full bg-neutral-900 px-2 text-xs font-semibold text-white transition active:scale-95 disabled:opacity-40 dark:bg-white dark:text-black"
          >
            {{ product().inStock <= 0 ? 'Sold out' : added() ? '+ Add' : 'Add to Cart' }}
          </button>
          @if (added()) {
            <a routerLink="/checkout" data-testid="card-checkout-phone"
              class="animate-overlay-in inline-flex h-9 min-w-0 flex-1 items-center justify-center truncate rounded-full bg-emerald-600 px-2 text-xs font-semibold text-white transition active:scale-95">
              <span class="sr-only">Added to cart.</span>
              Checkout
            </a>
          }
        </div>
      </div>
    </article>
  `
})
export class ProductCardComponent {
  protected readonly store = inject(EcommerceStore);
  readonly product = input.required<Product>();
  /** This card's product was just put in the cart: offer the way to the checkout. */
  protected readonly added = signal(false);

  protected quickAddToCart(): void {
    const selected = this.product();
    if (selected.inStock <= 0) return;
    this.store.addToCart(selected.id, selected.sizes[0], 1);
    this.added.set(true);
  }
}
