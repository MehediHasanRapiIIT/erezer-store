import { Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'erezer-recently-viewed';
const MAX_ENTRIES = 12;

/**
 * Tracks the last N product IDs the customer opened, persisted in
 * localStorage. Most-recent-first; visiting a product that's already in the
 * list moves it to the front. Anonymous: no backend round-trip.
 */
@Injectable({ providedIn: 'root' })
export class RecentlyViewedService {

  readonly ids = signal<number[]>(this.load());

  /** Record a PDP visit. No-op when running outside the browser (SSR safety). */
  track(productId: number | string): void {
    const id = Number(productId);
    if (!Number.isFinite(id) || id <= 0) return;

    const next = [id, ...this.ids().filter((x) => x !== id)].slice(0, MAX_ENTRIES);
    this.ids.set(next);
    this.persist(next);
  }

  /** Returns up to {@code limit} ids, optionally excluding one (the current PDP). */
  others(excludeId: number | string | null, limit = 6): number[] {
    const ex = excludeId == null ? null : Number(excludeId);
    return this.ids()
      .filter((id) => ex == null || id !== ex)
      .slice(0, limit);
  }

  clear(): void {
    this.ids.set([]);
    this.persist([]);
  }

  // ── internal ────────────────────────────────────────────────────────────────

  private load(): number[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return [];
      return parsed.filter((x) => typeof x === 'number' && x > 0).slice(0, MAX_ENTRIES);
    } catch {
      return [];
    }
  }

  private persist(ids: number[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(ids));
    } catch {
      // Private mode / quota — silently ignore.
    }
  }
}
