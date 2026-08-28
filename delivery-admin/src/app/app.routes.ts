import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent),
  },
  {
    path: 'products',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/products/products-list/products-list.component').then((m) => m.ProductsListComponent),
  },
  {
    path: 'products/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/products/add-product/add-product.component').then((m) => m.AddProductComponent),
  },
  {
    path: 'products/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/products/edit-product/edit-product.component').then((m) => m.EditProductComponent),
  },
  {
    path: 'categories',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/categories/categories-list/categories-list.component').then((m) => m.CategoriesListComponent),
  },
  {
    path: 'categories/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/categories/add-category/add-category.component').then((m) => m.AddCategoryComponent),
  },
  {
    path: 'categories/:id/edit',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/categories/edit-category/edit-category.component').then((m) => m.EditCategoryComponent),
  },
  {
    path: 'inventory',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/inventory/inventory.component').then((m) => m.InventoryComponent),
  },
  {
    path: 'analytics',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/analytics/analytics.component').then((m) => m.AnalyticsComponent),
  },
  {
    path: 'banners',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/banners/banners.component').then((m) => m.BannersComponent),
  },
  {
    path: 'orders',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/orders/orders.component').then((m) => m.OrdersComponent),
  },
  {
    path: 'orders/:orderId',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/orders/order-detail/order-detail.component').then((m) => m.OrderDetailComponent),
  },
  {
    path: 'reviews',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/reviews/reviews.component').then((m) => m.ReviewsComponent),
  },
  {
    path: 'coupons',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/coupons/coupons.component').then((m) => m.CouponsComponent),
  },
  {
    path: 'discounts',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/discounts/discounts.component').then((m) => m.DiscountsComponent),
  },
  {
    path: 'flash-sales',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/flash-sale/flash-sale.component').then((m) => m.FlashSaleComponent),
  },
  {
    path: 'bundles',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/bundles/bundles.component').then((m) => m.BundlesComponent),
  },
  {
    path: 'settings',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/settings/store-settings.component').then((m) => m.StoreSettingsComponent),
  },
  {
    path: 'returns',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/returns/returns.component').then((m) => m.ReturnsComponent),
  },
  {
    path: 'support',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/support/support.component').then((m) => m.SupportComponent),
  },
  {
    path: 'custom-orders',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/custom-orders/custom-orders.component').then((m) => m.CustomOrdersComponent),
  },
  {
    path: 'custom-design',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/custom-design/custom-design.component').then((m) => m.CustomDesignComponent),
  },
  {
    path: 'reports',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/reports/reports.component').then((m) => m.ReportsComponent),
  },
  {
    path: 'customers',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/customers/customers.component').then((m) => m.CustomersComponent),
  },
  {
    path: 'newsletter',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/newsletter/newsletter.component').then((m) => m.NewsletterComponent),
  },
  { path: '**', redirectTo: 'login' },
];
