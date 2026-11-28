import { Injectable } from '@angular/core';

/**
 * Where each list was (page, search, filters, tab), kept while the admin is open,
 * so going into an order or a product and coming back - by its Back button, the
 * breadcrumb, the sidebar or the browser's back - shows the list as it was left.
 * A list saves its state after each page it loads and reads it when it opens.
 */
@Injectable({ providedIn: 'root' })
export class ListMemory {
  private readonly saved = new Map<string, unknown>();

  remember<T>(list: string, state: T): void {
    this.saved.set(list, state);
  }

  recall<T>(list: string): T | undefined {
    return this.saved.get(list) as T | undefined;
  }
}
