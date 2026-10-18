import { Injectable, signal } from '@angular/core';

/** One short message shown at the bottom of the screen. */
export interface Toast {
  id: number;
  text: string;
  kind: 'warning' | 'info';
}

/**
 * Short pop-up messages ("Please select a size…"), shown by the toast list in
 * the app's root and gone after a few seconds.
 */
@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private nextId = 1;

  show(text: string, kind: Toast['kind'] = 'info', ms = 3500): void {
    // The same message twice in a row: keep one, shown afresh.
    const id = this.nextId++;
    this.toasts.update((list) => [...list.filter((t) => t.text !== text), { id, text, kind }]);
    setTimeout(() => this.dismiss(id), ms);
  }

  warn(text: string): void {
    this.show(text, 'warning');
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }
}
