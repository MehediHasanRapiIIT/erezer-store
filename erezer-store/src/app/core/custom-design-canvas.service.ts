import { Injectable, signal } from '@angular/core';
import { Canvas, FabricImage, FabricObject, IText, Rect } from 'fabric';
import type { CustomDesignImages, CustomDesignView } from './api.models';

const VIEWS: CustomDesignView[] = ['front', 'back', 'leftSleeve', 'rightSleeve'];

/** Serialized whole-studio state: the fabric objects for each view. */
type StudioState = Partial<Record<CustomDesignView, unknown>>;

/** Editable properties of the currently-selected object, surfaced to the UI. */
export interface ActiveProps {
  kind: 'text' | 'image' | 'other';
  fontFamily: string;
  fontSize: number;
  bold: boolean;
  italic: boolean;
  textAlign: string;
  fill: string;
  strokeColor: string;
  strokeWidth: number;
  opacity: number;
}

/**
 * Wraps a fabric.js canvas for the custom-design studio. Keeps one object layer
 * per garment view (front/back/sleeves), swaps the mockup background per colour,
 * and can flatten each non-empty view to a PNG for the quote request.
 *
 * Provided at the component level (not root) so each studio instance is isolated
 * and torn down with the page.
 */
@Injectable()
export class CustomDesignCanvasService {
  private canvas: Canvas | null = null;
  private width = 500;
  private height = 600;

  private currentView: CustomDesignView = 'front';
  private readonly objectsByView = new Map<CustomDesignView, unknown>();
  private backgrounds: CustomDesignImages = { front: null, back: null, leftSleeve: null, rightSleeve: null };

  // Per-view undo/redo history of object snapshots.
  private history: string[] = [];
  private historyIndex = -1;
  private suppressHistory = false;

  /**
   * The garment mockup, rendered as a locked object at the back of the stack
   * (fabric v6 backgroundImage did not paint reliably here). Tagged
   * excludeFromExport so it never enters the saved design JSON, but it still
   * shows in the flattened preview (toDataURL renders the live canvas).
   */
  private mockup: FabricObject | null = null;

  /** Dashed "safe zone" guide showing the printable area (visual + drag constraint). */
  private printGuide: FabricObject | null = null;
  private printArea = { left: 0, top: 0, width: 0, height: 0 };

  /** Reactive flags the toolbar binds to. */
  readonly hasSelection = signal(false);
  readonly canUndo = signal(false);
  readonly canRedo = signal(false);
  readonly zoom = signal(1);
  readonly showGuide = signal(true);

  /** Properties of the currently-selected object, for the contextual tool panel. */
  readonly active = signal<ActiveProps | null>(null);

  // ── Lifecycle ──────────────────────────────────────────────────────────────

  init(el: HTMLCanvasElement, width = 500, height = 600): void {
    this.width = width;
    this.height = height;
    this.computePrintArea();
    this.canvas = new Canvas(el, {
      width,
      height,
      // Neutral light-grey drawn by fabric itself, so every garment colour —
      // including white shirts — stays visible.
      backgroundColor: '#e5e7eb',
      preserveObjectStacking: true,
      selection: true,
      // Map canvas pixels 1:1 so DevTools device emulation (fake devicePixelRatio)
      // can't distort the backing store.
      enableRetinaScaling: false,
    });
    const sync = () => {
      this.hasSelection.set(!!this.canvas?.getActiveObject());
      this.syncActive();
    };
    this.canvas.on('selection:created', sync);
    this.canvas.on('selection:updated', sync);
    this.canvas.on('selection:cleared', sync);
    // Transform (move/scale/rotate) is a discrete undoable step.
    this.canvas.on('object:modified', () => { this.pushHistory(); this.syncActive(); });
    // Keep designs within the printable area.
    this.canvas.on('object:moving', (e) => this.clampToArea(e.target));
    this.canvas.on('object:scaling', (e) => this.clampToArea(e.target));

    this.ensurePrintArea();
    this.canvas.renderAll();
  }

  dispose(): void {
    this.canvas?.dispose();
    this.canvas = null;
    this.objectsByView.clear();
  }

  /**
   * Re-assert the canvas dimensions and repaint. Fabric computes its retina
   * (devicePixelRatio) transform at init time; if that happened while the canvas
   * was display:none, the background/objects render through a wrong transform and
   * appear blank. Call this once the studio becomes visible to fix it.
   */
  refreshSize(): void {
    if (!this.canvas) return;
    this.canvas.setDimensions({ width: this.width, height: this.height });
    this.canvas.renderAll();
  }

