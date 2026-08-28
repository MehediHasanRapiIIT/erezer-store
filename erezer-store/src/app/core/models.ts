export interface Product {
  id: string;
  slug: string;
  name: string;
  description: string;
  price: number;
  image: string;
  /** Optional secondary image; cards crossfade to it on hover when present. */
  hoverImage?: string;
  category: 'Tops' | 'Outerwear' | 'Bottoms' | 'Accessories';
  sizes: string[];
  rating: number;
  isFeatured?: boolean;
  inStock: number;
}

export interface CartItem {
  productId: string;
  variantId: number | null;
  quantity: number;
  size: string;
  // Self-contained display/pricing data so the cart renders without depending
  // on the products() list and reflects per-variant pricing.
  unitPrice: number;
  name: string;
  image: string | null;
  // Custom (made-to-order) sizing. customMeasurements is a JSON string sent to
  // the backend; customSurcharge is the flat per-line fee (display only — the
  // server re-derives it authoritatively from the product).
  customMeasurements?: string | null;
  customSurcharge?: number | null;
}

export interface UserProfile {
  name: string;
  email: string;
  address: string;
  city: string;
  country: string;
}

export interface Order {
  id: string;
  items: CartItem[];
  payment: {
    method: 'Card' | 'Cash on Delivery' | 'Mobile Wallet';
    status: 'Pending' | 'Paid' | 'Failed';
    transactionId: string;
  };
  delivery: {
    status: 'Processing' | 'Packed' | 'Shipped' | 'Out for Delivery' | 'Delivered';
    estimatedDate: string;
    trackingNumber: string;
  };
  shippingAddress: UserProfile;
  subtotal: number;
  shippingFee: number;
  discount: number;
  total: number;
  appliedPromoCode?: string;
  createdAt: string;
}

export interface Review {
  id: string;
  productId: string;
  name: string;
  rating: 1 | 2 | 3 | 4 | 5;
  comment: string;
  createdAt: string;
}
