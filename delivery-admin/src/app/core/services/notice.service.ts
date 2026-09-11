import { Injectable, signal } from '@angular/core';

export interface Notice {
  id: number;
  kind: 'success' | 'error' | 'info';
  title: string;
  message: string;
}

/** Short pop-up messages in the corner of the screen, e.g. when an action is refused. */
@Injectable({ providedIn: 'root' })
export class NoticeService {
  readonly notices = signal<Notice[]>([]);
  private nextId = 1;

  show(kind: Notice['kind'], title: string, message: string, durationMs = 7000): void {
    // The same message twice in a row (e.g. two refused requests) shows once.
    if (this.notices().some((n) => n.title === title && n.message === message)) return;
    const notice: Notice = { id: this.nextId++, kind, title, message };
    this.notices.update((list) => [...list, notice]);
    setTimeout(() => this.dismiss(notice.id), durationMs);
  }

  /** "Saved", "Deleted", "Updated": gone again in a few seconds. */
  success(title: string, message = ''): void {
    this.show('success', title, message, 4000);
  }

  /** Something didn't work; stays longer so it can be read. */
  error(title: string, message = ''): void {
    this.show('error', title, message, 7000);
  }

  dismiss(id: number): void {
    this.notices.update((list) => list.filter((n) => n.id !== id));
  }
}
