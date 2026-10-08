import { AfterViewInit, Component, ElementRef, OnDestroy, ViewChild, computed, effect, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { catchError, of } from 'rxjs';
import { ApiService } from '../core/api.service';
import { AuthService } from '../core/auth.service';
import { CustomDesignCanvasService, ImageFilterPreset } from '../core/custom-design-canvas.service';
import type {
  CustomDesignColor,
  CustomDesignDraft,
  CustomDesignItem,
  CustomDesignLogo,
  CustomDesignView,
  CustomOrderRequest,
} from '../core/api.models';

interface ViewTab { id: CustomDesignView; label: string; }

/** The panels a phone opens from its bar of tools, one at a time. */
type StudioSheet = 'design' | 'text' | 'product' | 'edit' | 'more';

@Component({
  standalone: true,
  imports: [FormsModule],
  providers: [CustomDesignCanvasService],
  template: `
    <!-- On a phone this page is laid out like a design app: the garment at the
         top where it can always be seen, a bar of tools at the bottom of the
         screen, and each tool's controls in a panel that slides up over the lower
         part of the screen. From 640px up it is the three-column studio. One
         template serves both, because the canvas can only exist once: the phone
         layout is the same elements, re-placed by the styles below. -->
    <section class="studio-bleed mx-auto max-w-[1600px] px-4 py-6 max-sm:-mt-8 max-sm:px-0 max-sm:pb-20 max-sm:pt-2">
      <header class="mb-3 rounded-2xl bg-amber-100 px-6 py-3 text-center dark:bg-amber-950/40 max-sm:sr-only">
        <h1 class="text-lg font-bold tracking-wide sm:text-xl max-sm:text-[11px] max-sm:leading-tight">DESIGN YOUR OWN CLOTHING IN JUST MINUTES</h1>
        <p class="app-muted mt-1 text-sm max-sm:hidden">No minimum order — even a single piece. Design t-shirts, hoodies and more, then submit for a price.</p>
      </header>

      @if (loading()) {
        <p class="app-muted py-2 text-center text-sm">Loading the design studio…</p>
      }
      @if (error()) {
        <p class="rounded-xl border border-red-200 bg-red-50 p-4 text-center text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">{{ error() }}</p>
      }
      <!-- The grid (and canvas) must stay VISIBLE in the DOM: fabric initialised on a
           display:none canvas can't measure it and paints nothing. We dim it while
           loading instead of hiding it. -->
      <!-- minmax(0,1fr) on the single column too: a bare column grows to fit the
           canvas, which pushed the page wider than a phone's screen. -->
      <div class="grid grid-cols-[minmax(0,1fr)] gap-4 max-sm:gap-2 lg:grid-cols-[240px_minmax(0,1fr)_248px]" [class.opacity-50]="loading()">

          <!-- ── Left: tools ─────────────────────────────────────────────── -->
          <aside class="space-y-3 max-sm:order-3">
            <div class="grid grid-cols-2 gap-3 max-sm:hidden">
              <label class="app-card flex cursor-pointer flex-col items-center gap-1 p-4 text-center text-xs font-semibold">
                <input type="file" accept="image/*" class="hidden" (change)="onFileSelected($event)" [disabled]="uploading()" />
                <span class="text-lg">🖼️</span>
                {{ uploading() ? 'UPLOADING…' : 'ADD DESIGN' }}
              </label>
              <button type="button" (click)="focusText()" class="app-card flex flex-col items-center gap-1 p-4 text-center text-xs font-semibold">
                <span class="text-lg">T</span>
                ADD TEXT
              </button>
            </div>

            <!-- Add text row -->
            <div class="app-card studio-sheet space-y-2 p-3" [class.studio-sheet-closed]="sheet() !== 'text'" data-testid="sheet-text">
              <div class="studio-sheet-head sm:hidden">
                <span>Add text</span>
                <button type="button" (click)="closeSheet()" class="studio-sheet-done">Done</button>
              </div>
              <input #textInput [(ngModel)]="newText" name="newText" placeholder="Type text…" maxlength="120"
                (keyup.enter)="newText.trim() && addText()"
                class="w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
              <div class="flex items-center gap-2">
                <input type="color" value="#111111" [(ngModel)]="textColor" name="textColor" (change)="applyTextColor()" class="h-8 w-10 cursor-pointer rounded" />
                <button type="button" (click)="addText()" class="btn-secondary flex-1 py-1.5 text-sm" [disabled]="!newText.trim()">Add text</button>
              </div>
            </div>

            <!-- Contextual: selected element tools -->
            @if (canvas.active(); as a) {
              <div class="app-card studio-sheet space-y-3 p-3" [class.studio-sheet-closed]="sheet() !== 'edit'" data-testid="sheet-edit">
                <div class="studio-sheet-head sm:hidden">
                  <span>Edit {{ a.kind === 'text' ? 'text' : a.kind === 'image' ? 'picture' : 'item' }}</span>
                  <button type="button" (click)="closeSheet()" class="studio-sheet-done">Done</button>
                </div>
                <span class="text-xs font-semibold uppercase tracking-wide max-sm:hidden">
                  {{ a.kind === 'text' ? 'Text' : a.kind === 'image' ? 'Image' : 'Element' }} options
                </span>

                @if (a.kind === 'text') {
                  <select [ngModel]="a.fontFamily.split(',')[0].trim()" (ngModelChange)="canvas.setFontFamily($event)"
                    class="w-full rounded-lg border border-neutral-300 px-2 py-1.5 text-sm dark:border-neutral-700 dark:bg-neutral-900"
                    [style.font-family]="a.fontFamily">
                    @for (g of fontGroups; track g.label) {
                      <optgroup [label]="g.label">
                        @for (f of g.fonts; track f) {
                          <option [value]="f" [style.font-family]="f">{{ f }}</option>
                        }
                      </optgroup>
                    }
                  </select>

                  <div class="flex items-center gap-2">
                    <label class="flex flex-1 items-center gap-1 text-xs">Size
                      <input type="number" min="6" max="200" [ngModel]="a.fontSize" (ngModelChange)="canvas.setFontSize($event)"
                        class="w-16 rounded-lg border border-neutral-300 px-2 py-1 text-sm dark:border-neutral-700 dark:bg-neutral-900" />
                    </label>
                    <button type="button" (click)="canvas.toggleBold()" title="Bold"
                      class="h-8 w-8 rounded-lg border text-sm font-bold" [class.bg-neutral-900]="a.bold" [class.text-white]="a.bold"
                      [class.border-neutral-300]="!a.bold" [class.dark:border-neutral-700]="!a.bold">B</button>
                    <button type="button" (click)="canvas.toggleItalic()" title="Italic"
                      class="h-8 w-8 rounded-lg border text-sm italic" [class.bg-neutral-900]="a.italic" [class.text-white]="a.italic"
                      [class.border-neutral-300]="!a.italic" [class.dark:border-neutral-700]="!a.italic">I</button>
                    <button type="button" (click)="canvas.toggleUnderline()" title="Underline"
                      class="h-8 w-8 rounded-lg border text-sm underline" [class.bg-neutral-900]="a.underline" [class.text-white]="a.underline"
                      [class.border-neutral-300]="!a.underline" [class.dark:border-neutral-700]="!a.underline">U</button>
                  </div>

                  <div class="flex items-center gap-2">
                    <div class="flex overflow-hidden rounded-lg border border-neutral-300 dark:border-neutral-700">
                      @for (al of ['left','center','right']; track al) {
                        <button type="button" (click)="canvas.setTextAlign($any(al))" class="px-2.5 py-1 text-xs"
                          [class.bg-neutral-900]="a.textAlign === al" [class.text-white]="a.textAlign === al">
                          {{ al === 'left' ? '⇤' : al === 'center' ? '≡' : '⇥' }}
                        </button>
                      }
                    </div>
                    <label class="flex items-center gap-1 text-xs">Fill
                      <input type="color" [ngModel]="a.fill" (ngModelChange)="canvas.setActiveTextColor($event)" class="h-7 w-8 cursor-pointer rounded" />
                    </label>
                  </div>

                  <div class="flex items-center gap-2 text-xs">
                    <span>Outline</span>
                    <input type="color" [value]="a.strokeColor" (change)="onStroke($event, a.strokeWidth)" class="h-7 w-8 cursor-pointer rounded" />
                    <input type="range" min="0" max="8" step="0.5" [ngModel]="a.strokeWidth" (ngModelChange)="canvas.setTextStroke(a.strokeColor, $event)" class="flex-1" />
                  </div>

                  <!-- Letter spacing: fabric measures it in 1/1000 em, so the
                       slider range is wide but the label shows plain steps. -->
                  <label class="flex items-center gap-2 text-xs">
                    <span class="w-14 shrink-0">Spacing</span>
                    <input type="range" min="-50" max="600" step="10" [ngModel]="a.charSpacing"
                      (ngModelChange)="canvas.setCharSpacing($event)" class="flex-1" />
                  </label>

                  <label class="flex items-center gap-2 text-xs">
                    <span class="w-14 shrink-0">Line gap</span>
                    <input type="range" min="0.7" max="2.4" step="0.05" [ngModel]="a.lineHeight"
                      (ngModelChange)="canvas.setLineHeight($event)" class="flex-1" />
                  </label>

                  <div class="flex items-center gap-2 text-xs">
                    <button type="button" (click)="canvas.setShadow(!a.shadow, a.shadowColor)"
                      class="rounded-lg border px-2 py-1" [class.bg-neutral-900]="a.shadow" [class.text-white]="a.shadow"
                      [class.border-neutral-300]="!a.shadow" [class.dark:border-neutral-700]="!a.shadow">Shadow</button>
                    @if (a.shadow) {
                      <input type="color" [value]="a.shadowColor" (change)="onShadowColor($event)"
                        title="Shadow colour" class="h-7 w-8 cursor-pointer rounded" />
                    }
                    <span class="ml-auto flex overflow-hidden rounded-lg border border-neutral-300 dark:border-neutral-700">
                      <button type="button" (click)="canvas.transformCase('upper')" title="UPPERCASE" class="px-2 py-1">AA</button>
                      <button type="button" (click)="canvas.transformCase('title')" title="Title Case" class="px-2 py-1">Aa</button>
                      <button type="button" (click)="canvas.transformCase('lower')" title="lowercase" class="px-2 py-1">aa</button>
                    </span>
                  </div>
                }

                @if (a.kind === 'image') {
                  <div class="flex gap-2">
                    <button type="button" (click)="canvas.flipHorizontal()" class="btn-secondary flex-1 py-1.5 text-xs">Flip ⇆</button>
                    <button type="button" (click)="canvas.flipVertical()" class="btn-secondary flex-1 py-1.5 text-xs">Flip ⇅</button>
                  </div>
                  <button type="button" (click)="removeBg()" [disabled]="removingBg()" class="btn-secondary w-full py-1.5 text-xs">
                    {{ removingBg() ? 'Removing…' : 'Remove background' }}
                  </button>
                  @if (removeBgError()) { <p class="text-xs text-red-500" data-testid="remove-bg-note">{{ removeBgError() }}</p> }

                  <!-- Filter presets. Rebuilding the whole fabric filter stack
                       on each change, so these are mutually exclusive. -->
                  <div class="flex overflow-hidden rounded-lg border border-neutral-300 text-xs dark:border-neutral-700">
                    @for (f of imageFilters; track f.id) {
                      <button type="button" (click)="canvas.setImageFilter(f.id)" class="flex-1 px-1 py-1"
                        [class.bg-neutral-900]="a.filterPreset === f.id" [class.text-white]="a.filterPreset === f.id">{{ f.label }}</button>
                    }
                  </div>

                  <label class="flex items-center gap-2 text-xs">
                    <span class="w-14 shrink-0">Bright</span>
                    <input type="range" min="-0.6" max="0.6" step="0.05" [ngModel]="a.brightness"
                      (ngModelChange)="canvas.setBrightness($event)" class="flex-1" />
                  </label>
                  <label class="flex items-center gap-2 text-xs">
                    <span class="w-14 shrink-0">Contrast</span>
                    <input type="range" min="-0.6" max="0.6" step="0.05" [ngModel]="a.contrast"
                      (ngModelChange)="canvas.setContrast($event)" class="flex-1" />
                  </label>
                  <label class="flex items-center gap-2 text-xs">
                    <span class="w-14 shrink-0">Saturate</span>
                    <input type="range" min="-1" max="1" step="0.05" [ngModel]="a.saturation"
                      (ngModelChange)="canvas.setSaturation($event)" class="flex-1" />
                  </label>
                  <button type="button" (click)="canvas.resetAdjustments()" class="btn-secondary w-full py-1.5 text-xs">Reset adjustments</button>
                }

                <!-- Applies to any element, not just images. -->
                <div class="flex items-center gap-2 text-xs">
                  <span class="w-14 shrink-0">Rotate</span>
                  <button type="button" (click)="canvas.rotateBy(-90)" title="Rotate left" class="rounded-lg border border-neutral-300 px-2 py-1 dark:border-neutral-700">↺</button>
                  <input type="range" min="0" max="359" step="1" [ngModel]="a.angle"
                    (ngModelChange)="canvas.setAngle($event)" class="flex-1" />
                  <button type="button" (click)="canvas.rotateBy(90)" title="Rotate right" class="rounded-lg border border-neutral-300 px-2 py-1 dark:border-neutral-700">↻</button>
                </div>

                <div class="flex gap-2">
                  <button type="button" (click)="canvas.bringForward()" class="btn-secondary flex-1 py-1.5 text-xs" title="Bring forward">Forward ↑</button>
                  <button type="button" (click)="canvas.sendBackward()" class="btn-secondary flex-1 py-1.5 text-xs" title="Send backward">Back ↓</button>
                </div>
                <button type="button" (click)="canvas.fitToPrintArea()" class="btn-secondary w-full py-1.5 text-xs">Fit to print area</button>

                <label class="flex items-center gap-2 text-xs">Opacity
                  <input type="range" min="0.1" max="1" step="0.05" [ngModel]="a.opacity" (ngModelChange)="canvas.setOpacity($event)" class="flex-1" />
                </label>
              </div>
            }

            <!-- Selectors -->
            <!-- studio-last: with no logo library the panel after this one is hidden
                 in the studio, and this card must end the column as it always did. -->
            <div class="app-card studio-sheet space-y-3 p-3" [class.studio-sheet-closed]="sheet() !== 'product'"
              [class.studio-last]="!logos().length" data-testid="sheet-product">
              <div class="studio-sheet-head sm:hidden">
                <span>Product</span>
                <button type="button" (click)="closeSheet()" class="studio-sheet-done">Done</button>
              </div>
              <label class="block text-xs font-semibold uppercase tracking-wide">
                Item
                <select [ngModel]="selectedItemName()" (ngModelChange)="onItemChange($event)" name="item"
                  class="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900">
                  @for (it of items(); track it.name) { <option [value]="it.name">{{ it.name }}</option> }
                </select>
              </label>

              <div>
                <span class="text-xs font-semibold uppercase tracking-wide">Colour</span>
                <div class="mt-1 flex flex-wrap gap-2">
                  @for (c of selectedItem()?.colors ?? []; track c.name) {
                    <button type="button" (click)="onColorChange(c.name)" [title]="c.name"
                      class="h-7 w-7 rounded-full border-2 transition"
                      [style.background-color]="c.hex"
                      [class.border-black]="selectedColorName() === c.name"
                      [class.dark:border-white]="selectedColorName() === c.name"
                      [class.border-neutral-300]="selectedColorName() !== c.name"></button>
                  }
                </div>
              </div>

              <label class="block text-xs font-semibold uppercase tracking-wide">
                Size
                <select [(ngModel)]="selectedSize" name="size"
                  class="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900">
                  @for (s of selectedItem()?.sizes ?? []; track s) { <option [value]="s">{{ s }}</option> }
                </select>
              </label>

              <label class="block text-xs font-semibold uppercase tracking-wide">
                Print technique
                <select [(ngModel)]="selectedTechnique" name="tech"
                  class="mt-1 w-full rounded-lg border border-neutral-300 px-3 py-2 text-sm dark:border-neutral-700 dark:bg-neutral-900">
                  @for (t of selectedItem()?.printTechniques ?? []; track t) { <option [value]="t">{{ t }}</option> }
                </select>
              </label>
            </div>

            <!-- Logo library; on a phone this panel also holds the upload button,
                 so it is there (as a panel) even when the library is empty. -->
            <div class="app-card studio-sheet p-3" [class.studio-sheet-closed]="sheet() !== 'design'"
              [class.sm:hidden]="!logos().length" data-testid="sheet-design">
              <div class="studio-sheet-head sm:hidden">
                <span>Add a design</span>
                <button type="button" (click)="closeSheet()" class="studio-sheet-done">Done</button>
              </div>
              <label class="btn-primary mb-3 flex w-full cursor-pointer items-center justify-center gap-2 !rounded-xl py-3 text-sm font-semibold sm:hidden">
                <input type="file" accept="image/*" class="hidden" (change)="onFileSelected($event)" [disabled]="uploading()" data-testid="phone-upload" />
                {{ uploading() ? 'Uploading…' : 'Upload a picture from your phone' }}
              </label>
              @if (logos().length) {
                <span class="text-xs font-semibold uppercase tracking-wide">Logo library</span>
                <div class="mt-2 grid grid-cols-3 gap-2 max-sm:grid-cols-4">
                  @for (logo of logos(); track logo.url) {
                    <button type="button" (click)="addLogo(logo.url)" [title]="logo.name"
                      class="aspect-square overflow-hidden rounded-lg border border-neutral-200 p-1 dark:border-neutral-700">
                      <img [src]="logo.url" [alt]="logo.name" class="h-full w-full object-contain" />
                    </button>
                  }
                </div>
              } @else {
                <p class="app-muted text-center text-xs sm:hidden">PNG or JPG. A picture with a clear background looks best on the garment.</p>
              }
            </div>
          </aside>

          <!-- ── Center: canvas ───────────────────────────── -->
          <!-- position:relative so the toolbar can float over the stage instead of
               occupying its own row above it - that row cost ~66px of height,
               which on a laptop is the difference between the whole garment
               being visible and having to scroll for it. -->
          <div class="relative flex justify-center rounded-2xl border border-neutral-200 bg-neutral-50 p-4 dark:border-neutral-800 dark:bg-neutral-900 max-sm:order-1 max-sm:flex-col max-sm:items-center max-sm:rounded-xl max-sm:p-1" data-testid="studio-stage">
            <div class="studio-actions pointer-events-auto absolute right-6 top-6 z-10 flex items-center gap-1 rounded-full bg-neutral-900/90 px-3 py-2 text-white shadow-lg backdrop-blur">
              <button type="button" (click)="canvas.setZoom(canvas.zoom() + 0.1)" title="Zoom in" class="rounded-full p-2 hover:bg-white/10">＋</button>
              <button type="button" (click)="canvas.setZoom(canvas.zoom() - 0.1)" title="Zoom out" class="rounded-full p-2 hover:bg-white/10">－</button>
              <button type="button" (click)="canvas.duplicateActive()" [disabled]="!canvas.hasSelection()" title="Duplicate" class="rounded-full p-2 hover:bg-white/10 disabled:opacity-40">⧉</button>
              <button type="button" (click)="canvas.undo()" [disabled]="!canvas.canUndo()" title="Undo" class="rounded-full p-2 hover:bg-white/10 disabled:opacity-40">↶</button>
              <button type="button" (click)="canvas.redo()" [disabled]="!canvas.canRedo()" title="Redo" class="rounded-full p-2 hover:bg-white/10 disabled:opacity-40">↷</button>
              <button type="button" (click)="canvas.deleteActive()" [disabled]="!canvas.hasSelection()" title="Delete" class="rounded-full bg-red-500 p-2 hover:bg-red-600 disabled:opacity-40">🗑</button>
              <span class="mx-1 h-5 w-px bg-white/20"></span>
              <button type="button" (click)="canvas.toggleGuide()" title="Toggle printable area"
                class="rounded-full px-2 py-1 text-[11px] hover:bg-white/10">
                {{ canvas.showGuide() ? '▣ area' : '▢ area' }}
              </button>
            </div>
            <!-- IMPORTANT: no background/class styling on the canvas element itself.
                 Fabric copies the element's className + inline style onto the
                 transparent "upper canvas" it overlays for selection — any
                 background here becomes an opaque layer hiding everything drawn. -->
            <canvas #canvasEl></canvas>
          </div>

          <!-- ── Right: views + actions ───────────────────────────────────── -->
          <aside class="space-y-3 max-sm:order-2 max-sm:space-y-2">
            <div class="grid grid-cols-2 gap-2 max-sm:grid-cols-4 max-sm:gap-1.5" data-testid="studio-views">
              @for (tab of viewTabs; track tab.id) {
                <button type="button" (click)="selectView(tab.id)"
                  class="relative rounded-xl border-2 bg-neutral-50 p-1.5 transition hover:border-neutral-400 dark:bg-neutral-900 max-sm:p-1"
                  [class.border-black]="view() === tab.id"
                  [class.dark:border-white]="view() === tab.id"
                  [class.border-neutral-200]="view() !== tab.id"
                  [class.dark:border-neutral-700]="view() !== tab.id">
                  <span class="flex h-16 items-center justify-center overflow-hidden max-sm:h-9">
                    @if (viewThumb(tab.id); as thumb) {
                      <img [src]="thumb" [alt]="tab.label" class="max-h-full max-w-full object-contain" />
                    } @else {
                      <!-- No mockup uploaded for this view yet; keep the tab usable. -->
                      <span class="text-[10px] leading-tight opacity-40">no
                        <br />mockup</span>
                    }
                  </span>
                  <span class="mt-1 block truncate text-center text-[11px] font-medium lowercase max-sm:mt-0.5 max-sm:text-[10px]">{{ tab.label }}</span>
                  @if (!canvas.isViewEmpty(tab.id)) {
                    <span class="absolute right-1.5 top-1.5 h-2 w-2 rounded-full bg-green-500"
                      title="This view has artwork"></span>
                  }
                </button>
              }
            </div>

            <div class="studio-sheet space-y-3" [class.studio-sheet-closed]="sheet() !== 'more'" data-testid="sheet-more">
            <div class="studio-sheet-head sm:hidden">
                <span>Save and share</span>
                <button type="button" (click)="closeSheet()" class="studio-sheet-done">Done</button>
              </div>
            <div class="grid grid-cols-3 gap-2">
              <button type="button" (click)="saveDraft()" [disabled]="savingDraft()" class="app-card p-2 text-center text-[11px] font-semibold">💾<br>{{ draftId() ? 'update' : 'save' }}</button>
              <button type="button" (click)="toggleDrafts()" class="app-card p-2 text-center text-[11px] font-semibold">📁<br>drafts</button>
              <button type="button" (click)="share()" [disabled]="!draftId()" class="app-card p-2 text-center text-[11px] font-semibold disabled:opacity-40">🔗<br>share</button>
            </div>

            @if (shareUrl()) {
              <div class="app-card space-y-1 p-3 text-xs">
                <span class="app-muted">Share link</span>
                <input readonly [value]="shareUrl()" class="w-full rounded border border-neutral-300 px-2 py-1 dark:border-neutral-700 dark:bg-neutral-900" />
              </div>
            }

            @if (showDrafts()) {
              <div class="app-card max-h-64 space-y-2 overflow-auto p-3">
                <span class="text-xs font-semibold uppercase tracking-wide">Saved designs</span>
                @if (!auth.isAuthenticated()) {
                  <p class="app-muted text-xs">Sign in to save and reload designs.</p>
                } @else if (!drafts().length) {
                  <p class="app-muted text-xs">No saved designs yet.</p>
                } @else {
                  @for (d of drafts(); track d.id) {
                    <button type="button" (click)="openDraft(d)" class="flex w-full items-center gap-2 rounded-lg border border-neutral-200 p-2 text-left text-xs dark:border-neutral-700">
                      @if (d.thumbnailUrl) { <img [src]="d.thumbnailUrl" class="h-8 w-8 rounded object-cover" alt="" /> }
                      <span class="truncate">{{ d.name }}</span>
                    </button>
                  }
                }
              </div>
            }
            </div>

            <button type="button" (click)="openSubmit()" data-testid="studio-submit" class="btn-primary w-full py-3 text-sm font-bold max-sm:!rounded-xl max-sm:py-2.5">SUBMIT FOR PRICE</button>
          </aside>
      </div>
    </section>

    <!-- ── Phone: the bar of tools, always under the thumb ───────────────────── -->
    <nav class="studio-toolbar sm:hidden" aria-label="Design tools" data-testid="studio-toolbar">
      @for (tool of phoneTools; track tool.id) {
        <button type="button" (click)="toggleSheet(tool.id)"
          [disabled]="tool.id === 'edit' && !canvas.active()"
          [attr.aria-pressed]="sheet() === tool.id" [attr.data-testid]="'tool-' + tool.id"
          class="studio-tool" [class.studio-tool-on]="sheet() === tool.id">
          <span class="studio-tool-icon" aria-hidden="true">{{ tool.icon }}</span>
          <span>{{ tool.label }}</span>
          @if (tool.id === 'edit' && canvas.active() && sheet() !== 'edit') {
            <span class="studio-tool-dot" aria-hidden="true"></span>
          }
        </button>
      }
    </nav>

    <!-- ── Submit for price modal ───────────────────────────────────────────── -->
    @if (showSubmit()) {
      <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 max-sm:items-end max-sm:p-0" (click)="closeSubmit()">
        <div class="max-h-[90vh] w-full max-w-2xl overflow-auto rounded-2xl bg-white p-6 dark:bg-neutral-900 max-sm:max-h-[92dvh] max-sm:rounded-b-none max-sm:p-4" (click)="$event.stopPropagation()">
          @if (submittedRef()) {
            <div class="space-y-4 py-8 text-center">
              <h2 class="text-xl font-bold">Request sent 🎉</h2>
              <p class="app-muted text-sm">Your reference is <strong>{{ submittedRef() }}</strong>. Our team will email you a price shortly.</p>
              <button type="button" (click)="closeSubmit()" class="btn-primary">Done</button>
            </div>
          } @else {
            <h2 class="mb-4 text-center text-xl font-bold">Submit Custom Design Request</h2>
            <div class="grid gap-3 sm:grid-cols-2">
              <input [(ngModel)]="form.firstName" placeholder="First Name *" class="cd-input" />
              <input [(ngModel)]="form.lastName" placeholder="Last Name *" class="cd-input" />
              <input [(ngModel)]="form.phone" placeholder="Phone *" class="cd-input" />
              <input [(ngModel)]="form.email" type="email" placeholder="Email *" class="cd-input" />
              <input [(ngModel)]="form.shippingAddress" placeholder="Shipping Address *" class="cd-input sm:col-span-2" />
              <input [(ngModel)]="form.apartment" placeholder="Apartment, suite, etc. (Optional)" class="cd-input" />
              <input [(ngModel)]="form.city" placeholder="City *" class="cd-input" />
              <input [(ngModel)]="form.zipCode" placeholder="Zip Code" class="cd-input" />
              <input [(ngModel)]="form.country" placeholder="Country *" class="cd-input" />
            </div>

            <div class="mt-4">
              <span class="text-xs font-semibold uppercase tracking-wide">Size-wise quantity *</span>
              <div class="mt-1 grid grid-cols-3 gap-2 sm:grid-cols-4">
                @for (s of orderSizes; track s) {
                  <label class="flex items-center justify-between gap-2 rounded-lg border border-neutral-200 px-2 py-1.5 text-sm dark:border-neutral-700">
                    <span class="font-medium">{{ s }}</span>
                    <input type="number" min="0" [(ngModel)]="sizeQty[s]"
                      class="w-14 rounded border border-neutral-300 px-2 py-1 text-sm dark:border-neutral-600 dark:bg-neutral-900" />
                  </label>
                }
              </div>
              <p class="app-muted mt-1 text-xs">Total pieces: {{ totalQty }}</p>
            </div>

            <div class="mt-3">
              <span class="text-xs font-semibold uppercase tracking-wide">Print / Embroidery technique *</span>
              <select [(ngModel)]="submitTechnique" class="cd-input mt-1 w-full">
                @for (t of selectedItem()?.printTechniques ?? []; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>

            <div class="mt-3">
              <span class="text-xs font-semibold uppercase tracking-wide">Additional notes</span>
              <textarea [(ngModel)]="notesExtra" rows="3"
                placeholder="Anything else we should know (deadlines, placement, colours…)"
                class="cd-input mt-1 w-full"></textarea>
            </div>

            @if (submitError()) {
              <p class="mt-3 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-600 dark:border-red-800 dark:bg-red-950 dark:text-red-400">{{ submitError() }}</p>
            }

            <div class="mt-5 flex gap-3">
              <button type="button" (click)="closeSubmit()" class="btn-secondary flex-1">Cancel</button>
              <button type="button" (click)="submit()" [disabled]="submitting()" class="btn-primary flex-1">{{ submitting() ? 'Sending…' : 'Send' }}</button>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    .cd-input {
      border-radius: 0.75rem;
      border: 1px solid rgb(212 212 212);
      padding: 0.6rem 0.9rem;
      font-size: 0.875rem;
      background: #fafafa;
    }
    :host-context(.dark) .cd-input { background: rgb(23 23 23); border-color: rgb(64 64 64); }

    /* The phone-only pieces take no part in the studio from 640px up. */
    .studio-sheet-head, .studio-toolbar { display: none; }
    .studio-last { margin-bottom: 0 !important; }

    /*
     * ── Phone layout ───────────────────────────────────────────────────────
     * Tool groups that are cards down the side of the studio become panels
     * fixed to the bottom of the screen, one open at a time, above the bar of
     * tools. The garment stays at the top of the page, in view above them.
     */
    @media (max-width: 639px) {
      .studio-toolbar {
        position: fixed; inset-inline: 0; bottom: 0; z-index: 40;
        display: grid; grid-template-columns: repeat(5, minmax(0, 1fr));
        padding-bottom: env(safe-area-inset-bottom);
        border-top: 1px solid rgb(229 229 229); background: #fff;
        box-shadow: 0 -6px 18px rgb(0 0 0 / 0.08);
      }
      .studio-tool {
        position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center;
        gap: 1px; height: 56px; font-size: 11px; font-weight: 600; color: rgb(64 64 64);
      }
      .studio-tool:disabled { opacity: 0.35; }
      .studio-tool-icon { font-size: 19px; line-height: 1.1; }
      .studio-tool-on { color: #000; background: rgb(245 245 245); box-shadow: inset 0 2px 0 #000; }
      .studio-tool-dot {
        position: absolute; top: 8px; left: calc(50% + 10px); width: 8px; height: 8px;
        border-radius: 9999px; background: rgb(16 185 129);
      }

      .studio-sheet {
        position: fixed; inset-inline: 0; z-index: 39;
        bottom: calc(56px + env(safe-area-inset-bottom));
        max-height: 42dvh; overflow-y: auto; overscroll-behavior: contain;
        margin: 0 !important; padding: 0 16px 16px;
        border: 0; border-top: 1px solid rgb(229 229 229); border-radius: 18px 18px 0 0;
        background: #fff; box-shadow: 0 -10px 28px rgb(0 0 0 / 0.18);
        animation: studioSheetUp 0.2s ease-out;
      }
      .studio-sheet-closed { display: none; }
      .studio-sheet-head {
        position: sticky; top: 0; z-index: 1; display: flex; align-items: center; justify-content: space-between;
        margin: 0 -16px 4px; padding: 10px 16px; background: inherit;
        font-size: 14px; font-weight: 700; border-bottom: 1px solid rgb(240 240 240);
      }
      .studio-sheet-done {
        min-height: 44px !important; padding: 0 16px; border-radius: 9999px;
        background: #000; color: #fff; font-size: 13px; font-weight: 600;
      }

      /* Controls a finger can hit: 44px tall, and text that doesn't make the
         phone zoom in when a box is tapped (it does below 16px). */
      .studio-sheet button, .studio-sheet select { min-height: 44px; min-width: 44px; }
      .studio-sheet input:not([type='range']):not([type='color']):not([type='file']),
      .studio-sheet select { min-height: 44px; font-size: 16px; }
      .studio-sheet input[type='color'] { height: 44px; width: 52px; }
      .studio-sheet input[type='range'] { height: 36px; }
      .studio-sheet .text-xs, .studio-sheet .text-\\[11px\\] { font-size: 13px; line-height: 1.35; }

      /* The strip of actions: a row of its own above the garment (floating, it
         covered the collar on a screen this narrow), finger-sized. */
      .studio-actions {
        position: static; align-self: stretch; margin-bottom: 4px; justify-content: space-between;
        padding: 2px 6px; gap: 0; border-radius: 10px;
      }
      .studio-actions button { min-width: 40px; min-height: 40px; }
      .studio-actions > span { display: none; }

      :host-context(.dark) .studio-toolbar { background: rgb(10 10 10); border-color: rgb(38 38 38); }
      :host-context(.dark) .studio-tool { color: rgb(212 212 212); }
      :host-context(.dark) .studio-tool-on { color: #fff; background: rgb(23 23 23); box-shadow: inset 0 2px 0 #fff; }
      :host-context(.dark) .studio-sheet { background: rgb(18 18 18); border-color: rgb(38 38 38); }
      :host-context(.dark) .studio-sheet-head { border-color: rgb(38 38 38); }
      :host-context(.dark) .studio-sheet-done { background: #fff; color: #000; }
    }
    @keyframes studioSheetUp { from { transform: translateY(16px); opacity: 0; } to { transform: none; opacity: 1; } }
    @media (prefers-reduced-motion: reduce) { .studio-sheet { animation: none; } }

    /*
     * The app shell wraps every route in <main class="max-w-7xl">, which caps
     * this page at 1280px and leaves ~340px of dead margin either side on a
     * wide monitor. The design stage is the whole point of this page, so it
     * escapes that column with negative margins.
     *
     * max(..., -190px) bounds the escape so the section never exceeds 1600px,
     * and the +10px allows for the scrollbar - 50vw includes it, so without
     * that slack a full-bleed element overflows and adds a horizontal scrollbar.
     * Only from 1280px up; below that the shell's own width is already right.
     */
    @media (min-width: 1280px) {
      .studio-bleed {
        /* Scrollbar-aware, same maths as the global .full-bleed: 100vw includes
           the scrollbar but the usable viewport does not. Capped at -190px so
           the studio never exceeds 1600px on a very wide monitor. */
        margin-left: max(calc(50% - 50vw + (var(--sbw, 0px) / 2)), -190px);
        margin-right: max(calc(50% - 50vw + (var(--sbw, 0px) / 2)), -190px);
      }
    }
  `],
})
export class CustomDesignPage implements AfterViewInit, OnDestroy {
  protected readonly canvas = inject(CustomDesignCanvasService);
  private readonly api = inject(ApiService);
  protected readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  @ViewChild('canvasEl') private canvasRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('textInput') private textInputRef?: ElementRef<HTMLInputElement>;

