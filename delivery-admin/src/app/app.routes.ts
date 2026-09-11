import { Route, Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
import { accessGuard } from './core/guards/access.guard';
import { ACCESS, AccessRule } from './core/access/admin-pages';

/**
 * A page behind login with the permission it needs (see ACCESS). `title`
 * names the page on the "No access" screen. `landing` marks the page people
 * are sent to after login; without access to it they go to their first
 * permitted page instead.
 */
function page(path: string, title: string, access: AccessRule, loadComponent: Route['loadComponent'],
              extra: { landing?: boolean } = {}): Route {
  return { path, canActivate: [authGuard, accessGuard], data: { access, title, ...extra }, loadComponent };
}

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'no-access',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/no-access/no-access.component').then((m) => m.NoAccessComponent),
  },
  page('dashboard', 'the dashboard', ACCESS.dashboard, () =>
    import('./features/dashboard/dashboard.component').then((m) => m.DashboardComponent), { landing: true }),
  page('products', 'Products', ACCESS.products, () =>
    import('./features/products/products-list/products-list.component').then((m) => m.ProductsListComponent)),
  page('products/new', 'Add product', ACCESS.productNew, () =>
    import('./features/products/add-product/add-product.component').then((m) => m.AddProductComponent)),
  page('products/prices', 'Change prices', ACCESS.productPrices, () =>
    import('./features/products/price-change/price-change.component').then((m) => m.PriceChangeComponent)),
  page('products/codes', 'Change product codes', ACCESS.productCodes, () =>
    import('./features/products/code-change/code-change.component').then((m) => m.CodeChangeComponent)),
  page('products/:id/edit', 'Edit product', ACCESS.productEdit, () =>
    import('./features/products/edit-product/edit-product.component').then((m) => m.EditProductComponent)),
  page('categories', 'Categories', ACCESS.categories, () =>
    import('./features/categories/categories-list/categories-list.component').then((m) => m.CategoriesListComponent)),
  page('categories/new', 'Add category', ACCESS.categoryNew, () =>
    import('./features/categories/add-category/add-category.component').then((m) => m.AddCategoryComponent)),
  page('categories/:id/edit', 'Edit category', ACCESS.categoryEdit, () =>
    import('./features/categories/edit-category/edit-category.component').then((m) => m.EditCategoryComponent)),
  page('inventory', 'Inventory', ACCESS.inventory, () =>
    import('./features/inventory/inventory.component').then((m) => m.InventoryComponent)),
  page('analytics', 'Analytics', ACCESS.analytics, () =>
    import('./features/analytics/analytics.component').then((m) => m.AnalyticsComponent)),
  page('banners', 'Banners', ACCESS.banners, () =>
    import('./features/banners/banners.component').then((m) => m.BannersComponent)),
  page('orders', 'Orders', ACCESS.orders, () =>
    import('./features/orders/orders.component').then((m) => m.OrdersComponent)),
  page('orders/:orderId', 'Order details', ACCESS.orders, () =>
    import('./features/orders/order-detail/order-detail.component').then((m) => m.OrderDetailComponent)),
  page('reviews', 'Reviews', ACCESS.reviews, () =>
    import('./features/reviews/reviews.component').then((m) => m.ReviewsComponent)),
  page('coupons', 'Coupons', ACCESS.coupons, () =>
    import('./features/coupons/coupons.component').then((m) => m.CouponsComponent)),
  page('discounts', 'Discounts', ACCESS.discounts, () =>
    import('./features/discounts/discounts.component').then((m) => m.DiscountsComponent)),
  page('flash-sales', 'Flash sales', ACCESS.flashSales, () =>
    import('./features/flash-sale/flash-sale.component').then((m) => m.FlashSaleComponent)),
  page('bundles', 'Bundles', ACCESS.bundles, () =>
    import('./features/bundles/bundles.component').then((m) => m.BundlesComponent)),
  page('settings', 'Settings', ACCESS.settings, () =>
    import('./features/settings/store-settings.component').then((m) => m.StoreSettingsComponent)),
  page('returns', 'Returns', ACCESS.returns, () =>
    import('./features/returns/returns.component').then((m) => m.ReturnsComponent)),
  page('support', 'Support', ACCESS.support, () =>
    import('./features/support/support.component').then((m) => m.SupportComponent)),
  page('custom-orders', 'Custom orders', ACCESS.customOrders, () =>
    import('./features/custom-orders/custom-orders.component').then((m) => m.CustomOrdersComponent)),
  page('custom-design', 'Custom design', ACCESS.customDesign, () =>
    import('./features/custom-design/custom-design.component').then((m) => m.CustomDesignComponent)),
  page('reports', 'Reports', ACCESS.reports, () =>
    import('./features/reports/reports.component').then((m) => m.ReportsComponent)),
  page('customers', 'Customers', ACCESS.customers, () =>
    import('./features/customers/customers.component').then((m) => m.CustomersComponent)),
  page('newsletter', 'Newsletter', ACCESS.newsletter, () =>
    import('./features/newsletter/newsletter.component').then((m) => m.NewsletterComponent)),
  page('staff', 'Staff', ACCESS.staff, () =>
    import('./features/staff/staff.component').then((m) => m.StaffComponent)),
  page('staff/new', 'Add staff member', ACCESS.staffNew, () =>
    import('./features/staff/staff-add.component').then((m) => m.StaffAddComponent)),
  page('activity', 'the activity log', ACCESS.activity, () =>
    import('./features/activity/activity.component').then((m) => m.ActivityComponent)),
  { path: '**', redirectTo: 'login' },
];
