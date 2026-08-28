import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { catchError, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import {
  ContactMessage,
  ContactStatus,
  SupportService,
} from '../../core/services/support.service';
import { parseApiError } from '../../core/utils/api-error.util';

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [DatePipe, FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Support inbox</h1>
          <div class="flex items-center gap-2 text-sm">
            <label class="text-gray-500">Filter:</label>
            <select
              [ngModel]="statusFilter()"
              (ngModelChange)="setStatusFilter($event)"
              class="rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm">
              <option value="ALL">All</option>
              <option value="NEW">New</option>
              <option value="READ">Read</option>
              <option value="RESOLVED">Resolved</option>
            </select>
          </div>
        </header>

        <main class="flex-1 overflow-y-auto p-6">
          <div class="max-w-6xl mx-auto grid gap-6 lg:grid-cols-[1fr_2fr]">

            <!-- Inbox list -->
            <section class="bg-white rounded-xl border border-gray-200 overflow-hidden h-fit">
              <header class="border-b border-gray-100 bg-gray-50 px-4 py-2 text-xs uppercase text-gray-400">
                {{ messages().length }} message(s)
              </header>
              <ul class="divide-y divide-gray-50">
                @if (loading()) {
                  <li class="px-4 py-6 text-center text-sm text-gray-400">Loading…</li>
                }
                @for (m of messages(); track m.id) {
                  <li>
                    <button (click)="select(m)" class="w-full px-4 py-3 text-left hover:bg-gray-50"
                      [class.bg-blue-50]="selected()?.id === m.id">
                      <div class="flex items-center justify-between gap-2">
                        <span class="text-sm font-medium" [class.text-gray-900]="m.status === 'NEW'"
                          [class.text-gray-500]="m.status !== 'NEW'">{{ m.name }}</span>
                        <span class="inline-block rounded-full px-2 py-0.5 text-xs font-medium"
                          [class]="badgeClass(m.status)">{{ m.status }}</span>
                      </div>
                      <p class="mt-1 text-xs text-gray-500 truncate">{{ m.subject || m.message }}</p>
                      <p class="text-xs text-gray-400">{{ m.createdAt | date: 'mediumDate' }}</p>
                    </button>
                  </li>
                } @empty {
                  @if (!loading()) {
                    <li class="px-4 py-6 text-center text-sm text-gray-400">No messages yet.</li>
                  }
                }
              </ul>
            </section>

            <!-- Detail -->
            <section class="bg-white rounded-xl border border-gray-200 p-5">
              @if (selected(); as m) {
                <div class="space-y-4">
                  <header class="flex flex-wrap items-start justify-between gap-2">
                    <div>
                      <h2 class="text-lg font-semibold">{{ m.subject || '(No subject)' }}</h2>
                      <p class="text-sm text-gray-500">{{ m.name }} &lt;{{ m.email }}&gt;</p>
                      <p class="text-xs text-gray-400">{{ m.createdAt | date: 'medium' }}</p>
                      @if (m.orderId) {
                        <p class="text-xs text-gray-500">Re: order <span class="font-mono">{{ m.orderId }}</span></p>
                      }
                    </div>
                    <span class="inline-block rounded-full px-2.5 py-1 text-xs font-medium"
                      [class]="badgeClass(m.status)">{{ m.status }}</span>
                  </header>

                  <p class="whitespace-pre-wrap rounded-lg border border-gray-100 bg-gray-50 p-4 text-sm text-gray-800">
                    {{ m.message }}
                  </p>

                  @if (error()) {
                    <p class="rounded-md bg-red-50 px-3 py-2 text-xs text-red-700">{{ error() }}</p>
                  }

                  <div class="flex gap-2 border-t border-gray-100 pt-3">
                    @if (m.status === 'NEW') {
                      <button (click)="setStatus(m, 'READ')" [disabled]="acting()"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                        Mark as read
                      </button>
                    }
                    @if (m.status !== 'RESOLVED') {
                      <button (click)="setStatus(m, 'RESOLVED')" [disabled]="acting()"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg disabled:opacity-50">
                        Mark resolved
                      </button>
                    }
                    <a [href]="'mailto:' + m.email + '?subject=' + replyEncoded(m)" target="_blank" rel="noopener"
                      class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                      Reply via email
                    </a>
                    <button (click)="remove(m)" [disabled]="acting()"
                      class="ml-auto px-3 py-1.5 text-xs font-medium text-red-500 hover:text-red-600">
                      Delete
                    </button>
                  </div>
                </div>
              } @else {
                <p class="text-sm text-gray-400">Select a message to view.</p>
              }
            </section>
          </div>
        </main>
      </div>
    </div>
  `,
})
export class SupportComponent implements OnInit {
  private readonly api = inject(SupportService);

  readonly messages     = signal<ContactMessage[]>([]);
  readonly selected     = signal<ContactMessage | null>(null);
  readonly loading      = signal(false);
  readonly acting       = signal(false);
  readonly error        = signal<string>('');
  readonly statusFilter = signal<string>('ALL');

  ngOnInit(): void {
    this.reload();
  }

  protected setStatusFilter(status: string): void {
    this.statusFilter.set(status);
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list(this.statusFilter()).pipe(catchError(() => of(null))).subscribe((page) => {
      this.loading.set(false);
      if (page) {
        this.messages.set(page.content);
        const sel = this.selected();
        if (sel) {
          const fresh = page.content.find((m) => m.id === sel.id);
          this.selected.set(fresh ?? null);
        }
      }
    });
  }

  protected select(m: ContactMessage): void {
    this.selected.set(m);
    this.error.set('');
    // Auto-mark NEW → READ when viewing.
    if (m.status === 'NEW') {
      this.setStatus(m, 'READ');
    }
  }

  protected setStatus(m: ContactMessage, status: ContactStatus): void {
    this.acting.set(true);
    this.api.updateStatus(m.id, status)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe((updated) => {
        this.acting.set(false);
        if (updated) {
          this.selected.set(updated);
          this.messages.update((list) => list.map((x) => x.id === updated.id ? updated : x));
        }
      });
  }

  protected remove(m: ContactMessage): void {
    if (!confirm('Delete this message?')) return;
    this.acting.set(true);
    this.api.delete(m.id)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return of(null); }))
      .subscribe(() => {
        this.acting.set(false);
        this.messages.update((list) => list.filter((x) => x.id !== m.id));
        if (this.selected()?.id === m.id) this.selected.set(null);
      });
  }

  protected badgeClass(status: ContactStatus): string {
    return status === 'RESOLVED' ? 'bg-emerald-100 text-emerald-700'
         : status === 'READ'     ? 'bg-gray-100 text-gray-600'
         :                         'bg-amber-100 text-amber-700';
  }

  protected replyEncoded(m: ContactMessage): string {
    const subject = m.subject ? `Re: ${m.subject}` : 'Re: Your Erezer enquiry';
    return encodeURIComponent(subject);
  }
}