  /**
   * Mockup image for a view tab's thumbnail, taken from the currently-selected
   * colourway. Null until an admin uploads that view's mockup, in which case the
   * tab falls back to a text placeholder rather than a broken image.
   */
  protected viewThumb(view: CustomDesignView): string | null {
    return this.selectedColor()?.images?.[view] ?? null;
  }

  protected readonly viewTabs: ViewTab[] = [
    { id: 'front', label: 'Front' },
    { id: 'back', label: 'Back' },
    { id: 'leftSleeve', label: 'Left sleeve' },
    { id: 'rightSleeve', label: 'Right sleeve' },
  ];

  /** Phone only: which panel is open above the bar of tools; null for none. */
  protected readonly sheet = signal<StudioSheet | null>(null);
  protected readonly phoneTools: { id: StudioSheet; label: string; icon: string }[] = [
    { id: 'design', label: 'Design', icon: '🖼️' },
    { id: 'text', label: 'Text', icon: 'T' },
    { id: 'product', label: 'Product', icon: '👕' },
    { id: 'edit', label: 'Edit', icon: '✎' },
    { id: 'more', label: 'Save', icon: '💾' },
  ];

  constructor() {
    // The Edit panel is about the selected item: with nothing selected it has
    // nothing to show, so it closes rather than sit there empty.
    effect(() => {
      if (!this.canvas.active() && this.sheet() === 'edit') this.sheet.set(null);
    });
  }