  // ── Backgrounds (garment colour) ─────────────────────────────────────────────

  /** Point every view at a colourway's mockups and refresh the current view. */
  async setBackgrounds(images: CustomDesignImages): Promise<void> {
    this.backgrounds = images;
    await this.applyBackground(this.currentView);
  }

  private async applyBackground(view: CustomDesignView): Promise<void> {
    if (!this.canvas) return;
    this.removeMockup();
    const url = this.backgrounds[view];
    if (url) {
      try {
        const img = await this.loadImage(url);
        const scale = Math.min(this.width / (img.width || 1), this.height / (img.height || 1));
        img.set({
          originX: 'center', originY: 'center',
          left: this.width / 2, top: this.height / 2,
          scaleX: scale, scaleY: scale,
          selectable: false, evented: false, hoverCursor: 'default',
          excludeFromExport: true,
        });
        this.canvas.add(img);
        this.mockup = img;
      } catch (e) {
        console.warn('[custom-design] mockup failed to load:', url, e);
      }
    }
    this.ensurePrintArea();
    this.restackBackground();
    this.canvas.renderAll();
  }

  private removeMockup(): void {
    if (this.mockup && this.canvas) {
      this.canvas.remove(this.mockup);
    }
    this.mockup = null;
  }

  // ── Printable area guide ──────────────────────────────────────────────────────

  private computePrintArea(): void {
    // A centred chest-area rectangle. Tunable per garment later.
    this.printArea = {
      left: this.width * 0.22,
      top: this.height * 0.20,
      width: this.width * 0.56,
      height: this.height * 0.52,
    };
  }

  private ensurePrintArea(): void {
    if (!this.canvas) return;
    if (this.printGuide && this.canvas.getObjects().includes(this.printGuide)) return;
    const a = this.printArea;
    this.printGuide = new Rect({
      left: a.left, top: a.top, width: a.width, height: a.height,
      fill: 'transparent', stroke: '#3b82f6', strokeWidth: 1, strokeDashArray: [6, 6],
      selectable: false, evented: false, hoverCursor: 'default',
      excludeFromExport: true, objectCaching: false,
      visible: this.showGuide(),
    });
    this.canvas.add(this.printGuide);
  }

  /** Push the mockup to the very back and the guide just above it. */
  private restackBackground(): void {
    if (!this.canvas) return;
    if (this.printGuide) this.canvas.sendObjectToBack(this.printGuide);
    if (this.mockup) this.canvas.sendObjectToBack(this.mockup);
  }

  toggleGuide(): void {
    this.showGuide.update((v) => !v);
    if (this.printGuide) {
      this.printGuide.visible = this.showGuide();
      this.canvas?.requestRenderAll();
    }
  }

  /** Nudge an object back inside the printable area (called live during drag/scale). */
  private clampToArea(obj?: FabricObject | null): void {
    if (!obj || obj === this.mockup || obj === this.printGuide || !this.showGuide()) return;
    const a = this.printArea;
    const b = obj.getBoundingRect();
    let dx = 0, dy = 0;
    if (b.left < a.left) dx = a.left - b.left;
    else if (b.left + b.width > a.left + a.width) dx = a.left + a.width - (b.left + b.width);
    if (b.top < a.top) dy = a.top - b.top;
    else if (b.top + b.height > a.top + a.height) dy = a.top + a.height - (b.top + b.height);
    if (dx || dy) {
      obj.set({ left: (obj.left ?? 0) + dx, top: (obj.top ?? 0) + dy });
      obj.setCoords();
    }
  }

  /**
   * Loads an image, preferring a CORS-clean load (needed so the canvas can be
   * exported to PNG on submit). If that is blocked — e.g. the same URL was cached
   * earlier by a plain <img> without CORS headers — fall back to a normal load so
   * the mockup at least displays. In that case export may taint; exportPreviews
   * handles that gracefully.
   */
  private async loadImage(url: string): Promise<FabricImage> {
    try {
      return await FabricImage.fromURL(url, { crossOrigin: 'anonymous' });
    } catch (e) {
      console.warn('[custom-design] CORS image load failed, retrying without crossOrigin:', url);
      return await FabricImage.fromURL(url);
    }
  }

  // ── View switching ───────────────────────────────────────────────────────────

  get view(): CustomDesignView {
    return this.currentView;
  }

