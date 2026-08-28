import { Component, computed, inject } from '@angular/core';
import { ThemeService } from '../../core/theme.service';

/**
 * Animated sun/moon dark-mode switch. A knob slides between the sun (light) and
 * moon (dark) ends of a pill track; whichever icon the knob is *not* covering
 * stays faintly visible on the rail, so both icons are always shown. Colours use
 * the app's neutral palette with a warm amber sun accent.
 */
@Component({
  selector: 'app-theme-toggle',
  template: `
    <button
      type="button"
      role="switch"
      [attr.aria-checked]="isDark()"
      [attr.aria-label]="isDark() ? 'Switch to light mode' : 'Switch to dark mode'"
      (click)="theme.toggleTheme()"
      class="relative inline-flex h-7 w-[52px] shrink-0 items-center rounded-full border border-neutral-300 bg-neutral-200/80 transition-colors duration-300 hover:border-neutral-400 dark:border-neutral-700 dark:bg-neutral-700/70 dark:hover:border-neutral-600"
    >
      <!-- Rail sun (left) — shown when the knob is on the right (dark) -->
      <svg
        class="pointer-events-none absolute left-1.5 h-3.5 w-3.5 text-amber-500 transition-opacity duration-300"
        [class.opacity-0]="!isDark()"
        fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"
      ><path stroke-linecap="round" stroke-linejoin="round" d="M12 3v2.25m6.364.386l-1.591 1.591M21 12h-2.25m-.386 6.364l-1.591-1.591M12 18.75V21m-4.773-4.227l-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z" /></svg>

      <!-- Rail moon (right) — shown when the knob is on the left (light) -->
      <svg
        class="pointer-events-none absolute right-1.5 h-3.5 w-3.5 text-neutral-400 transition-opacity duration-300"
        [class.opacity-0]="isDark()"
        fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"
      ><path stroke-linecap="round" stroke-linejoin="round" d="M21.752 15.002A9.72 9.72 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z" /></svg>

      <!-- Sliding knob carrying the active icon -->
      <span
        class="relative z-10 flex h-5 w-5 translate-x-1 items-center justify-center rounded-full bg-white shadow-sm ring-1 ring-black/5 transition-transform duration-300 ease-out dark:translate-x-[26px] dark:bg-neutral-950 dark:ring-white/10"
      >
        @if (isDark()) {
          <svg class="h-3 w-3 text-neutral-100" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M21.752 15.002A9.72 9.72 0 0118 15.75c-5.385 0-9.75-4.365-9.75-9.75 0-1.33.266-2.597.748-3.752A9.753 9.753 0 003 11.25C3 16.635 7.365 21 12.75 21a9.753 9.753 0 009.002-5.998z" /></svg>
        } @else {
          <svg class="h-3 w-3 text-amber-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M12 3v2.25m6.364.386l-1.591 1.591M21 12h-2.25m-.386 6.364l-1.591-1.591M12 18.75V21m-4.773-4.227l-1.591 1.591M5.25 12H3m4.227-4.773L5.636 5.636M15.75 12a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0z" /></svg>
        }
      </span>
    </button>
  `
})
export class ThemeToggleComponent {
  protected readonly theme = inject(ThemeService);
  protected readonly isDark = computed(() => this.theme.theme() === 'dark');
}