  /** The bar of tools: opens a panel, or closes it when it is the one open. */
  protected toggleSheet(id: StudioSheet): void {
    const opening = this.sheet() !== id;
    this.sheet.set(opening ? id : null);
    // Typing is the whole point of the Text panel: put the cursor in its box.
    if (opening && id === 'text') setTimeout(() => this.textInputRef?.nativeElement.focus(), 60);
  }

  protected closeSheet(): void {
    this.sheet.set(null);
  }

  /** True on a phone-width screen, where the studio is laid out as an app. */
  private isPhone(): boolean {
    return typeof window !== 'undefined' && window.matchMedia('(max-width: 639px)').matches;
  }

  protected readonly loading = signal(true);
  protected readonly error = signal('');
  protected readonly items = signal<CustomDesignItem[]>([]);
  protected readonly logos = signal<CustomDesignLogo[]>([]);
  protected readonly uploading = signal(false);

  protected readonly selectedItemName = signal('');
  protected readonly selectedColorName = signal('');
  protected selectedSize = '';
  protected selectedTechnique = '';
  protected readonly view = signal<CustomDesignView>('front');

  protected newText = '';
  protected textColor = '#111111';

  /**
   * Fonts offered in the text tool, grouped so the list stays scannable.
   *
   * The webfont families are loaded in index.html. System families need no
   * loading; both kinds are awaited through ensureFontLoaded before being
   * applied, because canvas text is rasterised once and will not repaint
   * itself when a font arrives late.
   */
  protected readonly imageFilters: { id: ImageFilterPreset; label: string }[] = [
    { id: 'none', label: 'None' },
    { id: 'grayscale', label: 'B&W' },
    { id: 'sepia', label: 'Sepia' },
    { id: 'invert', label: 'Invert' },
  ];

