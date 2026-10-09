import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/sidebar/sidebar.component';
import { ContentPage, ContentPageService } from '../../core/services/content-page.service';
import { UploadService } from '../../core/services/upload.service';
import { PermissionService } from '../../core/services/permission.service';
import { ConfirmService } from '../../core/services/confirm.service';
import { NoticeService } from '../../core/services/notice.service';
import { parseApiError } from '../../core/utils/api-error.util';

const BLANK: ContentPage = {
  id: null, slug: '', title: '', eyebrow: 'EREZER', intro: '', heroImageUrl: null,
  sections: [{ heading: '', body: '', imageUrl: null }], closing: '', ctaLabel: 'Shop the collection', ctaLink: '/shop',
  showInFooter: true, isActive: true,
};

/**
 * The shop's own pages - "Our Mission", "Our Values", and any other - written
 * here and read by customers. A page is a title, an opening, a list of sections
 * and a closing line; the shop's website lays them out.
 */
@Component({
  selector: 'app-pages',
  standalone: true,
  imports: [FormsModule, SidebarComponent],
  template: `
    <div class="flex h-screen bg-gray-50 overflow-hidden">
      <app-sidebar />

      <div class="flex-1 flex flex-col overflow-hidden">
        <header class="bg-white border-b border-gray-200 px-6 h-14 flex items-center justify-between gap-4 flex-shrink-0">
          <div class="flex items-center gap-3">
            <h1 class="text-lg font-bold text-gray-900">Pages</h1>
            <span class="text-xs text-gray-400">{{ pages().length }} {{ pages().length === 1 ? 'page' : 'pages' }}</span>
          </div>
          @if (canEdit() && !form()) {
            <button type="button" (click)="startNew()" data-testid="page-new"
              class="px-3 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg whitespace-nowrap">+ New page</button>
          }
        </header>

        <main class="flex-1 overflow-y-auto p-6 space-y-5">
          <div class="rounded-xl border border-blue-100 bg-blue-50 px-4 py-3 text-xs text-blue-900">
            <p class="font-semibold">Pages of your own words</p>
            <p class="mt-1">
              Pages like “Our Mission” and “Our Values”. You write the title, the opening, the sections and the closing line here;
              the website lays them out. A page set to “Show in the footer” is linked at the bottom of every page of the shop.
            </p>
          </div>

          @if (error()) {
            <p class="rounded-lg border border-red-200 bg-red-50 px-4 py-2.5 text-sm text-red-700" data-testid="page-error">{{ error() }}</p>
          }
          @if (!canEdit()) {
            <p class="text-xs text-gray-400">Changing pages needs the “Edit the About page” permission.</p>
          }

          <!-- ── the editor ─────────────────────────────────────────────────── -->
          @if (form(); as f) {
            <section class="bg-white rounded-xl border border-blue-200 p-5 space-y-5" data-testid="page-editor">
              <h2 class="text-base font-semibold">{{ f.id ? 'Edit page' : 'New page' }}</h2>

              <div class="grid gap-4 md:grid-cols-2">
                <label class="block text-xs font-medium text-gray-600">
                  Title <span class="text-red-500">*</span>
                  <input [(ngModel)]="f.title" maxlength="160" placeholder="e.g. Our Mission" data-testid="page-title"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="block text-xs font-medium text-gray-600">
                  Small line above the title <span class="font-normal text-gray-400">(optional)</span>
                  <input [(ngModel)]="f.eyebrow" maxlength="80" placeholder="e.g. EREZER"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
              </div>

              <label class="block text-xs font-medium text-gray-600">
                Web address <span class="font-normal text-gray-400">(optional — made from the title when left empty)</span>
                <div class="mt-1 flex items-center rounded-lg border border-gray-200 bg-gray-50 text-sm">
                  <span class="px-3 text-gray-400">/pages/</span>
                  <input [(ngModel)]="f.slug" maxlength="140" [placeholder]="slugPreview(f)" data-testid="page-slug"
                    class="w-full rounded-r-lg border-0 border-l border-gray-200 bg-white px-3 py-2 text-sm" />
                </div>
              </label>

              <label class="block text-xs font-medium text-gray-600">
                Opening
                <textarea [(ngModel)]="f.intro" rows="5" maxlength="4000" data-testid="page-intro"
                  placeholder="The first lines of the page."
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
                <span class="mt-1 block text-[11px] font-normal text-gray-400">Leave an empty line to start a new paragraph. The first paragraph is shown large, under the title.</span>
              </label>

              <!-- Main picture -->
              <div class="space-y-2">
                <p class="text-xs font-medium text-gray-600">Picture behind the title <span class="font-normal text-gray-400">(optional)</span></p>
                <p class="picture-hint text-xs text-gray-500" data-testid="picture-hint"><span class="font-semibold text-gray-700">Best size:</span> 1920 × 800 px, wide. The title sits over it, so a calm picture without text works best. Without one the title is on plain black.</p>
                <div class="flex flex-wrap items-center gap-3">
                  @if (f.heroImageUrl) {
                    <img [src]="f.heroImageUrl" alt="Picture behind the title" class="h-20 w-36 rounded-lg border border-gray-200 object-cover" />
                  }
                  <label class="cursor-pointer rounded-lg border border-blue-200 px-2.5 py-1.5 text-xs font-medium text-blue-600 hover:bg-blue-50">
                    {{ uploading() === 'hero' ? 'Uploading…' : (f.heroImageUrl ? 'Replace picture' : 'Upload picture') }}
                    <input type="file" accept="image/*" class="hidden" aria-label="Upload the picture behind the title"
                      (change)="onImage($event, 'hero')" [disabled]="uploading() !== null" />
                  </label>
                  @if (f.heroImageUrl) {
                    <button type="button" (click)="f.heroImageUrl = null" class="text-xs text-red-600 hover:underline">Remove picture</button>
                  }
                </div>
              </div>

              <!-- Sections -->
              <div class="space-y-3">
                <div class="flex items-center justify-between">
                  <div>
                    <p class="text-xs font-medium text-gray-600">Sections</p>
                    <p class="text-[11px] text-gray-400">Each is a heading with its text. They are shown as a numbered list: 01, 02, 03…</p>
                  </div>
                  @if (f.sections.length < 30) {
                    <button type="button" (click)="addSection()" data-testid="page-add-section"
                      class="rounded-lg border border-blue-200 px-2.5 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50">+ Section</button>
                  }
                </div>
                @for (s of f.sections; track $index; let i = $index) {
                  <div class="rounded-lg border border-gray-100 bg-gray-50/60 p-3 space-y-2" data-testid="page-section">
                    <div class="flex items-center gap-2">
                      <span class="w-7 text-sm font-bold tabular-nums text-gray-400">{{ number(i) }}</span>
                      <input [(ngModel)]="s.heading" maxlength="200" placeholder="Heading" [attr.aria-label]="'Heading of section ' + (i + 1)"
                        data-testid="page-section-heading"
                        class="flex-1 rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm font-semibold" />
                      <button type="button" (click)="move(i, -1)" [disabled]="i === 0" title="Move up" [attr.aria-label]="'Move section ' + (i + 1) + ' up'"
                        class="h-8 w-8 rounded-lg border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 disabled:opacity-30">↑</button>
                      <button type="button" (click)="move(i, 1)" [disabled]="i === f.sections.length - 1" title="Move down" [attr.aria-label]="'Move section ' + (i + 1) + ' down'"
                        class="h-8 w-8 rounded-lg border border-gray-200 bg-white text-gray-500 hover:bg-gray-50 disabled:opacity-30">↓</button>
                      <button type="button" (click)="removeSection(i)" class="act-btn act-btn-delete" title="Remove section">Remove</button>
                    </div>
                    <textarea [(ngModel)]="s.body" rows="3" maxlength="5000" placeholder="Text" [attr.aria-label]="'Text of section ' + (i + 1)"
                      data-testid="page-section-body"
                      class="w-full rounded-lg border border-gray-200 bg-white px-3 py-2 text-sm"></textarea>
                    <div class="flex flex-wrap items-center gap-3">
                      @if (s.imageUrl) {
                        <img [src]="s.imageUrl" [alt]="'Picture of section ' + (i + 1)" class="h-14 w-20 rounded border border-gray-200 object-cover" />
                      }
                      <label class="cursor-pointer rounded-lg border border-blue-200 px-2.5 py-1 text-xs font-medium text-blue-600 hover:bg-blue-50">
                        {{ uploading() === i ? 'Uploading…' : (s.imageUrl ? 'Replace picture' : 'Add a picture (optional)') }}
                        <input type="file" accept="image/*" class="hidden" [attr.aria-label]="'Upload a picture for section ' + (i + 1)"
                          (change)="onImage($event, i)" [disabled]="uploading() !== null" />
                      </label>
                      @if (s.imageUrl) {
                        <button type="button" (click)="s.imageUrl = null" class="text-xs text-red-600 hover:underline">Remove picture</button>
                      } @else {
                        <span class="picture-hint text-[11px] text-gray-400"><span class="font-semibold">Best size:</span> 1000 × 1000 px, square.</span>
                      }
                    </div>
                  </div>
                } @empty {
                  <p class="rounded-lg bg-gray-50 px-3 py-3 text-xs text-gray-500">No sections. The page is its title, opening and closing line.</p>
                }
              </div>

              <label class="block text-xs font-medium text-gray-600">
                Closing line <span class="font-normal text-gray-400">(optional — shown large at the end of the page)</span>
                <textarea [(ngModel)]="f.closing" rows="2" maxlength="1000" data-testid="page-closing"
                  placeholder="e.g. Wear what represents you. Stay original."
                  class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm"></textarea>
              </label>

              <div class="grid gap-4 md:grid-cols-2">
                <label class="block text-xs font-medium text-gray-600">
                  Button under the closing line <span class="font-normal text-gray-400">(optional)</span>
                  <input [(ngModel)]="f.ctaLabel" maxlength="80" placeholder="e.g. Shop the collection"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                </label>
                <label class="block text-xs font-medium text-gray-600">
                  Where the button goes
                  <input [(ngModel)]="f.ctaLink" maxlength="300" placeholder="/shop"
                    class="mt-1 w-full rounded-lg border border-gray-200 px-3 py-2 text-sm" />
                  <span class="mt-1 block text-[11px] font-normal text-gray-400">A page of the shop starts with “/”, like /shop. Leave both boxes empty for no button.</span>
                </label>
              </div>

              <div class="flex flex-wrap gap-x-8 gap-y-2 border-t border-gray-100 pt-4">
                <label class="flex items-center gap-2 text-sm text-gray-700">
                  <input type="checkbox" [(ngModel)]="f.isActive" data-testid="page-active" class="h-4 w-4 rounded border-gray-300 text-blue-600" />
                  Published <span class="text-xs text-gray-400">— customers can open it</span>
                </label>
                <label class="flex items-center gap-2 text-sm text-gray-700">
                  <input type="checkbox" [(ngModel)]="f.showInFooter" data-testid="page-footer" class="h-4 w-4 rounded border-gray-300 text-blue-600" />
                  Show in the footer <span class="text-xs text-gray-400">— a link at the bottom of every page</span>
                </label>
              </div>

              <div class="flex items-center justify-end gap-2 border-t border-gray-100 pt-4">
                <button type="button" (click)="cancel()" class="px-3 py-1.5 text-sm font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50">Cancel</button>
                <button type="button" (click)="save()" [disabled]="saving() || uploading() !== null" data-testid="page-save"
                  class="px-4 py-1.5 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-700 rounded-lg disabled:opacity-50">
                  {{ saving() ? 'Saving…' : (f.id ? 'Save changes' : 'Save page') }}
                </button>
              </div>
            </section>
          }

          <!-- ── the pages ──────────────────────────────────────────────────── -->
          @if (loading()) {
            <p class="py-10 text-center text-sm text-gray-400">Loading…</p>
          } @else if (pages().length === 0) {
            <p class="rounded-xl border border-dashed border-gray-200 bg-white py-12 text-center text-sm text-gray-500">
              No pages yet. Add the first one with “+ New page”.
            </p>
          } @else {
            <div class="grid gap-4 lg:grid-cols-2">
              @for (p of pages(); track p.id) {
                <section class="bg-white rounded-xl border p-4" [class.border-blue-300]="form()?.id === p.id" [class.border-gray-200]="form()?.id !== p.id"
                  data-testid="page-card">
                  <div class="flex flex-wrap items-start justify-between gap-2">
                    <div class="min-w-0">
                      <h2 class="text-sm font-bold text-gray-900">
                        {{ p.title }}
                        @if (!p.isActive) {
                          <span class="ml-1 rounded bg-gray-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-gray-500">Hidden</span>
                        }
                        @if (p.isActive && p.showInFooter) {
                          <span class="ml-1 rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-emerald-700" data-testid="page-in-footer">In the footer</span>
                        }
                      </h2>
                      <p class="mt-0.5 font-mono text-xs text-gray-500" data-testid="page-address">/pages/{{ p.slug }}</p>
                    </div>
                    @if (canEdit()) {
                      <div class="flex gap-1.5">
                        <button type="button" (click)="startEdit(p)" class="act-btn act-btn-edit" data-testid="page-edit">Edit</button>
                        <button type="button" (click)="remove(p)" class="act-btn act-btn-delete" data-testid="page-delete">Delete</button>
                      </div>
                    }
                  </div>
                  @if (p.intro) { <p class="mt-2 line-clamp-2 text-xs text-gray-600">{{ p.intro }}</p> }
                  <p class="mt-2 text-[11px] text-gray-400">
                    {{ p.sections.length }} {{ p.sections.length === 1 ? 'section' : 'sections' }}@if (p.sections.length) {: {{ headings(p) }}}
                  </p>
                </section>
              }
            </div>
          }
        </main>
      </div>
    </div>
  `,
})
export class PagesComponent implements OnInit {
  private readonly service = inject(ContentPageService);
  private readonly uploads = inject(UploadService);
  private readonly confirmer = inject(ConfirmService);
  private readonly notices = inject(NoticeService);
  protected readonly perms = inject(PermissionService);

