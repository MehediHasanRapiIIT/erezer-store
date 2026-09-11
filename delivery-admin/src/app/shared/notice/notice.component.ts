import { Component, inject } from '@angular/core';
import { NoticeService } from '../../core/services/notice.service';

/** Shows NoticeService messages stacked in the bottom-right corner. */
@Component({
  selector: 'app-notices',
  standalone: true,
  template: `
    <div class="fixed bottom-4 right-4 z-[70] flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
      @for (n of notices.notices(); track n.id) {
        <div role="status"
          class="rounded-lg border border-l-4 bg-white px-4 py-3 text-sm shadow-lg"
          [class.border-emerald-200]="n.kind === 'success'" [class.border-l-emerald-500]="n.kind === 'success'"
          [class.border-red-200]="n.kind === 'error'" [class.border-l-red-500]="n.kind === 'error'"
          [class.border-gray-200]="n.kind === 'info'" [class.border-l-gray-400]="n.kind === 'info'">
          <div class="flex items-start gap-3">
            <span aria-hidden="true" class="mt-0.5 font-bold"
              [class.text-emerald-600]="n.kind === 'success'"
              [class.text-red-600]="n.kind === 'error'"
              [class.text-gray-400]="n.kind === 'info'">
              {{ n.kind === 'success' ? '✓' : n.kind === 'error' ? '!' : 'i' }}
            </span>
            <div class="min-w-0 flex-1">
              <p class="font-semibold"
                [class.text-emerald-700]="n.kind === 'success'"
                [class.text-red-700]="n.kind === 'error'"
                [class.text-gray-900]="n.kind === 'info'">
                {{ n.title }}
              </p>
              @if (n.message) {
                <p class="mt-0.5 text-gray-600">{{ n.message }}</p>
              }
            </div>
            <button type="button" (click)="notices.dismiss(n.id)" aria-label="Dismiss"
              class="-mr-1 rounded p-1 text-gray-400 hover:bg-gray-100 hover:text-gray-600">✕</button>
          </div>
        </div>
      }
    </div>
  `,
})
export class NoticeComponent {
  protected notices = inject(NoticeService);
}
