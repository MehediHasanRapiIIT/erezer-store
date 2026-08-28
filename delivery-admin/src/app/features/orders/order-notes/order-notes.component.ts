import { Component, inject, input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { OrderNote, OrderNoteService } from '../../../core/services/order-note.service';
import { parseApiError } from '../../../core/utils/api-error.util';

@Component({
  selector: 'app-order-notes',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <section class="bg-white rounded-xl border border-gray-200 p-5">
      <header class="mb-3 flex items-center justify-between">
        <div>
          <h2 class="font-bold text-gray-900 text-sm">Internal notes</h2>
          <p class="text-xs text-gray-500">Visible to admins only. Never sent to the customer.</p>
        </div>
      </header>

      @if (error()) {
        <p class="mb-3 rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
      }

      @if (loading()) {
        <p class="text-sm text-gray-400">Loading notes…</p>
      } @else {
        <ul class="space-y-2">
          @for (n of notes(); track n.id) {
            <li class="rounded-lg border border-gray-100 bg-gray-50 px-3 py-2">
              <div class="flex items-start justify-between gap-3">
                <p class="text-sm text-gray-700 whitespace-pre-wrap">{{ n.body }}</p>
                <button (click)="remove(n)" class="act-btn act-btn-delete shrink-0" title="Delete">
                  <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.8"><path stroke-linecap="round" stroke-linejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0"/></svg>
                  Delete
                </button>
              </div>
              <p class="mt-1 text-xs text-gray-400">
                {{ n.author || 'admin' }} &middot; {{ n.createdAt | date: 'medium' }}
              </p>
            </li>
          } @empty {
            <li class="text-xs text-gray-400">No internal notes yet.</li>
          }
        </ul>
      }

      <div class="mt-4 flex gap-2">
        <input
          [(ngModel)]="draft"
          placeholder="Add a note (Enter to save)"
          maxlength="4000"
          (keyup.enter)="add()"
          class="flex-1 rounded-lg border border-gray-200 px-3 py-2 text-sm" />
        <button (click)="add()" [disabled]="adding() || !draft.trim()"
          class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
          {{ adding() ? 'Saving…' : 'Add note' }}
        </button>
      </div>
    </section>
  `,
})
export class OrderNotesComponent implements OnChanges {
  readonly orderId = input.required<string>();

  private readonly api = inject(OrderNoteService);

  readonly notes   = signal<OrderNote[]>([]);
  readonly loading = signal(false);
  readonly adding  = signal(false);
  readonly error   = signal<string>('');

  protected draft = '';

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['orderId']) this.reload();
  }

  reload(): void {
    const id = this.orderId();
    if (!id) return;
    this.loading.set(true);
    this.api.list(id).pipe(catchError(() => of([] as OrderNote[]))).subscribe((list) => {
      this.notes.set(list);
      this.loading.set(false);
    });
  }

  protected add(): void {
    const body = this.draft.trim();
    if (!body) return;
    this.adding.set(true);
    this.error.set('');
    this.api.create(this.orderId(), body)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.adding.set(false); return of(null); }))
      .subscribe((created) => {
        this.adding.set(false);
        if (created) {
          this.notes.update((list) => [created, ...list]);
          this.draft = '';
        }
      });
  }

  protected remove(n: OrderNote): void {
    if (!confirm('Delete this note?')) return;
    this.api.delete(this.orderId(), n.id)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); return of(null); }))
      .subscribe(() => {
        this.notes.update((list) => list.filter((x) => x.id !== n.id));
      });
  }
}
