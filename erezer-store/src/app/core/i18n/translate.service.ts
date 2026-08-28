import { computed, Injectable, signal } from '@angular/core';
import { DICTIONARIES, Lang, SUPPORTED_LANGS } from './dictionaries';

const STORAGE_KEY = 'erezer-lang';

/**
 * Runtime translation service. Single source of truth for the customer's
 * current language; persists to localStorage so refresh keeps the choice.
 *
 * Usage:
 *   - In templates:   {{ 'header.shop' | t }}        (see TranslatePipe)
 *   - Imperative:     translate.t('header.shop')
 *
 * Missing Bangla keys fall back to English; missing English keys fall back
 * to the raw key string (visible-bad-data — easy to catch in QA).
 */
@Injectable({ providedIn: 'root' })
export class TranslateService {

  readonly supported = SUPPORTED_LANGS;
  readonly lang = signal<Lang>(this.loadInitialLang());

  /** Used by the pipe to invalidate caches when the language changes. */
  readonly version = computed(() => this.lang());

  setLang(lang: Lang): void {
    if (lang === this.lang()) return;
    this.lang.set(lang);
    this.persist(lang);
    // Some browsers honour `lang` for hyphenation / font fallbacks.
    if (typeof document !== 'undefined') {
      document.documentElement.lang = lang;
    }
  }

  t(key: string): string {
    const lang = this.lang();
    const dict = DICTIONARIES[lang];
    if (dict && dict[key]) return dict[key];
    // Fallback chain: lang -> en -> raw key.
    if (lang !== 'en' && DICTIONARIES.en[key]) return DICTIONARIES.en[key];
    return key;
  }

  // ── internal ────────────────────────────────────────────────────────────────

  private loadInitialLang(): Lang {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      if (stored === 'en' || stored === 'bn') return stored;
    } catch { /* ignore */ }

    // Reasonable default from the browser. Anything starting with 'bn' → Bangla.
    try {
      const navLang = (navigator?.language ?? '').toLowerCase();
      if (navLang.startsWith('bn')) return 'bn';
    } catch { /* ignore */ }
    return 'en';
  }

  private persist(lang: Lang): void {
    try {
      localStorage.setItem(STORAGE_KEY, lang);
    } catch { /* ignore */ }
  }
}
