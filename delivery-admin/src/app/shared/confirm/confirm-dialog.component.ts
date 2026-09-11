import { AfterViewChecked, Component, ElementRef, HostListener, inject, viewChild } from '@angular/core';
import { ConfirmService } from '../../core/services/confirm.service';

/**
 * The panel's own "are you sure?" dialog, shown for whatever
 * {@link ConfirmService} asks. Escape or a click outside cancels.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  template: `
    @if (confirm.pending(); as ask) {
      <div class="fixed inset-0 z-[60] flex items-center justify-center bg-black/50 p-4"
        (click)="confirm.answer(false)">
        <div role="alertdialog" aria-modal="true" [attr.aria-label]="ask.title"
          class="w-full max-w-md rounded-xl bg-white p-5 shadow-xl" (click)="$event.stopPropagation()">
          <h2 class="text-base font-bold text-gray-900">{{ ask.title }}</h2>
          @if (ask.message) {
            <p class="mt-1.5 text-sm text-gray-600">{{ ask.message }}</p>
          }
          <div class="mt-5 flex justify-end gap-2">
            <button type="button" (click)="confirm.answer(false)"
              class="rounded-lg border border-gray-200 px-3.5 py-2 text-sm font-medium text-gray-700 hover:bg-gray-50">
              {{ ask.cancelLabel || 'Cancel' }}
            </button>
            <button type="button" #go (click)="confirm.answer(true)"
              class="rounded-lg px-3.5 py-2 text-sm font-semibold text-white"
              [class.bg-red-600]="ask.danger" [class.hover:bg-red-700]="ask.danger"
              [class.bg-blue-600]="!ask.danger" [class.hover:bg-blue-700]="!ask.danger">
              {{ ask.confirmLabel || 'Yes, continue' }}
            </button>
          </div>
        </div>
      </div>
    }
  `,
})
export class ConfirmDialogComponent implements AfterViewChecked {
  protected readonly confirm = inject(ConfirmService);
  private readonly go = viewChild<ElementRef<HTMLButtonElement>>('go');
  private focusedFor = 0;

  /** Puts the keyboard on the confirm button when a question opens. */
  ngAfterViewChecked(): void {
    const ask = this.confirm.pending();
    if (ask && ask.id !== this.focusedFor) {
      this.focusedFor = ask.id;
      this.go()?.nativeElement.focus();
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.confirm.pending()) this.confirm.answer(false);
  }
}