  pages = signal<ContentPage[]>([]);
  loading = signal(true);
  saving = signal(false);
  error = signal('');
  /** The page being written; null when the editor is closed. */
  form = signal<ContentPage | null>(null);
  /** 'hero', or the number of the section whose picture is going up. */
  uploading = signal<'hero' | number | null>(null);

  ngOnInit(): void {
    this.load();
  }

  canEdit(): boolean {
    return this.perms.can('settings.about');
  }

  private load(): void {
    this.service.list().subscribe({
      next: (all) => { this.pages.set(all); this.loading.set(false); },
      error: (err) => { this.error.set(parseApiError(err)); this.loading.set(false); },
    });
  }

  protected number(index: number): string {
    return String(index + 1).padStart(2, '0');
  }

  protected headings(p: ContentPage): string {
    return p.sections.map((s) => s.heading).filter(Boolean).join(' · ');
  }

  /** The address the title would give, shown faintly in the empty address box. */
  protected slugPreview(f: ContentPage): string {
    return f.title.trim().toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'our-mission';
  }

  startNew(): void {
    this.error.set('');
    this.form.set(structuredClone(BLANK));
  }

  startEdit(p: ContentPage): void {
    this.error.set('');
    this.form.set(structuredClone(p));
    document.querySelector('main')?.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancel(): void {
    this.form.set(null);
  }

  addSection(): void {
    this.form()?.sections.push({ heading: '', body: '', imageUrl: null });
  }

  removeSection(index: number): void {
    this.form()?.sections.splice(index, 1);
  }

  move(index: number, by: number): void {
    const sections = this.form()?.sections;
    if (!sections) return;
    const to = index + by;
    if (to < 0 || to >= sections.length) return;
    [sections[index], sections[to]] = [sections[to], sections[index]];
  }

  onImage(event: Event, where: 'hero' | number): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    const f = this.form();
    if (!file || !f) return;
    this.uploading.set(where);
    this.uploads.uploadImage(file).subscribe({
      next: (url) => {
        if (where === 'hero') f.heroImageUrl = url; else if (f.sections[where]) f.sections[where].imageUrl = url;
        this.uploading.set(null);
      },
      error: (err) => { this.error.set(parseApiError(err)); this.uploading.set(null); },
    });
  }