  async setView(view: CustomDesignView): Promise<void> {
    if (!this.canvas || view === this.currentView) return;
    this.stashCurrentObjects();
    this.currentView = view;
    await this.loadObjects(this.objectsByView.get(view));
    await this.applyBackground(view);
    this.resetHistory();
  }

  /** True when a view has no user-added objects (so we skip it on submit). */
  isViewEmpty(view: CustomDesignView): boolean {
    if (view === this.currentView && this.canvas) {
      // The mockup and print guide are locked helpers; ignore them when deciding "empty".
      return this.canvas.getObjects().filter((o) => o !== this.mockup && o !== this.printGuide).length === 0;
    }
    const state = this.objectsByView.get(view) as { objects?: unknown[] } | undefined;
    return !state?.objects || state.objects.length === 0;
  }

  private stashCurrentObjects(): void {
    if (!this.canvas) return;
    this.objectsByView.set(this.currentView, { objects: this.canvas.toObject().objects });
  }

  private async loadObjects(state: unknown): Promise<void> {
    if (!this.canvas) return;
    this.suppressHistory = true;
    const objects = (state as { objects?: unknown[] })?.objects ?? [];
    await this.canvas.loadFromJSON({ objects, version: '6' });
    this.canvas.requestRenderAll();
    this.suppressHistory = false;
  }

  // ── Add / edit objects ───────────────────────────────────────────────────────

  addText(value: string): void {
    if (!this.canvas || !value.trim()) return;
    const text = new IText(value.trim(), {
      left: this.width / 2,
      top: this.height / 2,
      originX: 'center',
      originY: 'center',
      fontFamily: 'Inter, system-ui, sans-serif',
      fontSize: 40,
      fill: '#111111',
    });
    this.canvas.add(text);
    this.canvas.setActiveObject(text);
    this.canvas.requestRenderAll();
    this.hasSelection.set(true);
    this.syncActive();
    this.pushHistory();
  }

  async addImage(url: string): Promise<void> {
    if (!this.canvas) return;
    try {
      const img = await this.loadImage(url);
      const maxDim = Math.min(this.width, this.height) * 0.5;
      const scale = Math.min(maxDim / (img.width || 1), maxDim / (img.height || 1), 1);
      img.set({ left: this.width / 2, top: this.height / 2, originX: 'center', originY: 'center', scaleX: scale, scaleY: scale });
      this.canvas.add(img);
      this.canvas.setActiveObject(img);
      this.canvas.requestRenderAll();
      this.hasSelection.set(true);
      this.syncActive();
      this.pushHistory();
    } catch {
      // ignore load failures; caller surfaces upload errors separately
    }
  }

  // ── Selection introspection + rich text / image editing ──────────────────────

  private isText(o: FabricObject | undefined | null): boolean {
    return !!o && (o.type === 'i-text' || o.type === 'text' || o.type === 'textbox');
  }

  private syncActive(): void {
    const o = this.canvas?.getActiveObject();
    if (!o) { this.active.set(null); return; }
    const t = o as unknown as Record<string, unknown>;
    this.active.set({
      kind: this.isText(o) ? 'text' : (o.type === 'image' ? 'image' : 'other'),
      fontFamily: (t['fontFamily'] as string) ?? 'Inter',
      fontSize: (t['fontSize'] as number) ?? 40,
      bold: t['fontWeight'] === 'bold' || t['fontWeight'] === 700,
      italic: t['fontStyle'] === 'italic',
      textAlign: (t['textAlign'] as string) ?? 'center',
      fill: typeof t['fill'] === 'string' ? (t['fill'] as string) : '#111111',
      strokeColor: (t['stroke'] as string) ?? '#000000',
      strokeWidth: (t['strokeWidth'] as number) ?? 0,
      opacity: (o.opacity ?? 1),
    });
  }

  private mutateActive(predicate: (o: FabricObject) => boolean, fn: (o: FabricObject) => void): void {
    const o = this.canvas?.getActiveObject();
    if (!o || !predicate(o)) return;
    fn(o);
    this.canvas?.requestRenderAll();
    this.pushHistory();
    this.syncActive();
  }

