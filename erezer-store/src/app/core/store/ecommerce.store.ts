import { computed, effect, inject, Injectable, signal } from '@angular/core';
import { CartItem, Order, Product, Review, UserProfile } from '../models';
import { ApiCartItem, ApiProduct } from '../api.models';
import { PRODUCTS } from '../mock-data';
import { DiscountsStore } from './discounts.store';
import { baseProductPrice } from '../discount-pricing';

/** localStorage key for the persisted cart so it survives a page refresh. */
const CART_STORAGE_KEY = 'erezer-cart';

function readStoredCart(): CartItem[] {
  try {
    const raw = localStorage.getItem(CART_STORAGE_KEY);
    return raw ? (JSON.parse(raw) as CartItem[]) : [];
  } catch {
    return [];
  }
}

function persistCart(items: CartItem[]): void {
  try { localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(items)); } catch { /* ignore */ }
}

@Injectable({ providedIn: 'root' })
export class EcommerceStore {
  private readonly discountsStore = inject(DiscountsStore);

  /** Last API product payload, kept so cards can re-price when discounts load. */
  private lastApiProducts: ApiProduct[] = [];

  constructor() {
    // When active discounts arrive (or change), re-price the seeded products so
    // card prices reflect automatic discounts without a manual refresh.
    effect(() => {
      this.discountsStore.discounts();
      if (this.lastApiProducts.length > 0) {
        this.seedApiProducts(this.lastApiProducts);
      }
    });

    // Persist the cart on every change so a refresh (e.g. after email
    // verification) doesn't lose the customer's items.
    effect(() => persistCart(this.cart()));
  }
  // ── catalog ────────────────────────────────────────────────────────────────
  readonly products  = signal<Product[]>(PRODUCTS);
  readonly query     = signal('');
  readonly category  = signal<'All' | Product['category']>('All');
  readonly sortBy    = signal<'featured' | 'price-asc' | 'price-desc' | 'rating'>('featured');

  // ── cart / wishlist ────────────────────────────────────────────────────────
  // Rehydrate from localStorage so the cart survives a page refresh.
  readonly cart     = signal<CartItem[]>(readStoredCart());
  readonly wishlist = signal<string[]>([]);
  readonly promoCode = signal('');

  // ── reviews / orders / profile ─────────────────────────────────────────────
  readonly reviews = signal<Record<string, Review[]>>({});
  readonly orders  = signal<Order[]>([]);
  readonly profile = signal<UserProfile>({
    name: 'Guest Customer',
    email: 'guest@erezer.com',
    address: '',
    city: '',
    country: ''
  });

  readonly isAuthenticated = signal(false);

  // ── derived: filtered + sorted products ───────────────────────────────────
  readonly filteredProducts = computed(() => {
    const q = this.query().trim().toLowerCase();
    const reviewsByProduct = this.reviews();

    const avgWithReviews = (product: Product): number => {
      const rs = reviewsByProduct[product.id] ?? [];
      if (rs.length === 0) return product.rating;
      return rs.reduce((acc, r) => acc + r.rating, product.rating) / (rs.length + 1);
    };

    const filtered = this.products().filter((p) => {
      const catMatch = this.category() === 'All' || p.category === this.category();
      const qMatch   = q.length === 0 || p.name.toLowerCase().includes(q) || p.description.toLowerCase().includes(q);
      return catMatch && qMatch;
    });

    if (this.sortBy() === 'price-asc')  return [...filtered].sort((a, b) => a.price - b.price);
    if (this.sortBy() === 'price-desc') return [...filtered].sort((a, b) => b.price - a.price);
    if (this.sortBy() === 'rating')     return [...filtered].sort((a, b) => avgWithReviews(b) - avgWithReviews(a));
    return [...filtered].sort((a, b) => Number(Boolean(b.isFeatured)) - Number(Boolean(a.isFeatured)));
  });

  readonly featuredProducts = computed(() => this.products().filter((p) => p.isFeatured));

  // ── cart derived ───────────────────────────────────────────────────────────
  // Each cart item is self-contained (name/image/unitPrice), so the row renders
  // even when products() doesn't hold that product. We still merge any catalog
  // product we *do* have for richer fields, but item data wins for price/name/image.
  readonly cartItemsDetailed = computed(() =>
    this.cart().map((item) => {
      const catalog = this.products().find((p) => p.id === item.productId);
      const product: Product = {
        id:          item.productId,
        slug:        catalog?.slug ?? item.productId,
        name:        item.name || catalog?.name || 'Product',
        description: catalog?.description ?? '',
        price:       item.unitPrice,
        image:       item.image ?? catalog?.image ?? '',
        category:    catalog?.category ?? 'Tops',
        sizes:       catalog?.sizes ?? [item.size],
        rating:      catalog?.rating ?? 0,
        isFeatured:  catalog?.isFeatured ?? false,
        inStock:     catalog?.inStock ?? 99,
      };
      return { ...item, product, subtotal: item.unitPrice * item.quantity };
    })
  );

