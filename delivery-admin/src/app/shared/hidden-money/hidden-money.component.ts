import { Component } from '@angular/core';

/**
 * Stands in for a money figure the person may not see. The backend leaves
 * such figures out (null) without the "See money totals" permission; show
 * this instead of 0 so nobody mistakes "hidden" for "no sales".
 */
@Component({
  selector: 'app-hidden-money',
  standalone: true,
  template: `<span class="inline-flex items-center gap-1 text-gray-400"
    title="Hidden: needs the “See money totals” permission">🔒 Hidden</span>`,
})
export class HiddenMoneyComponent {}