  protected readonly fontGroups: { label: string; fonts: string[] }[] = [
    { label: 'Sans', fonts: ['Inter', 'Montserrat', 'Poppins', 'Oswald', 'Arial', 'Helvetica', 'Trebuchet MS', 'Verdana'] },
    { label: 'Display', fonts: ['Anton', 'Bebas Neue', 'Archivo Black', 'Righteous', 'Bangers', 'Impact'] },
    { label: 'Script & handwriting', fonts: ['Pacifico', 'Lobster', 'Permanent Marker', 'Caveat', 'Brush Script MT', 'Comic Sans MS'] },
    { label: 'Serif & mono', fonts: ['Playfair Display', 'Georgia', 'Times New Roman', 'Courier New'] },
    { label: 'বাংলা / Bengali', fonts: ['Hind Siliguri', 'Noto Sans Bengali'] },
  ];

  /** Flat list, used to warm the webfont cache once the studio opens. */
  protected readonly fonts = this.fontGroups.flatMap((g) => g.fonts);
  protected readonly removingBg = signal(false);
  protected readonly removeBgError = signal('');

  protected readonly selectedItem = computed(() => this.items().find((i) => i.name === this.selectedItemName()) ?? null);
  protected readonly selectedColor = computed<CustomDesignColor | null>(
    () => this.selectedItem()?.colors.find((c) => c.name === this.selectedColorName()) ?? null,
  );

