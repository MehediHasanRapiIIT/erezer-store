import { Routes } from '@angular/router';

// data.seo is the title and description search engines and link previews get
// for the page (core/seo.service.ts). Pages without one use the shop's own;
// a product or a collection sets its own once it has loaded.
export const routes: Routes = [
  {
    path: '',
    data: { seo: { shop: true } },
    loadComponent: () => import('./pages/home.page').then((m) => m.HomePage)
  },
  {
    path: 'shop',
    data: { seo: { title: 'Shop All Clothing', description: 'Browse everything at Erezer: t-shirts, hoodies and more, with new arrivals and offers. Cash on delivery and bKash, delivery across Bangladesh.' } },
    loadComponent: () => import('./pages/shop.page').then((m) => m.ShopPage)
  },
  {
    path: 'custom-design',
    data: { seo: { title: 'Custom T-Shirt Design – Print Your Own', description: 'Design your own t-shirt or hoodie online at Erezer: upload a picture or add text, choose the colour and size, and get a price. Printed and delivered across Bangladesh.' } },
    loadComponent: () => import('./pages/custom-design.page').then((m) => m.CustomDesignPage)
  },
  {
    path: 'flash-sale',
    data: { seo: { title: 'Flash Sale', description: 'Limited-time flash sale prices on Erezer clothing. Order before the timer runs out.' } },
    loadComponent: () => import('./pages/flash-sales-list.page').then((m) => m.FlashSalesListPage)
  },
  {
    path: 'flash-sale/:id',
    data: { seo: { title: 'Flash Sale', description: 'Limited-time flash sale prices on Erezer clothing. Order before the timer runs out.' } },
    loadComponent: () => import('./pages/flash-sale.page').then((m) => m.FlashSalePage)
  },
  {
    path: 'bundles',
    data: { seo: { title: 'Bundle Offers', description: 'Save more with Erezer bundle offers: sets of clothing sold together at a lower price.' } },
    loadComponent: () => import('./pages/bundles.page').then((m) => m.BundlesPage)
  },
  {
    path: 'bundles/:id',
    data: { seo: { title: 'Bundle Offer', description: 'Save more with Erezer bundle offers: sets of clothing sold together at a lower price.' } },
    loadComponent: () => import('./pages/bundle-detail.page').then((m) => m.BundleDetailPage)
  },
  {
    path: 'product/:id',
    loadComponent: () =>
      import('./pages/product-detail.page').then((m) => m.ProductDetailPage)
  },
  {
    path: 'cart',
    data: { seo: { title: 'Your Cart', noindex: true } },
    loadComponent: () => import('./pages/cart.page').then((m) => m.CartPage)
  },
  {
    path: 'checkout',
    data: { seo: { title: 'Checkout', noindex: true } },
    loadComponent: () => import('./pages/checkout.page').then((m) => m.CheckoutPage)
  },
  {
    path: 'account',
    data: { seo: { title: 'Your Account', noindex: true } },
    loadComponent: () => import('./pages/account.page').then((m) => m.AccountPage)
  },
  {
    path: 'wishlist',
    data: { seo: { title: 'Your Wishlist', noindex: true } },
    loadComponent: () => import('./pages/wishlist.page').then((m) => m.WishlistPage)
  },
  {
    path: 'orders',
    data: { seo: { title: 'Your Orders', noindex: true } },
    loadComponent: () => import('./pages/orders.page').then((m) => m.OrdersPage)
  },
  {
    path: 'orders/:id',
    data: { seo: { title: 'Your Order', noindex: true } },
    loadComponent: () => import('./pages/order-detail.page').then((m) => m.OrderDetailPage)
  },
  {
    path: 'admin',
    data: { seo: { title: 'Admin', noindex: true } },
    loadComponent: () => import('./pages/admin.page').then((m) => m.AdminPage)
  },
  {
    path: 'verify-email',
    data: { seo: { title: 'Verify Email', noindex: true } },
    loadComponent: () => import('./pages/verify-email.page').then((m) => m.VerifyEmailPage)
  },
  {
    path: 'reset-password',
    data: { seo: { title: 'Reset Password', noindex: true } },
    loadComponent: () => import('./pages/reset-password.page').then((m) => m.ResetPasswordPage)
  },
  {
    path: 'bkash-callback',
    data: { seo: { title: 'bKash Payment', noindex: true } },
    loadComponent: () => import('./pages/bkash-callback.page').then((m) => m.BkashCallbackPage)
  },
  {
    path: 'orders/:orderId/return',
    data: { seo: { title: 'Request a Return', noindex: true } },
    loadComponent: () => import('./pages/request-return.page').then((m) => m.RequestReturnPage)
  },
  {
    path: 'contact',
    data: { seo: { title: 'Contact Us', description: 'Contact Erezer about an order, a custom design or anything else. We are happy to help.' } },
    loadComponent: () => import('./pages/contact.page').then((m) => m.ContactPage)
  },
  {
    path: 'unsubscribe',
    data: { seo: { title: 'Unsubscribe', noindex: true } },
    loadComponent: () => import('./pages/unsubscribe.page').then((m) => m.UnsubscribePage)
  },
  {
    path: 'track-order',
    data: { seo: { title: 'Track Your Order', description: 'Track your Erezer order with your order number and see where it is.' } },
    loadComponent: () => import('./pages/track-order.page').then((m) => m.TrackOrderPage)
  },
  {
    path: 'order-placed',
    data: { seo: { title: 'Order Placed', noindex: true } },
    loadComponent: () => import('./pages/order-placed.page').then((m) => m.OrderPlacedPage)
  },
  {
    path: 'about',
    data: { seo: { title: 'About Us', description: 'About Erezer, a clothing shop in Bangladesh: who we are and what we make.' } },
    loadComponent: () => import('./pages/about.page').then((m) => m.AboutPage)
  },
  {
    path: 'categories',
    data: { seo: { title: 'All Collections', description: 'Browse every Erezer collection: t-shirts, hoodies and more.' } },
    loadComponent: () => import('./pages/categories.page').then((m) => m.CategoriesPage)
  },
  // Catch-all collection page, e.g. /erezer-pink.
  //
  // Registered LAST so every real route above wins: a category slugged "shop"
  // could otherwise shadow the shop page. Any slug that matches no category
  // redirects home, which is what the wildcard below used to do for these URLs.
  {
    path: ':slug',
    loadComponent: () => import('./pages/collection.page').then((m) => m.CollectionPage)
  },
  { path: '**', redirectTo: '' }
];
