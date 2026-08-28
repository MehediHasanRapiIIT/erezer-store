import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/home.page').then((m) => m.HomePage)
  },
  {
    path: 'shop',
    loadComponent: () => import('./pages/shop.page').then((m) => m.ShopPage)
  },
  {
    path: 'custom-design',
    loadComponent: () => import('./pages/custom-design.page').then((m) => m.CustomDesignPage)
  },
  {
    path: 'flash-sale',
    loadComponent: () => import('./pages/flash-sales-list.page').then((m) => m.FlashSalesListPage)
  },
  {
    path: 'flash-sale/:id',
    loadComponent: () => import('./pages/flash-sale.page').then((m) => m.FlashSalePage)
  },
  {
    path: 'bundles',
    loadComponent: () => import('./pages/bundles.page').then((m) => m.BundlesPage)
  },
  {
    path: 'bundles/:id',
    loadComponent: () => import('./pages/bundle-detail.page').then((m) => m.BundleDetailPage)
  },
  {
    path: 'product/:id',
    loadComponent: () =>
      import('./pages/product-detail.page').then((m) => m.ProductDetailPage)
  },
  {
    path: 'cart',
    loadComponent: () => import('./pages/cart.page').then((m) => m.CartPage)
  },
  {
    path: 'checkout',
    loadComponent: () => import('./pages/checkout.page').then((m) => m.CheckoutPage)
  },
  {
    path: 'account',
    loadComponent: () => import('./pages/account.page').then((m) => m.AccountPage)
  },
  {
    path: 'wishlist',
    loadComponent: () => import('./pages/wishlist.page').then((m) => m.WishlistPage)
  },
  {
    path: 'orders',
    loadComponent: () => import('./pages/orders.page').then((m) => m.OrdersPage)
  },
  {
    path: 'orders/:id',
    loadComponent: () => import('./pages/order-detail.page').then((m) => m.OrderDetailPage)
  },
  {
    path: 'admin',
    loadComponent: () => import('./pages/admin.page').then((m) => m.AdminPage)
  },
  {
    path: 'verify-email',
    loadComponent: () => import('./pages/verify-email.page').then((m) => m.VerifyEmailPage)
  },
  {
    path: 'reset-password',
    loadComponent: () => import('./pages/reset-password.page').then((m) => m.ResetPasswordPage)
  },
  {
    path: 'bkash-callback',
    loadComponent: () => import('./pages/bkash-callback.page').then((m) => m.BkashCallbackPage)
  },
  {
    path: 'orders/:orderId/return',
    loadComponent: () => import('./pages/request-return.page').then((m) => m.RequestReturnPage)
  },
  {
    path: 'contact',
    loadComponent: () => import('./pages/contact.page').then((m) => m.ContactPage)
  },
  {
    path: 'unsubscribe',
    loadComponent: () => import('./pages/unsubscribe.page').then((m) => m.UnsubscribePage)
  },
  { path: '**', redirectTo: '' }
];