  // Drafts / share
  protected readonly draftId = signal<string | null>(null);
  protected readonly savingDraft = signal(false);
  protected readonly showDrafts = signal(false);
  protected readonly drafts = signal<CustomDesignDraft[]>([]);
  protected readonly shareUrl = signal<string | null>(null);

  // Submit modal
  protected readonly showSubmit = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal('');
  protected readonly submittedRef = signal<string | null>(null);
  protected form = { firstName: '', lastName: '', phone: '', email: '', shippingAddress: '', apartment: '', city: '', zipCode: '', country: '' };
  /** Size → quantity for the structured order grid, plus technique + free-text notes. */
  protected sizeQty: Record<string, number> = {};
  protected submitTechnique = '';
  protected notesExtra = '';

  private canvasReady = false;

  ngAfterViewInit(): void {
    // The grid is always visible now, so the canvas is laid out here → fabric can
    // measure it and will actually paint.
    // A square stage, as large as the centre column allows. Fabric maps canvas
    // pixels 1:1, so the backing store must be sized here rather than stretched
    // with CSS - scaling it in CSS would desynchronise pointer coordinates.
    const host = this.canvasRef.nativeElement.parentElement;
    // Minus the wrapper's own padding: p-4 in the studio, less on a phone.
    const style = host ? getComputedStyle(host) : null;
    const padding = style ? parseFloat(style.paddingLeft) + parseFloat(style.paddingRight) : 32;
    const available = Math.floor((host?.clientWidth ?? 0) - padding);
    // Also bound by the viewport height: the stage is square, so sizing purely
    // on width would push the garment below the fold on a short window.
    // Garment mockups are portrait, so the rendered shirt is bounded by the
    // canvas height, not its width - sizing on width alone would just add empty
    // margins either side of the shirt.
    // Leave room for the site header, the page banner and the canvas toolbar
    // that sit above the stage, so the whole garment is visible without
    // scrolling on a typical laptop.
    // On a phone the garment shares the screen with the site header,
    // the strip of actions, the row of sides, the submit button and the bar of
    // tools: about 322px in all, so the whole editor fits one screen without
    // scrolling.
    const byHeight = Math.floor(window.innerHeight - (this.isPhone() ? 322 : 185));
    // Floor low enough that a narrow phone is never forced wider than its own
    // viewport - a 360px floor overflowed at 420px once padding was counted.
    const size = Math.max(240, Math.min(1040, available || 840, byHeight));
    this.canvas.init(this.canvasRef.nativeElement, size, size);
    this.canvasReady = true;
    // Fire-and-forget: canvas text does not repaint when a webfont lands, so
    // the families are fetched up front rather than at first use.
    void Promise.all(this.fonts.map((f) => this.canvas.ensureFontLoaded(f)));
    this.loadAssets();
  }

