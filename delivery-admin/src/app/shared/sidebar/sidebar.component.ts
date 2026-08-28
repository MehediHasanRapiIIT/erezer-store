import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { GlobalSearchComponent } from '../global-search/global-search.component';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, GlobalSearchComponent],
  templateUrl: './sidebar.component.html',
})
export class SidebarComponent {
  protected auth = inject(AuthService);
  protected theme = inject(ThemeService);

  navItems = [
    { label: 'Dashboard',  icon: 'dashboard',  route: '/dashboard' },
    { label: 'Orders',     icon: 'orders',     route: '/orders' },
    { label: 'Products',   icon: 'products',   route: '/products' },
    { label: 'Categories', icon: 'categories', route: '/categories' },
    { label: 'Inventory',  icon: 'inventory',  route: '/inventory' },
    { label: 'Analytics',  icon: 'analytics',  route: '/analytics' },
    { label: 'Banners',    icon: 'banners',    route: '/banners' },
    { label: 'Reviews',    icon: 'reviews',    route: '/reviews' },
    { label: 'Reports',    icon: 'analytics',  route: '/reports' },
    { label: 'Coupons',    icon: 'banners',    route: '/coupons' },
    { label: 'Discounts',  icon: 'banners',    route: '/discounts' },
    { label: 'Flash Sales', icon: 'banners',   route: '/flash-sales' },
    { label: 'Bundles',    icon: 'banners',    route: '/bundles' },
    { label: 'Returns',    icon: 'orders',     route: '/returns' },
    { label: 'Custom Orders', icon: 'orders',  route: '/custom-orders' },
    { label: 'Custom Design', icon: 'products', route: '/custom-design' },
    { label: 'Support',    icon: 'reviews',    route: '/support' },
    { label: 'Newsletter', icon: 'banners',    route: '/newsletter' },
    { label: 'Customers',  icon: 'customers',  route: '/customers' },
    { label: 'Settings',   icon: 'dashboard',  route: '/settings' },
  ];
}