  readonly cartCount    = computed(() => this.cart().reduce((acc, i) => acc + i.quantity, 0));
  readonly cartSubtotal = computed(() => this.cartItemsDetailed().reduce((acc, i) => acc + i.subtotal, 0));
  readonly shippingFee  = computed(() => (this.cartSubtotal() > 150 ? 0 : 12));
  readonly discountAmount = computed(() => {
    const code = this.promoCode().trim().toUpperCase();
    if (code === 'WELCOME10') return this.cartSubtotal() * 0.1;
    if (code === 'FREESHIP')  return this.shippingFee();
    return 0;
  });
  readonly cartTotal = computed(() =>
    Math.max(0, this.cartSubtotal() + this.shippingFee() - this.discountAmount())
  );

  readonly wishlistProducts = computed(() =>
    this.wishlist()
      .map((id) => this.products().find((p) => p.id === id))
      .filter((p): p is Product => Boolean(p))
  );

  readonly deliveryStatusSteps: Order['delivery']['status'][] = [
    'Processing', 'Packed', 'Shipped', 'Out for Delivery', 'Delivered'
  ];

  // ── API integration helpers ────────────────────────────────────────────────

  /**
   * Convert an ApiProduct (from backend) into the local Product shape
   * so existing components (ProductCard etc.) work without changes.
   */
  toStoreProduct(api: ApiProduct): Product {
    return {
      id:          String(api.id),
      slug:        String(api.id),          // use numeric id as slug for routing
      name:        api.name,
      description: api.description,
      // Effective price = base price (sale-or-regular) reduced by any automatic
      // discount. Uses the SAME base rule as the product page so they agree.
      price:       this.discountsStore.effectivePrice(
                     baseProductPrice(api.price, api.discountPrice), api.id, api.categoryId),
      image:       api.imageUrl,
      category:    'Tops',                  // API has no category string; use categoryId mapping if needed
      sizes:       ['One Size'],
      rating:      4.5,
      isFeatured:  false,
      inStock:     api.isAvailable ? 99 : 0
    };
  }

  /**
   * Seed the product list from API data (replaces mock data).
   * Preserves any existing local products not in the API response.
   */
  seedApiProducts(apiProducts: ApiProduct[]): void {
    this.lastApiProducts = apiProducts;
    const converted = apiProducts.map((p) => this.toStoreProduct(p));
    // deduplicate by id
    const existingIds = new Set(converted.map((p) => p.id));
    const kept = this.products().filter((p) => !existingIds.has(p.id));
    this.products.set([...converted, ...kept]);
  }

  /** Convert a backend cart item into the self-contained local CartItem. */
  private fromApiCartItem(i: ApiCartItem): CartItem {
    const label = i.productName?.includes('—')
      ? i.productName.split('—').pop()!.trim()
      : 'One Size';
    return {
      productId: String(i.productId),
      variantId: i.variantId ?? null,
      quantity:  i.quantity,
      size:      label,
      unitPrice: i.unitPrice,
      name:      i.productName,
      image:     i.imageUrl,
    };
  }

  /**
   * Replace the local cart with items fetched from the API.
   */
  loadApiCart(apiItems: ApiCartItem[]): void {
    // Custom (made-to-order) lines live only in the local cart (the server cart
    // doesn't model measurements) — preserve them across a server refresh.
    const customLocal = this.cart().filter((i) => i.customMeasurements);
    this.cart.set([...apiItems.map((i) => this.fromApiCartItem(i)), ...customLocal]);
  }

  /**
   * Merge a single API cart item into the local cart signal (keyed by product+variant).
   */
  syncApiCartItem(item: ApiCartItem): void {
    const mapped = this.fromApiCartItem(item);
    this.cart.update((items) => {
      const exists = items.some((i) => i.productId === mapped.productId && i.variantId === mapped.variantId);
      if (exists) {
        return items.map((i) =>
          i.productId === mapped.productId && i.variantId === mapped.variantId ? mapped : i
        );
      }
      return [...items, mapped];
    });
  }

  // ── review helpers (by string id) ─────────────────────────────────────────

  getReviewsForProduct(productId: string): Review[] {
    return this.reviews()[productId] ?? [];
  }

  getReviewCount(productId: string): number {
    return this.getReviewsForProduct(productId).length;
  }

  /** Alias used by product-detail page (API products use numeric id as string) */
  getReviewCountById(productId: number | string): number {
    return this.getReviewCount(String(productId));
  }

  getAverageRating(product: Product): number {
    const rs = this.getReviewsForProduct(product.id);
    if (rs.length === 0) return product.rating;
    return rs.reduce((acc, r) => acc + r.rating, product.rating) / (rs.length + 1);
  }

  getAverageRatingById(productId: number | string): number {
    const id = String(productId);
    const rs = this.getReviewsForProduct(id);
    if (rs.length === 0) return 4.5;
    return rs.reduce((acc, r) => acc + r.rating, 4.5) / (rs.length + 1);
  }