  ngOnDestroy(): void {
    this.canvas.dispose();
  }

  private loadAssets(): void {
    this.api.getCustomDesignAssets().pipe(
      catchError((err) => {
        this.error.set(err?.error?.message ?? 'Could not load the design studio.');
        this.loading.set(false);
        return of(null);
      }),
    ).subscribe((assets) => {
      if (!assets) return;
      this.items.set(assets.items ?? []);
      this.logos.set(assets.logos ?? []);
      this.loading.set(false);
      const first = assets.items?.[0];
      if (first) {
        this.selectedItemName.set(first.name);
        this.selectedSize = first.sizes[0] ?? '';
        this.selectedTechnique = first.printTechniques[0] ?? '';
        this.selectedColorName.set(first.colors[0]?.name ?? '');
        this.applyBackgrounds();
      }
      this.maybeLoadShared();
    });
  }

  /** If the URL carries ?shared=<token>, load that public design onto the canvas. */
  private maybeLoadShared(): void {
    const token = this.route.snapshot.queryParamMap.get('shared');
    if (!token) return;
    this.api.getSharedDesign(token).pipe(catchError(() => of(null))).subscribe((draft) => {
      if (!draft) return;
      if (draft.itemName && this.items().some((i) => i.name === draft.itemName)) this.onItemChange(draft.itemName);
      if (draft.colorName) this.onColorChange(draft.colorName);
      if (draft.designJson) void this.canvas.load(draft.designJson);
    });
  }

