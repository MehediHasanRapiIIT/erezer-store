import { DatePipe } from '@angular/common';
import { Component, computed, input, OnInit, signal } from '@angular/core';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/** The steps every order goes through, in order. */
export const ORDER_STEPS: { status: string; key: string }[] = [
  { status: 'PLACED',           key: 'timeline.placed' },
  { status: 'ACCEPTED',         key: 'timeline.accepted' },
  { status: 'IN_PRODUCTION',    key: 'timeline.in_production' },
  { status: 'PROCESSING',       key: 'timeline.processing' },
  { status: 'SHIPPED',          key: 'timeline.shipped' },
  { status: 'OUT_FOR_DELIVERY', key: 'timeline.out_for_delivery' },
  { status: 'DELIVERED',        key: 'timeline.delivered' },
];

/** Order statuses after which nothing changes any more. */
export const FINAL_ORDER_STATUSES = ['DELIVERED', 'CANCELLED', 'RETURNED'];

/**
 * The animated order timeline, shared by a signed-in customer's order page and
 * Track Order. It only shows steps and their dates, never anything about the person.
 */
@Component({
  selector: 'app-order-timeline',
  standalone: true,
  imports: [DatePipe, TranslatePipe],
  template: `
    @if (status() === 'CANCELLED') {
      <div class="flex items-center gap-3 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-900/30 dark:text-red-300" data-testid="timeline-cancelled">
        <svg class="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9.75 9.75l4.5 4.5m0-4.5l-4.5 4.5M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>
        <span>{{ 'timeline.cancelled' | t }}@if (cancellationReason()) { {{ 'timeline.reason' | t }}: {{ cancellationReason() }} }</span>
      </div>
    } @else if (status() === 'RETURNED') {
      <div class="flex items-center gap-3 rounded-xl bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:bg-amber-900/30 dark:text-amber-300" data-testid="timeline-returned">
        <svg class="h-5 w-5 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.6"><path stroke-linecap="round" stroke-linejoin="round" d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3"/></svg>
        {{ 'timeline.returned' | t }}
      </div>
    } @else {
      <ol [attr.aria-label]="'timeline.progress' | t">
        @for (step of steps; track step.status; let i = $index; let last = $last) {
          <li class="relative flex gap-4" [class.pb-8]="!last" [attr.data-step]="step.status"
            [attr.data-state]="isCurrent(i) ? 'current' : reached(i) ? 'done' : 'todo'"
            [attr.aria-current]="isCurrent(i) ? 'step' : null">
            <!-- node + connector column -->
            <div class="relative z-10 flex-shrink-0">
              <span class="tl-node" [class.tl-done]="reached(i)" [class.tl-current]="isCurrent(i)"
                [style.animation-delay.ms]="i * 90">
                @if (reached(i) && !isCurrent(i)) {
                  <svg class="tl-check h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2.5"><path stroke-linecap="round" stroke-linejoin="round" d="M5 13l4 4L19 7"/></svg>
                } @else {
                  <span class="h-2.5 w-2.5 rounded-full bg-current"></span>
                }
                @if (isCurrent(i)) {
                  <span class="tl-ping"></span>
                }
              </span>
              @if (!last) {
                <span class="tl-conn">
                  <span class="tl-conn-fill" [class.filled]="segFilled(i)" [style.transition-delay.ms]="i * 120"></span>
                </span>
              }
            </div>
            <!-- content -->
            <div class="pb-1 pt-1">
              <p class="font-medium leading-tight transition-colors" [class.text-neutral-400]="!reached(i)" [class.dark:text-neutral-500]="!reached(i)">
                {{ step.key | t }}
              </p>
              @if (dateFor(step.status); as d) {
                <p class="mt-0.5 text-xs text-neutral-500 dark:text-neutral-400">{{ d | date: 'medium' }}</p>
              } @else if (isCurrent(i)) {
                <p class="mt-0.5 text-xs font-medium text-emerald-600">{{ 'timeline.in_progress' | t }}</p>
              }
            </div>
          </li>
        }
      </ol>
    }
  `,
  styles: [`
    @keyframes nodePop { 0% { transform: scale(.3); opacity: 0; } 60% { transform: scale(1.12); } 100% { transform: scale(1); opacity: 1; } }
    @keyframes checkPop { 0% { transform: scale(0); } 70% { transform: scale(1.2); } 100% { transform: scale(1); } }
    @keyframes tlPing { 0% { transform: scale(1); opacity: .6; } 100% { transform: scale(2.1); opacity: 0; } }

    .tl-node {
      position: relative; display: inline-flex; align-items: center; justify-content: center;
      height: 2.25rem; width: 2.25rem; border-radius: 9999px;
      border: 2px solid rgb(212 212 212); background: white; color: rgb(163 163 163);
      transition: background-color .35s ease, border-color .35s ease, color .35s ease;
      animation: nodePop .45s cubic-bezier(.22,1,.36,1) both;
    }
    :host-context(.dark) .tl-node { background: rgb(23 23 23); border-color: rgb(64 64 64); }
    .tl-done { background: rgb(23 23 23); border-color: rgb(23 23 23); color: white; }
    :host-context(.dark) .tl-done { background: white; border-color: white; color: black; }
    .tl-current { background: rgb(16 185 129); border-color: rgb(16 185 129); color: white; }
    :host-context(.dark) .tl-current { background: rgb(16 185 129); border-color: rgb(16 185 129); color: white; }
    .tl-check { animation: checkPop .4s cubic-bezier(.22,1,.36,1) both; }
    .tl-ping {
      position: absolute; inset: -2px; border-radius: 9999px;
      border: 2px solid rgb(16 185 129); animation: tlPing 1.6s ease-out infinite;
    }

    /* Runs through the step's bottom padding (pb-8) so it meets the next circle. */
    .tl-conn {
      position: absolute; left: 50%; top: 2.25rem; bottom: -2rem; width: 2px;
      transform: translateX(-50%); background: rgb(229 229 229); overflow: hidden;
    }
    :host-context(.dark) .tl-conn { background: rgb(64 64 64); }
    .tl-conn-fill {
      position: absolute; inset: 0 0 auto 0; width: 100%; height: 0;
      background: rgb(23 23 23); transition: height .6s ease;
    }
    :host-context(.dark) .tl-conn-fill { background: white; }
    .tl-conn-fill.filled { height: 100%; }

    @media (prefers-reduced-motion: reduce) {
      .tl-node, .tl-check { animation: none !important; }
      .tl-ping { animation: none !important; opacity: 0; }
      .tl-conn-fill { transition: none !important; }
    }
  `],
})
export class OrderTimelineComponent implements OnInit {
  /** The order's current status. */
  readonly status = input.required<string>();
  /** When each status was reached (status → date). */
  readonly dates = input<Record<string, string>>({});
  /** Fallback date for Placed when the history has none. */
  readonly placedAt = input<string | null | undefined>(null);
  /** Shown with a cancelled order, when there is one. */
  readonly cancellationReason = input<string | null | undefined>(null);

  protected readonly steps = ORDER_STEPS;

  /** Flipped true just after the first paint so the connector fills animate in. */
  private readonly animate = signal(false);

  /** Index of the current step (-1 when the status is not on the path). */
  private readonly currentIndex = computed(() => {
    const s = this.status() === 'PENDING' ? 'PLACED' : this.status();
    return ORDER_STEPS.findIndex((step) => step.status === s);
  });

  ngOnInit(): void {
    if (typeof window !== 'undefined') {
      setTimeout(() => this.animate.set(true), 120);
    } else {
      this.animate.set(true);
    }
  }

  protected reached(i: number): boolean { return this.currentIndex() >= 0 && this.currentIndex() >= i; }
  protected isCurrent(i: number): boolean { return i === this.currentIndex(); }
  /** The line below step i fills once the order has moved past it. */
  protected segFilled(i: number): boolean { return this.animate() && this.currentIndex() > i; }

  protected dateFor(status: string): string | null {
    const d = this.dates()[status];
    if (d) return d;
    return status === 'PLACED' ? (this.placedAt() ?? null) : null;
  }
}
