import { isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  Directive,
  ElementRef,
  inject,
  Input,
  OnDestroy,
  PLATFORM_ID,
} from '@angular/core';

/**
 * Counts the host element's number up from 0 to `[appCountUp]` the first time it
 * scrolls into view (ease-out, rAF). SSR-safe and respects prefers-reduced-motion
 * (jumps straight to the final value). Writes only the number — wrap any prefix/
 * suffix in sibling spans.
 */
@Directive({
  selector: '[appCountUp]',
  standalone: true,
})
export class CountUpDirective implements AfterViewInit, OnDestroy {
  @Input('appCountUp') target = 0;
  @Input() decimals = 0;
  @Input() durationMs = 1500;

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly platformId = inject(PLATFORM_ID);
  private observer?: IntersectionObserver;
  private rafId?: number;

  ngAfterViewInit(): void {
    const node = this.el.nativeElement;

    const noMotion =
      !isPlatformBrowser(this.platformId) ||
      typeof IntersectionObserver === 'undefined' ||
      (typeof window !== 'undefined' &&
        window.matchMedia?.('(prefers-reduced-motion: reduce)').matches);

    if (noMotion) {
      node.textContent = this.format(this.target);
      return;
    }

    node.textContent = this.format(0);
    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.observer?.unobserve(node);
            this.animate();
          }
        }
      },
      { threshold: 0.4 },
    );
    this.observer.observe(node);
  }

  private animate(): void {
    const start = performance.now();
    const tick = (now: number): void => {
      const t = Math.min((now - start) / this.durationMs, 1);
      const eased = 1 - Math.pow(1 - t, 3); // easeOutCubic
      this.el.nativeElement.textContent = this.format(this.target * eased);
      if (t < 1) this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  private format(value: number): string {
    return value.toFixed(this.decimals);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
    if (this.rafId) cancelAnimationFrame(this.rafId);
  }
}