  protected onItemChange(name: string): void {
    this.selectedItemName.set(name);
    const it = this.selectedItem();
    this.selectedColorName.set(it?.colors[0]?.name ?? '');
    this.selectedSize = it?.sizes[0] ?? '';
    this.selectedTechnique = it?.printTechniques[0] ?? '';
    this.applyBackgrounds();
  }

  protected onColorChange(name: string): void {
    this.selectedColorName.set(name);
    this.applyBackgrounds();
  }

  private applyBackgrounds(): void {
    const c = this.selectedColor();
    if (this.canvasReady && c) void this.canvas.setBackgrounds(c.images);
  }

  protected selectView(v: CustomDesignView): void {
    this.view.set(v);
    void this.canvas.setView(v);
  }

  protected focusText(): void {
    this.textInputRef?.nativeElement.focus();
  }

  protected addText(): void {
    this.canvas.addText(this.newText);
    this.newText = '';
    // On a phone, get the panel out of the way so the new text can be seen and moved.
    this.textInputRef?.nativeElement.blur();
    this.sheet.set(null);
  }

  protected applyTextColor(): void {
    this.canvas.setActiveTextColor(this.textColor);
  }

  protected addLogo(url: string): void {
    void this.canvas.addImage(url);
    this.sheet.set(null);
  }

