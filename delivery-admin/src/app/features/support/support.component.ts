import { Component, computed, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EMPTY, Subject, catchError, debounceTime, distinctUntilChanged, map, of } from 'rxjs';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { PagerComponent } from '../../shared/pager/pager.component';
import {
  ContactMessage,
  ContactStatus,
  SupportService,
} from '../../core/services/support.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

/**
 * The Support inbox. Search, the status filter and paging are all done by the
 * server, so a search covers every message, not just the page on screen.
 */
@Component({
  selector: 'app-support',
  standalone: true,
  imports: [DatePipe, FormsModule, SidebarComponent, PagerComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <h1 class="text-lg font-bold text-gray-900">Support inbox</h1>
          <div class="flex items-center gap-2 text-sm min-w-0">
            <input
              type="search"
              [ngModel]="search()"
              (ngModelChange)="onSearch($event)"
              placeholder="Search by name, email or subject…"
              aria-label="Search messages"
              class="w-56 md:w-72 min-w-0 rounded-lg border border-gray-200 bg-white px-3 py-1.5 text-sm outline-none focus:ring-2 focus:ring-blue-300 placeholder-gray-400" />
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
                {{ total() }} message(s)
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
                    <li class="px-4 py-6 text-center text-sm text-gray-400">
                      {{ searching() ? 'No messages match your search.' : 'No messages yet.' }}
                    </li>
                  }
                }
              </ul>
              @if (total() > 0) {
                <div class="border-t border-gray-100">
                  <app-pager [page]="page()" [size]="pageSize" [total]="total()" [disabled]="loading()" (pageChange)="goToPage($event)" />
                </div>
              }
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
                    @if (m.status === 'NEW' && perms.can('support.update')) {
                      <button (click)="setStatus(m, 'READ')" [disabled]="acting()"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                        Mark as read
                      </button>
                    }
                    @if (m.status !== 'RESOLVED' && perms.can('support.update')) {
                      <button (click)="setStatus(m, 'RESOLVED')" [disabled]="acting()"
                        class="px-3 py-1.5 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-lg disabled:opacity-50">
                        Mark resolved
                      </button>
                    }
                    <a [href]="'mailto:' + m.email + '?subject=' + replyEncoded(m)" target="_blank" rel="noopener"
                      class="px-3 py-1.5 text-xs font-medium text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50">
                      Reply via email
                    </a>
                    @if (perms.can('support.delete')) {
                    <button (click)="remove(m)" [disabled]="acting()"
                      class="ml-auto px-3 py-1.5 text-xs font-medium text-red-500 hover:text-red-600">
                      Delete
                    </button>
                    }
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
export class SupportComponent implements OnInit, OnDestroy {
  private readonly api = inject(SupportService);
  protected readonly perms = inject(PermissionService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);

  protected readonly pageSize = 20;

  readonly messages     = signal<ContactMessage[]>([]);
  readonly selected     = signal<ContactMessage | null>(null);
  readonly loading      = signal(false);
  readonly acting       = signal(false);
  readonly error        = signal<string>('');
  readonly statusFilter = signal<string>('ALL');
  /** Zero-based page on screen, and the number of messages across all pages. */
  readonly page         = signal(0);
  readonly total        = signal(0);
  /** Typed search text, sent to the server after a short pause. */
  readonly search       = signal('');
  protected readonly searching = computed(() => this.search().trim().length > 0);

  private readonly searchSubject = new Subject<string>();
  /** Numbers each request, so an answer that arrives after a newer one is ignored. */
  private latestRequest = 0;

  ngOnInit(): void {
    this.loadPage(0);
    // Typing searches all messages after a short pause, from the first page.
    this.searchSubject.pipe(map((q) => q.trim()), debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.loadPage(0));
  }

  ngOnDestroy(): void {
    this.searchSubject.complete();
  }

  protected onSearch(q: string): void {
    this.search.set(q);
    this.searchSubject.next(q);
  }

  protected setStatusFilter(status: string): void {
    this.statusFilter.set(status);
    this.loadPage(0);
  }

  protected goToPage(page: number): void {
    this.loadPage(page);
  }

  /**
   * One page from the server, for the current search and status filter.
   * `keepSelection` keeps the open message on screen even if it has left the
   * page (e.g. it was just marked read while filtering on "New").
   */
  private loadPage(page: number, keepSelection = false): void {
    const request = ++this.latestRequest;
    this.loading.set(true);
    this.api.list(this.statusFilter(), page, this.pageSize, this.search())
      .pipe(catchError(() => of(null)))
      .subscribe((res) => {
        if (request !== this.latestRequest) return;
        this.loading.set(false);
        if (!res) return;
        // The last row of this page went away (e.g. it was deleted): show the page before it.
        if (res.content.length === 0 && page > 0) {
          this.loadPage(Math.min(page - 1, Math.max(res.totalPages - 1, 0)), keepSelection);
          return;
        }
        this.messages.set(res.content);
        this.page.set(res.number);
        this.total.set(res.totalElements);
        const sel = this.selected();
        if (sel) {
          const fresh = res.content.find((m) => m.id === sel.id);
          this.selected.set(fresh ?? (keepSelection ? sel : null));
        }
      });
  }

  protected select(m: ContactMessage): void {
    this.selected.set(m);
    this.error.set('');
    // Auto-mark NEW → READ when viewing (only for people allowed to change the status).
    if (m.status === 'NEW' && this.perms.can('support.update')) {
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
          this.loadPage(this.page(), true);
        }
      });
  }

  protected async remove(m: ContactMessage): Promise<void> {
    const ok = await this.confirmer.ask({
      title: 'Delete this message?',
      message: "This can't be undone.",
      confirmLabel: 'Delete',
      danger: true,
    });
    if (!ok) return;
    this.acting.set(true);
    this.api.delete(m.id)
      .pipe(catchError((err) => { this.error.set(parseApiError(err)); this.acting.set(false); return EMPTY; }))
      .subscribe(() => {
        this.acting.set(false);
        this.messages.update((list) => list.filter((x) => x.id !== m.id));
        this.notices.success('Message deleted');
        if (this.selected()?.id === m.id) this.selected.set(null);
        this.loadPage(this.page());
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
