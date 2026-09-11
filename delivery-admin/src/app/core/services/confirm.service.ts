import { Injectable, signal } from '@angular/core';

/** What a confirmation asks. Only the title is required. */
export interface ConfirmRequest {
  title: string;
  message?: string;
  /** The button that goes ahead; "Yes, continue" unless given. */
  confirmLabel?: string;
  cancelLabel?: string;
  /** Red confirm button, for deletes and anything that can't be undone. */
  danger?: boolean;
}

/**
 * Asks the person to confirm, in the panel's own dialog rather than the
 * browser's grey box. `ask` resolves true when they go ahead.
 */
@Injectable({ providedIn: 'root' })
export class ConfirmService {
  /** The question on screen, or null when none is open. */
  readonly pending = signal<(ConfirmRequest & { id: number }) | null>(null);

  private answerWith: ((ok: boolean) => void) | null = null;
  private nextId = 1;

  ask(request: ConfirmRequest): Promise<boolean> {
    // A second question replaces the first, which counts as cancelled.
    this.answer(false);
    this.pending.set({ ...request, id: this.nextId++ });
    return new Promise<boolean>((resolve) => {
      this.answerWith = resolve;
    });
  }

  /** Called by the dialog: true from the confirm button, false from cancel, Escape or the backdrop. */
  answer(ok: boolean): void {
    const resolve = this.answerWith;
    this.answerWith = null;
    this.pending.set(null);
    resolve?.(ok);
  }
}
