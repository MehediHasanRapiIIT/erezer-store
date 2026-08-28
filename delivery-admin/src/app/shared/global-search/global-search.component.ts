import {
  Component,
  ElementRef,
  HostListener,
  OnDestroy,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subject, Subscription, debounceTime, distinctUntilChanged, switchMap, of } from 'rxjs';
import { SearchService } from '../../core/services/search.service';
import { AdminSearchResponse, SearchGroup, SearchItem } from '../../core/models/api.models';

type GroupKey = 'products' | 'categories' | 'orders' | 'customers' | 'reviews';

interface RenderGroup {
  key: GroupKey;
  label: string;
  group: SearchGroup;
}

@Component({
  selector: 'app-global-search',
  standalone: true,
  imports: [],
  templateUrl: './global-search.component.html',
})
export class GlobalSearchComponent implements OnInit, OnDestroy {
  private searchService = inject(SearchService);
  private router = inject(Router);
  private host = inject(ElementRef<HTMLElement>);

  query = signal('');
  loading = signal(false);
  open = signal(false);
  result = signal<AdminSearchResponse | null>(null);

  private query$ = new Subject<string>();
  private sub?: Subscription;

  ngOnInit(): void {
    this.sub = this.query$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => {
          const trimmed = q.trim();
          if (trimmed.length < 2) {
            this.loading.set(false);
            this.result.set(null);
            return of(null);
          }
          this.loading.set(true);
          return this.searchService.adminSearch(trimmed, 5);
        })
      )
      .subscribe({
        next: (res) => {
          this.loading.set(false);
          this.result.set(res);
        },
        error: () => {
          this.loading.set(false);
          this.result.set(null);
        },
      });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onInput(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.query.set(value);
    this.open.set(true);
    this.query$.next(value);
  }

  onFocus(): void {
    if (this.query().trim().length >= 2) {
      this.open.set(true);
    }
  }

  clear(): void {
    this.query.set('');
    this.result.set(null);
    this.open.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  groups(): RenderGroup[] {
    const r = this.result();
    if (!r) return [];
    const all: RenderGroup[] = [
      { key: 'products', label: 'Products', group: r.products },
      { key: 'categories', label: 'Categories', group: r.categories },
      { key: 'orders', label: 'Orders', group: r.orders },
      { key: 'customers', label: 'Customers', group: r.customers },
      { key: 'reviews', label: 'Reviews', group: r.reviews },
    ];
    return all.filter((g) => g.group && g.group.items.length > 0);
  }

  hasNoResults(): boolean {
    const r = this.result();
    return !this.loading() && !!r && r.totalCount === 0 && this.query().trim().length >= 2;
  }

  shouldShowDropdown(): boolean {
    return this.open() && this.query().trim().length >= 2;
  }

  onItemClick(item: SearchItem): void {
    const route = this.routeFor(item);
    if (route) {
      this.router.navigate(route);
    }
    this.open.set(false);
  }

  private routeFor(item: SearchItem): unknown[] | null {
    switch (item.type) {
      case 'PRODUCT':
        return ['/products', item.id, 'edit'];
      case 'CATEGORY':
        return ['/categories', item.id, 'edit'];
      case 'ORDER':
        return ['/orders', item.id];
      case 'CUSTOMER':
        return ['/customers'];
      case 'REVIEW':
        return ['/reviews'];
      default:
        return null;
    }
  }

  trackByItem(_: number, item: SearchItem): string {
    return `${item.type}-${item.id}`;
  }

  trackByGroup(_: number, g: RenderGroup): string {
    return g.key;
  }
}
