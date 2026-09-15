import { inject, Injectable } from '@angular/core';
import { catchError, map, Observable, of, switchMap, tap } from 'rxjs';
import { ApiService } from './api.service';
import { ApiCartItem, ApiProduct, ApiVariant } from './api.models';
import { AuthService } from './auth.service';
import { EcommerceStore } from './store/ecommerce.store';
import { DiscountsStore } from './store/discounts.store';
import { PixelService } from './pixel.service';
import { baseProductPrice, effectiveUnitPrice, isDiscountExcluded } from './discount-pricing';

/** What happened when a product was added from a suggestion. */
export type QuickAddResult = 'added' | 'choose-size' | 'unavailable' | 'failed';

/**
 * Cart changes shared by the cart page and the cart side panel, so both keep a
 * signed-in customer's server cart in step with what they see. Guests' carts
 * live in the browser only.
 */
@Injectable({ providedIn: 'root' })
export class CartActionsService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly store = inject(EcommerceStore);
  private readonly discounts = inject(DiscountsStore);
  private readonly pixel = inject(PixelService);

  /** productId|variantId → the server's cart line id, needed to change or remove it. */
  private serverIds = new Map<string, string>();

  private key(productId: string | number, variantId: number | null | undefined): string {
    return String(productId) + '|' + (variantId ?? '');
  }

  /** Loads a signed-in customer's cart from the server; guests get an empty list. */
  refresh(): Observable<ApiCartItem[]> {
    const userId = this.auth.userId();
    if (!userId) return of([]);
    return this.api.getCart(userId).pipe(
      catchError(() => of([] as ApiCartItem[])),
      tap((items) => {
        this.store.loadApiCart(items);
        this.serverIds = new Map(items.map((i) => [this.key(i.productId, i.variantId), i.cartItemId]));
      }),
    );
  }

  increase(productId: string, variantId: number | null, size: string, current: number): void {
    this.store.updateCartQuantity(productId, size, current + 1);
    this.onServer(productId, variantId, (userId, id) => this.api.incrementCartItem(userId, id));
  }

  decrease(productId: string, variantId: number | null, size: string, current: number): void {
    if (current <= 1) {
      this.remove(productId, variantId, size);
      return;
    }
    this.store.updateCartQuantity(productId, size, current - 1);
    this.onServer(productId, variantId, (userId, id) => this.api.decrementCartItem(userId, id));
  }

  remove(productId: string, variantId: number | null, size: string): void {
    this.store.removeFromCart(productId, size);
    this.onServer(productId, variantId, (userId, id) => this.api.removeCartItem(userId, id));
  }

  /**
   * Adds one of a product from a suggestion. A product with a choice of sizes
   * can't be added blind, so the caller is told to send the customer to it.
   */
  quickAdd(p: ApiProduct): Observable<QuickAddResult> {
    if (!p.isAvailable || (p.stockQuantity != null && p.stockQuantity <= 0)) return of('unavailable');
    return this.api.getProductVariants(p.id).pipe(
      catchError(() => of([] as ApiVariant[])),
      switchMap((variants): Observable<QuickAddResult> => {
        if (variants.length > 1) return of('choose-size');
        const variant = variants[0] ?? null;
        const base = variant?.priceOverride != null ? variant.priceOverride : baseProductPrice(p.price, p.discountPrice);
        const unitPrice = effectiveUnitPrice(base, p.id, p.categoryId, this.discounts.discounts(), isDiscountExcluded(p));
        const size = variant?.size ?? 'One Size';
        this.pixel.addToCart(p.id, p.name, unitPrice, 1);

        const userId = this.auth.userId();
        if (!userId) {
          this.store.addToCart(String(p.id), size, 1, {
            variantId: variant?.id ?? null, unitPrice, name: p.name, image: p.imageUrl,
            stock: variant?.stockQuantity ?? p.stockQuantity ?? null,
          });
          return of('added');
        }
        return this.api.addToCart(userId, {
          userId, productId: String(p.id), variantId: variant?.id ?? null, quantity: 1, deliveryInstructions: null,
        }).pipe(
          tap((item) => {
            this.store.syncApiCartItem(item);
            this.serverIds.set(this.key(item.productId, item.variantId), item.cartItemId);
          }),
          map((): QuickAddResult => 'added'),
          catchError(() => of<QuickAddResult>('failed')),
        );
      }),
    );
  }

  /** Runs a server change for a signed-in customer, looking the line id up first when needed. */
  private onServer(productId: string, variantId: number | null,
                   call: (userId: string, cartItemId: string) => Observable<unknown>): void {
    const userId = this.auth.userId();
    if (!userId) return;
    const known = this.serverIds.get(this.key(productId, variantId));
    if (known) {
      call(userId, known).pipe(catchError(() => of(null))).subscribe();
      return;
    }
    // A line added since the cart was last loaded: ask the server for its id.
    this.api.getCart(userId).pipe(catchError(() => of([] as ApiCartItem[]))).subscribe((items) => {
      this.serverIds = new Map(items.map((i) => [this.key(i.productId, i.variantId), i.cartItemId]));
      const id = this.serverIds.get(this.key(productId, variantId));
      if (id) call(userId, id).pipe(catchError(() => of(null))).subscribe();
    });
  }
}