  addReview(productId: string, name: string, rating: number, comment: string): boolean {
    const safeRating = Math.max(1, Math.min(5, Math.round(rating))) as Review['rating'];
    const n = name.trim();
    const c = comment.trim();
    if (!n || !c) return false;

    const review: Review = {
      id:        `RVW-${Date.now().toString(36).toUpperCase()}`,
      productId,
      name:      n,
      rating:    safeRating,
      comment:   c,
      createdAt: new Date().toISOString()
    };

    this.reviews.update((state) => ({
      ...state,
      [productId]: [review, ...(state[productId] ?? [])]
    }));
    return true;
  }

  // ── catalog mutations ──────────────────────────────────────────────────────

  setSearchQuery(query: string): void { this.query.set(query); }
  setCategory(category: 'All' | Product['category']): void { this.category.set(category); }
  setSort(sortBy: 'featured' | 'price-asc' | 'price-desc' | 'rating'): void { this.sortBy.set(sortBy); }
  setPromoCode(code: string): void { this.promoCode.set(code); }
  clearPromoCode(): void { this.promoCode.set(''); }

  getProductBySlug(slug: string): Product | undefined {
    return this.products().find((p) => p.slug === slug);
  }

  // ── wishlist ───────────────────────────────────────────────────────────────

  isWishlisted(productId: string): boolean {
    return this.wishlist().includes(productId);
  }

  toggleWishlist(productId: string): void {
    this.wishlist.update((items) =>
      items.includes(productId) ? items.filter((id) => id !== productId) : [...items, productId]
    );
  }

  // ── cart mutations ─────────────────────────────────────────────────────────

  addToCart(
    productId: string,
    size: string,
    quantity = 1,
    meta?: {
      variantId?: number | null; unitPrice?: number; name?: string; image?: string | null;
      customMeasurements?: string | null; customSurcharge?: number | null;
    },
  ): void {
    this.cart.update((items) => {
      const product = this.products().find((p) => p.id === productId);
      if (!product || product.inStock <= 0) return items;
      const variantId = meta?.variantId ?? null;
      const unitPrice = meta?.unitPrice ?? product.price;
      const name      = meta?.name ?? product.name;
      const image     = meta?.image ?? product.image;
      const customMeasurements = meta?.customMeasurements ?? null;
      const customSurcharge    = meta?.customSurcharge ?? null;
      // Custom (made-to-order) lines are always distinct — never merge them, so
      // each set of measurements stays separate.
      if (!customMeasurements) {
        const existing = items.find((i) => i.productId === productId && i.variantId === variantId && i.size === size && !i.customMeasurements);
        if (existing) {
          return items.map((i) =>
            i === existing
              ? { ...i, quantity: Math.min(product.inStock, i.quantity + quantity) }
              : i
          );
        }
      }
      return [...items, { productId, variantId, size, quantity: Math.min(product.inStock, quantity), unitPrice, name, image, customMeasurements, customSurcharge }];
    });
  }

  updateCartQuantity(productId: string, size: string, quantity: number): void {
    if (quantity <= 0) { this.removeFromCart(productId, size); return; }
    this.cart.update((items) =>
      items.map((i) => (i.productId === productId && i.size === size ? { ...i, quantity } : i))
    );
  }

  removeFromCart(productId: string, size: string): void {
    this.cart.update((items) => items.filter((i) => !(i.productId === productId && i.size === size)));
  }

  // ── auth (local / guest) ───────────────────────────────────────────────────

  signIn(name: string, email: string): void {
    this.profile.update((p) => ({ ...p, name, email }));
    this.isAuthenticated.set(true);
  }

  signOut(): void {
    this.isAuthenticated.set(false);
  }

  updateProfile(profile: UserProfile): void {
    this.profile.set(profile);
  }

  // ── orders ─────────────────────────────────────────────────────────────────

  getOrderById(orderId: string): Order | undefined {
    return this.orders().find((o) => o.id === orderId);
  }

  getDeliveryProgress(status: Order['delivery']['status']): number {
    return this.deliveryStatusSteps.indexOf(status) + 1;
  }

  checkout(paymentMethod: Order['payment']['method']): string {
    const orderId = `ORD-${Date.now().toString().slice(-6)}`;
    const now = new Date();
    const eta = new Date(now);
    eta.setDate(now.getDate() + 4);

    this.orders.update((orders) => [
      {
        id: orderId,
        items: this.cart(),
        payment: {
          method: paymentMethod,
          status: paymentMethod === 'Cash on Delivery' ? 'Pending' : 'Paid',
          transactionId: `TXN-${Math.random().toString(36).slice(2, 9).toUpperCase()}`
        },
        delivery: {
          status: 'Processing',
          estimatedDate: eta.toISOString(),
          trackingNumber: `TRK-${Math.random().toString(36).slice(2, 10).toUpperCase()}`
        },
        shippingAddress: this.profile(),
        subtotal:  this.cartSubtotal(),
        shippingFee: this.shippingFee(),
        discount:  this.discountAmount(),
        total:     this.cartTotal(),
        appliedPromoCode: this.promoCode().trim() || undefined,
        createdAt: now.toISOString()
      },
      ...orders
    ]);
    this.cart.set([]);
    this.clearPromoCode();
    return orderId;
  }
}