  save(): void {
    const f = this.form();
    if (!f) return;
    if (!f.title.trim()) { this.error.set('Give the page a title.'); return; }
    const label = (f.ctaLabel ?? '').trim();
    const link = (f.ctaLink ?? '').trim();
    if (!!label !== !!link) { this.error.set('The button needs both its words and where it goes, or leave both empty.'); return; }
    const payload: ContentPage = {
      ...f,
      title: f.title.trim(),
      slug: f.slug.trim(),
      ctaLabel: label || null,
      ctaLink: link || null,
      sections: f.sections.filter((s) => (s.heading ?? '').trim() || (s.body ?? '').trim() || s.imageUrl),
    };
    this.saving.set(true);
    this.error.set('');
    (f.id ? this.service.update(f.id, payload) : this.service.create(payload)).subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.form.set(null);
        this.notices.success(f.id ? 'Page saved' : 'Page added', `${saved.title} — /pages/${saved.slug}`);
        this.load();
      },
      error: (err) => { this.saving.set(false); this.error.set(parseApiError(err)); },
    });
  }

  async remove(p: ContentPage): Promise<void> {
    const ok = await this.confirmer.ask({
      title: `Delete the page “${p.title}”?`,
      message: 'Customers can no longer open it, and its link is taken out of the footer. This cannot be undone.',
      confirmLabel: 'Yes, delete it',
      danger: true,
    });
    if (!ok || p.id == null) return;
    this.service.delete(p.id).subscribe({
      next: () => {
        if (this.form()?.id === p.id) this.form.set(null);
        this.notices.success('Page deleted', p.title);
        this.load();
      },
      error: (err) => this.error.set(parseApiError(err)),
    });
  }
}
