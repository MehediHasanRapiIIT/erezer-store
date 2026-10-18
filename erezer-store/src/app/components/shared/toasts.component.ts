import { Component, inject } from '@angular/core';
import { ToastService } from '../../core/toast.service';

/** The shop's pop-up messages, at the bottom of the screen above everything else. */
@Component({
  selector: 'app-toasts',
  standalone: true,
  template: `
    <div class="pointer-events-none fixed inset-x-0 bottom-4 z-[70] flex flex-col items-center gap-2 px-4 sm:bottom-6" aria-live="assertive" role="status">
      @for (t of toasts.toasts(); track t.id) {
        <div data-testid="toast"
          class="animate-overlay-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl px-4 py-3 text-sm font-medium shadow-xl"
          [class]="t.kind === 'warning'
            ? 'animate-overlay-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl bg-amber-500 px-4 py-3 text-sm font-semibold text-black shadow-xl'
            : 'animate-overlay-in pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl bg-neutral-900 px-4 py-3 text-sm font-medium text-white shadow-xl dark:bg-white dark:text-black'">
          <svg class="mt-0.5 h-4 w-4 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2" aria-hidden="true"><path stroke-linecap="round" stroke-linejoin="round" d="M12 9v3.75m0 3.75h.008M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
          <span class="flex-1">{{ t.text }}</span>
          <button type="button" (click)="toasts.dismiss(t.id)" class="-m-1 rounded-full p-1 opacity-70 hover:opacity-100" aria-label="Close message">✕</button>
        </div>
      }
    </div>
  `,
})
export class ToastsComponent {
  protected readonly toasts = inject(ToastService);
}