  protected onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.uploading.set(true);
    this.api.uploadCustomArtwork(file).pipe(
      catchError(() => {
        this.uploading.set(false);
        return of(null);
      }),
    ).subscribe((res) => {
      this.uploading.set(false);
      input.value = '';
      if (res?.url) {
        void this.canvas.addImage(res.url);
        this.sheet.set(null);
      }
    });
  }

  // ── Drafts ────────────────────────────────────────────────────────────────

  protected saveDraft(): void {
    const userId = this.auth.userId();
    if (!userId) { this.showDrafts.set(true); return; }
    this.savingDraft.set(true);
    const payload = {
      name: this.selectedItem()?.name ? `${this.selectedItem()!.name} design` : 'My design',
      itemName: this.selectedItemName(),
      colorName: this.selectedColorName(),
      thumbnailUrl: this.canvas.thumbnailDataUrl() ?? undefined,
      designJson: this.canvas.serialize(),
    };
    const id = this.draftId();
    const req = id
      ? this.api.updateCustomDesignDraft(userId, id, payload)
      : this.api.saveCustomDesignDraft(userId, payload);
    req.pipe(catchError(() => { this.savingDraft.set(false); return of(null); }))
      .subscribe((draft) => {
        this.savingDraft.set(false);
        if (draft) this.draftId.set(draft.id);
      });
  }

  protected toggleDrafts(): void {
    const open = !this.showDrafts();
    this.showDrafts.set(open);
    const userId = this.auth.userId();
    if (open && userId) {
      this.api.listCustomDesignDrafts(userId).pipe(catchError(() => of([])))
        .subscribe((list) => this.drafts.set(list));
    }
  }

  protected openDraft(d: CustomDesignDraft): void {
    this.draftId.set(d.id);
    this.showDrafts.set(false);
    if (d.itemName && this.items().some((i) => i.name === d.itemName)) this.onItemChange(d.itemName);
    if (d.colorName) this.onColorChange(d.colorName);
    if (d.designJson) void this.canvas.load(d.designJson);
  }

  protected share(): void {
    const userId = this.auth.userId();
    const id = this.draftId();
    if (!userId || !id) return;
    this.api.shareCustomDesignDraft(userId, id).pipe(catchError(() => of(null)))
      .subscribe((draft) => {
        if (draft?.shareToken) {
          this.shareUrl.set(`${location.origin}/custom-design?shared=${draft.shareToken}`);
        }
      });
  }

  // ── Submit for price ──────────────────────────────────────────────────────

  protected openSubmit(): void {
    this.form.firstName ||= this.auth.firstName() ?? '';
    this.form.lastName ||= this.auth.lastName() ?? '';
    this.form.email ||= this.auth.email() ?? '';
    // Seed the size grid from the selected garment; default the chosen size to 1.
    const sizes = this.selectedItem()?.sizes ?? [];
    const grid: Record<string, number> = {};
    for (const s of sizes) grid[s] = this.sizeQty[s] ?? 0;
    if (this.selectedSize && !grid[this.selectedSize]) grid[this.selectedSize] = 1;
    this.sizeQty = grid;
    this.submitTechnique ||= this.selectedTechnique;
    this.submitError.set('');
    this.showSubmit.set(true);
  }

  protected get orderSizes(): string[] {
    return this.selectedItem()?.sizes ?? [];
  }

  protected get totalQty(): number {
    return Object.values(this.sizeQty).reduce((sum, q) => sum + (Number(q) || 0), 0);
  }

  /** Outline colour input passes the current width alongside the new colour. */
  protected onShadowColor(event: Event): void {
    const color = (event.target as HTMLInputElement).value;
    this.canvas.setShadow(true, color);
  }

  protected onStroke(event: Event, width: number): void {
    const color = (event.target as HTMLInputElement).value;
    this.canvas.setTextStroke(color, width > 0 ? width : 1);
  }

  protected removeBg(): void {
    this.removingBg.set(true);
    this.removeBgError.set('');
    this.canvas.removeBackground().then((result) => {
      this.removingBg.set(false);
      if (result === 'none') this.removeBgError.set('This picture has no plain background to remove. It works on a design with one plain colour behind it.');
      if (result === 'failed') this.removeBgError.set('Could not process this image (it may be protected).');
    });
  }

  private composeNotes(): string {
    const rows = this.orderSizes
      .filter((s) => (Number(this.sizeQty[s]) || 0) > 0)
      .map((s) => `<li>${s}: ${this.sizeQty[s]}</li>`);
    const parts: string[] = [];
    if (rows.length) parts.push(`<p><strong>Size-wise quantity</strong></p><ul>${rows.join('')}</ul>`);
    const tech = this.submitTechnique || this.selectedTechnique;
    if (tech) parts.push(`<p><strong>Print / Embroidery technique:</strong> ${this.escapeHtml(tech)}</p>`);
    if (this.notesExtra.trim()) parts.push(`<p><strong>Notes:</strong> ${this.escapeHtml(this.notesExtra.trim())}</p>`);
    return parts.join('');
  }

  private escapeHtml(s: string): string {
    return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  }

  protected closeSubmit(): void {
    this.showSubmit.set(false);
    this.submittedRef.set(null);
  }

  protected submit(): void {
    const f = this.form;
    if (!f.firstName.trim() || !f.lastName.trim() || !f.phone.trim() || !f.email.trim()
      || !f.shippingAddress.trim() || !f.city.trim() || !f.country.trim()) {
      this.submitError.set('Please fill in all required (*) fields.');
      return;
    }
    if (this.totalQty <= 0) {
      this.submitError.set('Enter a quantity for at least one size.');
      return;
    }
    const anyContent = this.viewTabs.some((t) => !this.canvas.isViewEmpty(t.id));
    if (!anyContent) {
      this.submitError.set('Add your design to the garment first.');
      return;
    }

    this.submitting.set(true);
    this.submitError.set('');
    this.canvas.exportPreviews().then((previews) => {
      if (!previews.length) {
        this.submitting.set(false);
        this.submitError.set('Could not render your design preview. If this persists, the mockup images may need CORS enabled.');
        return;
      }
      const payload: CustomOrderRequest = {
        firstName: f.firstName.trim(),
        lastName: f.lastName.trim(),
        phone: f.phone.trim(),
        email: f.email.trim(),
        shippingAddress: f.shippingAddress.trim(),
        apartment: f.apartment.trim() || undefined,
        city: f.city.trim(),
        zipCode: f.zipCode.trim() || undefined,
        country: f.country.trim(),
        notes: this.composeNotes(),
        itemName: this.selectedItemName() || undefined,
        colorName: this.selectedColorName() || undefined,
        size: this.selectedSize || undefined,
        printTechnique: this.submitTechnique || this.selectedTechnique || undefined,
        designJson: this.canvas.serialize(),
      };
      this.api.submitCustomDesignRequest(payload, previews).pipe(
        catchError((err) => {
          this.submitting.set(false);
          this.submitError.set(err?.error?.message ?? 'Could not submit your request. Please try again.');
          return of(null);
        }),
      ).subscribe((res) => {
        this.submitting.set(false);
        if (res) this.submittedRef.set(res.reference);
      });
    });
  }
}