  setActiveTextColor(color: string): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set('fill', color));
  }

  setFontFamily(family: string): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set('fontFamily', family));
  }

  setFontSize(size: number): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set('fontSize', Math.max(6, Math.round(size) || 6)));
  }

  toggleBold(): void {
    this.mutateActive((o) => this.isText(o), (o) => {
      const cur = (o as unknown as Record<string, unknown>)['fontWeight'];
      o.set('fontWeight', cur === 'bold' ? 'normal' : 'bold');
    });
  }

  toggleItalic(): void {
    this.mutateActive((o) => this.isText(o), (o) => {
      const cur = (o as unknown as Record<string, unknown>)['fontStyle'];
      o.set('fontStyle', cur === 'italic' ? 'normal' : 'italic');
    });
  }

  setTextAlign(align: 'left' | 'center' | 'right'): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set('textAlign', align));
  }

  setTextStroke(color: string, width: number): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set({ stroke: width > 0 ? color : null, strokeWidth: Math.max(0, width) }));
  }

  setOpacity(opacity: number): void {
    this.mutateActive(() => true, (o) => o.set('opacity', Math.max(0.1, Math.min(1, opacity))));
  }

  flipHorizontal(): void {
    this.mutateActive((o) => o.type === 'image', (o) => o.set('flipX', !(o as unknown as Record<string, unknown>)['flipX']));
  }

  flipVertical(): void {
    this.mutateActive((o) => o.type === 'image', (o) => o.set('flipY', !(o as unknown as Record<string, unknown>)['flipY']));
  }

  /**
   * Makes near-white pixels of the selected image transparent — a simple
   * background remover for artwork uploaded on a white/solid-light background.
   * Replaces the image source with the processed (same-origin) PNG.
   */
  async removeBackground(threshold = 238): Promise<boolean> {
    const obj = this.canvas?.getActiveObject();
    if (!obj || obj.type !== 'image') return false;
    const img = obj as FabricImage;
    const el = img.getElement() as CanvasImageSource & { naturalWidth?: number; naturalHeight?: number; width: number; height: number };
    const w = el.naturalWidth || el.width;
    const h = el.naturalHeight || el.height;
    const off = document.createElement('canvas');
    off.width = w; off.height = h;
    const ctx = off.getContext('2d');
    if (!ctx) return false;
    ctx.drawImage(el, 0, 0, w, h);
    let data: ImageData;
    try {
      data = ctx.getImageData(0, 0, w, h);
    } catch {
      // Tainted (non-CORS) image — can't read pixels.
      return false;
    }
    const d = data.data;
    for (let i = 0; i < d.length; i += 4) {
      if (d[i] > threshold && d[i + 1] > threshold && d[i + 2] > threshold) {
        d[i + 3] = 0;
      }
    }
    ctx.putImageData(data, 0, 0);
    await img.setSrc(off.toDataURL('image/png'));
    this.canvas?.requestRenderAll();
    this.pushHistory();
    this.syncActive();
    return true;
  }

  deleteActive(): void {
    if (!this.canvas) return;
    const active = this.canvas.getActiveObjects();
    if (!active.length) return;
    active.forEach((o: FabricObject) => this.canvas!.remove(o));
    this.canvas.discardActiveObject();
    this.canvas.requestRenderAll();
    this.hasSelection.set(false);
    this.active.set(null);
    this.pushHistory();
  }

  async duplicateActive(): Promise<void> {
    const obj = this.canvas?.getActiveObject();
    if (!obj || !this.canvas) return;
    const clone = await obj.clone();
    clone.set({ left: (obj.left ?? 0) + 20, top: (obj.top ?? 0) + 20 });
    this.canvas.add(clone);
    this.canvas.setActiveObject(clone);
    this.canvas.requestRenderAll();
    this.pushHistory();
  }

  bringForward(): void {
    const obj = this.canvas?.getActiveObject();
    if (obj && this.canvas) {
      this.canvas.bringObjectForward(obj);
      this.canvas.requestRenderAll();
      this.pushHistory();
    }
  }

  sendBackward(): void {
    const obj = this.canvas?.getActiveObject();
    if (obj && this.canvas) {
      this.canvas.sendObjectBackwards(obj);
      this.canvas.requestRenderAll();
      this.pushHistory();
    }
  }

  setZoom(next: number): void {
    if (!this.canvas) return;
    const clamped = Math.max(0.5, Math.min(2, next));
    this.canvas.setZoom(clamped);
    this.zoom.set(clamped);
    this.canvas.requestRenderAll();
  }

  // ── Undo / redo (per view) ────────────────────────────────────────────────────

  private resetHistory(): void {
    this.history = [this.snapshot()];
    this.historyIndex = 0;
    this.updateHistoryFlags();
  }

  private pushHistory(): void {
    if (this.suppressHistory) return;
    // Drop any redo branch, then append the new snapshot.
    this.history = this.history.slice(0, this.historyIndex + 1);
    this.history.push(this.snapshot());
    this.historyIndex = this.history.length - 1;
    this.updateHistoryFlags();
  }

  private snapshot(): string {
    return JSON.stringify(this.canvas?.toObject().objects ?? []);
  }

  private updateHistoryFlags(): void {
    this.canUndo.set(this.historyIndex > 0);
    this.canRedo.set(this.historyIndex < this.history.length - 1);
  }

  async undo(): Promise<void> {
    if (this.historyIndex <= 0) return;
    this.historyIndex--;
    await this.restore(this.history[this.historyIndex]);
  }

  async redo(): Promise<void> {
    if (this.historyIndex >= this.history.length - 1) return;
    this.historyIndex++;
    await this.restore(this.history[this.historyIndex]);
  }

  private async restore(objectsJson: string): Promise<void> {
    if (!this.canvas) return;
    this.suppressHistory = true;
    await this.canvas.loadFromJSON({ objects: JSON.parse(objectsJson), version: '6' });
    // loadFromJSON clears the canvas — re-attach the mockup + guide helpers.
    if (this.mockup) this.canvas.add(this.mockup);
    this.ensurePrintArea();
    this.restackBackground();
    this.canvas.requestRenderAll();
    this.suppressHistory = false;
    this.hasSelection.set(false);
    this.active.set(null);
    this.updateHistoryFlags();
  }

  // ── Persistence + export ───────────────────────────────────────────────────────

  /** Serializes every view's objects (not backgrounds) for drafts/resume. */
  serialize(): string {
    this.stashCurrentObjects();
    const state: StudioState = {};
    for (const v of VIEWS) {
      const objs = this.objectsByView.get(v);
      if (objs) state[v] = objs;
    }
    return JSON.stringify(state);
  }

  /** Restores a previously serialized studio state onto the current backgrounds. */
  async load(serialized: string): Promise<void> {
    let state: StudioState;
    try {
      state = JSON.parse(serialized) as StudioState;
    } catch {
      return;
    }
    this.objectsByView.clear();
    for (const v of VIEWS) {
      if (state[v]) this.objectsByView.set(v, state[v]);
    }
    await this.loadObjects(this.objectsByView.get(this.currentView));
    await this.applyBackground(this.currentView);
    this.resetHistory();
  }

  /** Flattens each non-empty view to a PNG blob (with its mockup background). */
  async exportPreviews(): Promise<{ view: CustomDesignView; blob: Blob }[]> {
    if (!this.canvas) return [];
    this.stashCurrentObjects();
    const original = this.currentView;
    const out: { view: CustomDesignView; blob: Blob }[] = [];

    for (const v of VIEWS) {
      if (this.isViewEmpty(v)) continue;
      await this.loadObjects(this.objectsByView.get(v));
      await this.applyBackground(v);
      this.canvas.discardActiveObject();
      this.canvas.requestRenderAll();
      const blob = await this.currentCanvasBlob();
      if (blob) out.push({ view: v, blob });
    }

    // Restore what the user was looking at.
    this.currentView = original;
    await this.loadObjects(this.objectsByView.get(original));
    await this.applyBackground(original);
    return out;
  }

  /** A single flattened PNG data URL of the current view (used for the draft thumbnail). */
  thumbnailDataUrl(): string | null {
    if (!this.canvas) return null;
    const restore = this.hideGuideForCapture();
    try {
      return this.canvas.toDataURL({ format: 'png', multiplier: 0.5 });
    } catch {
      return null; // tainted canvas (see CORS note)
    } finally {
      restore();
    }
  }

  private async currentCanvasBlob(): Promise<Blob | null> {
    if (!this.canvas) return null;
    const restore = this.hideGuideForCapture();
    try {
      const dataUrl = this.canvas.toDataURL({ format: 'png', multiplier: 2 });
      const res = await fetch(dataUrl);
      return await res.blob();
    } catch {
      // Tainted canvas: mockup/artwork served without CORS headers. Surfaced upstream.
      return null;
    } finally {
      restore();
    }
  }

  /** Temporarily hides the print guide so it never lands in a flattened preview. */
  private hideGuideForCapture(): () => void {
    const guide = this.printGuide;
    if (!guide || !guide.visible) return () => {};
    guide.visible = false;
    return () => { guide.visible = true; };
  }
}
