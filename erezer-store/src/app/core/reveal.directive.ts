import { isPlatformBrowser } from '@angular/common';
import {
  AfterViewInit,
  Directive,
  ElementRef,
  inject,
  Input,
  OnDestroy,
  PLATFORM_ID,
  Renderer2,
} from '@angular/core';

/**
 * Scroll-reveal: fades + rises the host element when it scrolls into view.
 * Add `appReveal` to any element; pass a stagger index (`[appReveal]="i"`) to
 * cascade items in a grid. SSR-safe and disabled under prefers-reduced-motion
 * (handled in styles.css). The host gets `.reveal` immediately (no flash), then
 * `.is-visible` once observed.
 */
@Directive({
  selector: '[appReveal]',
  standalone: true,
  host: { class: 'reveal' },
})
export class RevealDirective implements AfterViewInit, OnDestroy {
  /** Position within a staggered group; each step adds `revealStep` ms. */
  @Input('appReveal') index: number | string = 0;
  /** Milliseconds of delay per index step. */
  @Input() revealStep = 70;

  private readonly el = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly renderer = inject(Renderer2);
  private readonly platformId = inject(PLATFORM_ID);
  private observer?: IntersectionObserver;

  ngAfterViewInit(): void {
    const node = this.el.nativeElement;

    if (!isPlatformBrowser(this.platformId) || typeof IntersectionObserver === 'undefined') {
      this.renderer.addClass(node, 'is-visible'); // no JS/IO → just show it
      return;
    }

    const idx = Number(this.index) || 0;
    if (idx > 0) {
      this.renderer.setStyle(node, 'transition-delay', `${idx * this.revealStep}ms`);
    }

    this.observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            this.renderer.addClass(node, 'is-visible');
            this.observer?.unobserve(node);
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -8% 0px' },
    );
    this.observer.observe(node);
  }

  ngOnDestroy(): void {
    this.observer?.disconnect();
  }
}
