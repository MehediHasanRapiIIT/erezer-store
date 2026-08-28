import { inject, Injectable, signal } from '@angular/core';
import { catchError, of } from 'rxjs';
import { ApiService } from '../api.service';
import { ApiFlashSale } from '../api.models';

/**
 * Loads the active flash-sale campaign once and shares it across the landing
 * widget and the dedicated Flash Sale page. `null` means no sale is running, in
 * which case both surfaces hide / show an empty state. Mirrors DiscountsStore.
 */
@Injectable({ providedIn: 'root' })
export class FlashSaleStore {
  private readonly api = inject(ApiService);

  /** Featured campaign for the landing widget. */
  readonly sale = signal<ApiFlashSale | null>(null);
  readonly loading = signal(false);

  /** All active campaigns for the "view all deals" list page. */
  readonly sales = signal<ApiFlashSale[]>([]);
  readonly listLoading = signal(false);

  constructor() {
    this.reload();
  }

  /** Load the featured sale (widget). */
  reload(): void {
    this.loading.set(true);
    this.api.getFlashSale()
      .pipe(catchError(() => of(null)))
      .subscribe((s) => {
        this.sale.set(s);
        this.loading.set(false);
      });
  }

  /** Load all active sales (list page). */
  reloadList(): void {
    this.listLoading.set(true);
    this.api.getFlashSales()
      .pipe(catchError(() => of([] as ApiFlashSale[])))
      .subscribe((list) => {
        this.sales.set(list);
        this.listLoading.set(false);
      });
  }
}
