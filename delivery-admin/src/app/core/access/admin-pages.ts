import type { PermissionService } from '../services/permission.service';

/**
 * What a page or action needs. `any`: at least one of these permissions.
 * `all`: every one of them. Both may be given; both must then hold.
 */
export interface AccessRule {
  any?: string[];
  all?: string[];
}

/**
 * The permission each admin page needs, in one place: used by the route
 * guard, the sidebar and the landing page. Mirrors the backend catalogue in
 * ACCESS-CONTROL-PLAN.md; the backend still checks every action itself.
 */
export const ACCESS = {
  dashboard:    { any: ['dashboard.view'] },
  orders:       { any: ['orders.view'] },
  products:     { any: ['products.view'] },
  productNew:   { all: ['products.create', 'products.price'] },
  productEdit:  { any: ['products.edit'] },
  productPrices: { all: ['products.edit', 'products.price'] },
  productCodes: { any: ['products.edit'] },
  categories:   { any: ['categories.view'] },
  categoryNew:  { any: ['categories.create'] },
  categoryEdit: { any: ['categories.edit'] },
  inventory:    { any: ['inventory.view'] },
  analytics:    { any: ['analytics.view'] },
  banners:      { any: ['banners.view'] },
  reviews:      { any: ['reviews.view'] },
  reports:      { all: ['reports.view', 'finance.revenue'] },
  coupons:      { any: ['coupons.view'] },
  discounts:    { any: ['discounts.view'] },
  flashSales:   { any: ['flash_sales.view'] },
  bundles:      { any: ['bundles.view'] },
  returns:      { any: ['returns.view'] },
  customOrders: { any: ['custom_orders.view'] },
  customDesign: { any: ['design.view'] },
  support:      { any: ['support.view'] },
  newsletter:   { any: ['newsletter.subscribers', 'newsletter.campaigns.view'] },
  customers:    { any: ['customers.view'] },
  settings:     { any: ['settings.view'] },
  staff:        { any: ['staff.view'] },
  staffNew:     { any: ['staff.manage'] },
  activity:     { any: ['activity.view'] },
} satisfies Record<string, AccessRule>;

export interface AdminPage {
  label: string;
  icon: string;
  route: string;
  access: AccessRule;
}

/** Sidebar pages, in sidebar order. The first one a person may open is their landing page. */
export const ADMIN_PAGES: AdminPage[] = [
  { label: 'Dashboard',     icon: 'dashboard',  route: '/dashboard',     access: ACCESS.dashboard },
  { label: 'Orders',        icon: 'orders',     route: '/orders',        access: ACCESS.orders },
  { label: 'Products',      icon: 'products',   route: '/products',      access: ACCESS.products },
  { label: 'Categories',    icon: 'categories', route: '/categories',    access: ACCESS.categories },
  { label: 'Inventory',     icon: 'inventory',  route: '/inventory',     access: ACCESS.inventory },
  { label: 'Analytics',     icon: 'analytics',  route: '/analytics',     access: ACCESS.analytics },
  { label: 'Banners',       icon: 'banners',    route: '/banners',       access: ACCESS.banners },
  { label: 'Reviews',       icon: 'reviews',    route: '/reviews',       access: ACCESS.reviews },
  { label: 'Reports',       icon: 'analytics',  route: '/reports',       access: ACCESS.reports },
  { label: 'Coupons',       icon: 'banners',    route: '/coupons',       access: ACCESS.coupons },
  { label: 'Discounts',     icon: 'banners',    route: '/discounts',     access: ACCESS.discounts },
  { label: 'Flash Sales',   icon: 'banners',    route: '/flash-sales',   access: ACCESS.flashSales },
  { label: 'Bundles',       icon: 'banners',    route: '/bundles',       access: ACCESS.bundles },
  { label: 'Returns',       icon: 'orders',     route: '/returns',       access: ACCESS.returns },
  { label: 'Custom Orders', icon: 'orders',     route: '/custom-orders', access: ACCESS.customOrders },
  { label: 'Custom Design', icon: 'products',   route: '/custom-design', access: ACCESS.customDesign },
  { label: 'Support',       icon: 'reviews',    route: '/support',       access: ACCESS.support },
  { label: 'Newsletter',    icon: 'banners',    route: '/newsletter',    access: ACCESS.newsletter },
  { label: 'Customers',     icon: 'customers',  route: '/customers',     access: ACCESS.customers },
  { label: 'Settings',      icon: 'dashboard',  route: '/settings',      access: ACCESS.settings },
  { label: 'Staff',         icon: 'customers',  route: '/staff',         access: ACCESS.staff },
  { label: 'Activity log',  icon: 'reviews',    route: '/activity',      access: ACCESS.activity },
];

/** The first sidebar page this person may open, or null when there is none. */
export function firstAllowedPage(perms: PermissionService): string | null {
  return ADMIN_PAGES.find((p) => perms.allows(p.access))?.route ?? null;
}
