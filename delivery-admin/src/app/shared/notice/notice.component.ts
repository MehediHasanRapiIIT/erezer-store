import { Component, inject } from '@angular/core';
import { NoticeService } from '../../core/services/notice.service';

/** Shows NoticeService messages stacked in the bottom-right corner. */
@Component({
  selector: 'app-notices',
  standalone: true,
  template: `
    <div class="fixed bottom-4 right-4 z-50 flex w-80 max-w-[calc(100vw-2rem)] flex-col gap-2" aria-live="polite">
      @for (n of notices.notices(); track n.id) {
        <div role="status"
          class="rounded-lg border px-4 py-3 shadow-lg text-sm bg-white"
          [class.border-red-200]="n.kind === 'error'" [class.border-gray-200]="n.kind === 'info'">
          <div class="flex items-start gap-3">
            <div class="min-w-0 flex-1">
              <p class="font-semibold" [class.text-red-700]="n.kind === 'error'" [class.text-gray-900]="n.kind === 'info'">
                {{ n.title }}
              </p>
              <p class="mt-0.5 text-gray-600">{{ n.message }}</p>
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
