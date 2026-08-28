import { Injectable, signal } from '@angular/core';
import { Canvas, FabricImage, FabricObject, IText, Rect, Shadow, filters } from 'fabric';
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
  underline: boolean;
  /** Fabric measures letter spacing in 1/1000 em, so 100 = 0.1em. */
  charSpacing: number;
  lineHeight: number;
  shadow: boolean;
  shadowColor: string;
  /** Rotation in degrees (any object). */
  angle: number;
  /** Image-only adjustments, read back off the fabric filter stack. */
  filterPreset: ImageFilterPreset;
  brightness: number;
  contrast: number;
  saturation: number;
}

export type ImageFilterPreset = 'none' | 'grayscale' | 'sepia' | 'invert';

/** The adjustable image settings the UI drives; rebuilt into fabric filters. */
export interface ImageAdjustments {
  preset: ImageFilterPreset;
  brightness: number;
  contrast: number;
  saturation: number;
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
  private width = 840;
  private height = 840;

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

  init(el: HTMLCanvasElement, width = 840, height = 840): void {
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
    // The printable-area guide is only meaningful once there is artwork to
    // place, so its visibility follows the object count.
    this.canvas.on('object:added', () => this.refreshGuideVisibility());
    this.canvas.on('object:removed', () => this.refreshGuideVisibility());

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
    // Must run after the mockup is placed - the area is derived from its bounds.
    this.computePrintAreaFromMockup();
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
    // Fallback used before a mockup has loaded: a centred rectangle on the
    // bare canvas.
    this.printArea = {
      left: this.width * 0.22,
      top: this.height * 0.20,
      width: this.width * 0.56,
      height: this.height * 0.52,
    };
  }

  /**
   * Places the printable area on the GARMENT rather than on the canvas.
   *
   * The mockup is letterboxed into the canvas ("contain"), so a portrait photo
   * leaves wide empty margins. Deriving the area from the canvas would float it
   * off the shirt; deriving it from the mockup's rendered bounds keeps it on the
   * chest whatever the photo's aspect ratio.
   */
  private computePrintAreaFromMockup(): void {
    if (!this.mockup) {
      this.computePrintArea();
      return;
    }
    const b = this.mockup.getBoundingRect();
    const sleeve = this.currentView === 'leftSleeve' || this.currentView === 'rightSleeve';
    // Sleeves take a small centred badge; body views take a chest panel that
    // starts below the collar.
    const fx = sleeve ? 0.30 : 0.26;
    const fy = sleeve ? 0.34 : 0.24;
    const fw = sleeve ? 0.40 : 0.48;
    const fh = sleeve ? 0.28 : 0.42;
    this.printArea = {
      left: b.left + b.width * fx,
      top: b.top + b.height * fy,
      width: b.width * fw,
      height: b.height * fh,
    };
  }

  private ensurePrintArea(): void {
    if (!this.canvas) return;
    const a = this.printArea;
    // The area moves with the garment, so an existing guide is repositioned
    // rather than left where the previous mockup put it.
    if (this.printGuide && this.canvas.getObjects().includes(this.printGuide)) {
      this.printGuide.set({ left: a.left, top: a.top, width: a.width, height: a.height });
      this.printGuide.setCoords();
      this.refreshGuideVisibility();
      return;
    }
    this.printGuide = new Rect({
      left: a.left, top: a.top, width: a.width, height: a.height,
      fill: 'transparent', stroke: '#3b82f6', strokeWidth: 1, strokeDashArray: [6, 6],
      selectable: false, evented: false, hoverCursor: 'default',
      excludeFromExport: true, objectCaching: false,
      visible: false,
    });
    this.canvas.add(this.printGuide);
    this.refreshGuideVisibility();
  }

  /** Any object that is not one of our locked helpers. */
  private hasUserObjects(): boolean {
    if (!this.canvas) return false;
    return this.canvas.getObjects().some((o) => o !== this.mockup && o !== this.printGuide);
  }

  /**
   * Shows the dashed guide only once there is artwork on the view. On an empty
   * garment it is noise - it reads as a broken box floating on the shirt - so
   * it stays hidden until it has something to constrain.
   */
  private refreshGuideVisibility(): void {
    if (!this.printGuide) return;
    const visible = this.showGuide() && this.hasUserObjects();
    if (this.printGuide.visible !== visible) {
      this.printGuide.visible = visible;
      this.canvas?.requestRenderAll();
    }
  }

  /** Push the mockup to the very back and the guide just above it. */
  private restackBackground(): void {
    if (!this.canvas) return;
    if (this.printGuide) this.canvas.sendObjectToBack(this.printGuide);
    if (this.mockup) this.canvas.sendObjectToBack(this.mockup);
  }

  toggleGuide(): void {
    this.showGuide.update((v) => !v);
    this.refreshGuideVisibility();
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
      underline: t['underline'] === true,
      charSpacing: (t['charSpacing'] as number) ?? 0,
      lineHeight: (t['lineHeight'] as number) ?? 1.16,
      shadow: !!o.shadow,
      shadowColor: (o.shadow as Shadow | null)?.color ?? '#000000',
      angle: Math.round(o.angle ?? 0),
      ...this.readAdjustments(o),
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

  /**
   * Applies a font, waiting for the webfont to be downloaded first.
   *
   * Canvas draws with whatever the font engine has ready at that instant - it
   * does not re-paint when a webfont finishes loading the way DOM text does.
   * Setting the family before the file arrives silently renders the fallback
   * and leaves it there, so the load has to be awaited and the canvas
   * re-rendered afterwards.
   */
  async setFontFamily(family: string): Promise<void> {
    await this.ensureFontLoaded(family);
    this.mutateActive((o) => this.isText(o), (o) => o.set('fontFamily', family));
    this.canvas?.requestRenderAll();
  }

  /** Resolves once the family is usable, or immediately if it cannot be loaded. */
  async ensureFontLoaded(family: string): Promise<void> {
    const fonts = (document as Document & { fonts?: FontFaceSet }).fonts;
    if (!fonts?.load) return;
    try {
      // Both weights: toggling bold on an unloaded weight has the same problem.
      await Promise.all([
        fonts.load(`400 32px "${family}"`),
        fonts.load(`700 32px "${family}"`),
      ]);
    } catch {
      // Offline or a blocked CDN - fabric falls back to a system font.
    }
  }

  toggleUnderline(): void {
    this.mutateActive((o) => this.isText(o), (o) => o.set('underline', !(o as unknown as Record<string, unknown>)['underline']));
  }

  /** Letter spacing in 1/1000 em (fabric's unit), clamped to a sane range. */
  setCharSpacing(value: number): void {
    const v = Math.max(-200, Math.min(1000, Math.round(value) || 0));
    this.mutateActive((o) => this.isText(o), (o) => o.set('charSpacing', v));
  }

  setLineHeight(value: number): void {
    const v = Math.max(0.5, Math.min(3, Number(value) || 1.16));
    this.mutateActive((o) => this.isText(o), (o) => o.set('lineHeight', v));
  }

  /**
   * Drop shadow. Offsets scale with the font size so a shadow stays
   * proportional instead of vanishing on large display type.
   */
  setShadow(enabled: boolean, color = '#000000'): void {
    this.mutateActive((o) => this.isText(o), (o) => {
      if (!enabled) { o.set('shadow', null); return; }
      const size = ((o as unknown as Record<string, unknown>)['fontSize'] as number) ?? 40;
      o.set('shadow', new Shadow({
        color,
        blur: Math.max(2, size * 0.12),
        offsetX: Math.max(1, size * 0.05),
        offsetY: Math.max(1, size * 0.05),
      }));
    });
  }

  /** Rewrites the selected text's casing in place. */
  transformCase(mode: 'upper' | 'lower' | 'title'): void {
    this.mutateActive((o) => this.isText(o), (o) => {
      const t = o as unknown as { text?: string; set: (k: string, v: unknown) => void };
      const current = t.text ?? '';
      if (!current) return;
      const next =
        mode === 'upper' ? current.toUpperCase()
        : mode === 'lower' ? current.toLowerCase()
        : current.replace(/\w\S*/g, (w) => w[0].toUpperCase() + w.slice(1).toLowerCase());
      t.set('text', next);
    });
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

  // ── Rotation ────────────────────────────────────────────────────────────────

  /** Absolute angle in degrees. Objects use a centre origin, so this spins in place. */
  setAngle(deg: number): void {
    const a = ((Math.round(deg) % 360) + 360) % 360;
    this.mutateActive(() => true, (o) => { o.set('angle', a); o.setCoords(); });
  }

  /** Quarter turns, for the common "my photo is sideways" case. */
  rotateBy(delta: number): void {
    const o = this.canvas?.getActiveObject();
    if (!o) return;
    this.setAngle((o.angle ?? 0) + delta);
  }

  // ── Image adjustments ───────────────────────────────────────────────────────

  /**
   * Reconstructs the UI's slider state from the object's actual filter stack.
   *
   * Deliberately derived rather than cached on the side: fabric serialises
   * `filters` into a saved draft but would not serialise a private settings
   * object, so reading the stack back is what keeps the panel in step with a
   * reopened design.
   */
  private readAdjustments(o: FabricObject): ImageAdjustments & { filterPreset: ImageFilterPreset } {
    const empty = { preset: 'none' as ImageFilterPreset, brightness: 0, contrast: 0, saturation: 0 };
    const stack = (o as unknown as { filters?: unknown[] }).filters;
    if (o.type !== 'image' || !Array.isArray(stack)) {
      return { ...empty, filterPreset: 'none' };
    }
    const out: ImageAdjustments = { ...empty };
    for (const f of stack) {
      const rec = f as Record<string, unknown>;
      const type = String(rec['type'] ?? '').toLowerCase();
      if (type === 'grayscale') out.preset = 'grayscale';
      else if (type === 'sepia') out.preset = 'sepia';
      else if (type === 'invert') out.preset = 'invert';
      else if (type === 'brightness') out.brightness = (rec['brightness'] as number) ?? 0;
      else if (type === 'contrast') out.contrast = (rec['contrast'] as number) ?? 0;
      else if (type === 'saturation') out.saturation = (rec['saturation'] as number) ?? 0;
    }
    return { ...out, filterPreset: out.preset };
  }

  /** Current adjustments for the selected image, defaults when nothing applies. */
  private currentAdjustments(): ImageAdjustments {
    const o = this.canvas?.getActiveObject();
    if (!o) return { preset: 'none', brightness: 0, contrast: 0, saturation: 0 };
    const a = this.readAdjustments(o);
    return { preset: a.preset, brightness: a.brightness, contrast: a.contrast, saturation: a.saturation };
  }

  /**
   * Rebuilds the whole filter stack from a settings object.
   *
   * Fabric applies filters in array order and has no notion of "replace the
   * grayscale one", so every change re-creates the list. Zero-valued
   * adjustments are omitted entirely - an identity filter still costs a full
   * pixel pass on every render.
   */
  private applyAdjustments(next: ImageAdjustments): void {
    const o = this.canvas?.getActiveObject();
    if (!o || o.type !== 'image') return;
    const img = o as FabricImage;
    const stack: unknown[] = [];
    if (next.preset === 'grayscale') stack.push(new filters.Grayscale());
    else if (next.preset === 'sepia') stack.push(new filters.Sepia());
    else if (next.preset === 'invert') stack.push(new filters.Invert());
    if (next.brightness) stack.push(new filters.Brightness({ brightness: next.brightness }));
    if (next.contrast) stack.push(new filters.Contrast({ contrast: next.contrast }));
    if (next.saturation) stack.push(new filters.Saturation({ saturation: next.saturation }));

    (img as unknown as { filters: unknown[] }).filters = stack;
    try {
      img.applyFilters();
    } catch {
      // applyFilters rasterises through a 2D context, which throws if the
      // source image tainted the canvas (a non-CORS load). Leave the image
      // unfiltered rather than breaking the studio.
      (img as unknown as { filters: unknown[] }).filters = [];
    }
    this.canvas?.requestRenderAll();
    this.pushHistory();
    this.syncActive();
  }

  setImageFilter(preset: ImageFilterPreset): void {
    this.applyAdjustments({ ...this.currentAdjustments(), preset });
  }

  setBrightness(v: number): void {
    this.applyAdjustments({ ...this.currentAdjustments(), brightness: this.clampAdjust(v) });
  }

  setContrast(v: number): void {
    this.applyAdjustments({ ...this.currentAdjustments(), contrast: this.clampAdjust(v) });
  }

  setSaturation(v: number): void {
    this.applyAdjustments({ ...this.currentAdjustments(), saturation: this.clampAdjust(v) });
  }

  resetAdjustments(): void {
    this.applyAdjustments({ preset: 'none', brightness: 0, contrast: 0, saturation: 0 });
  }

  private clampAdjust(v: number): number {
    return Math.max(-1, Math.min(1, Number(v) || 0));
  }

  /**
   * Scales the selection to sit inside the printable area and centres it there.
   * Uses "contain" so artwork is never cropped or distorted.
   */
  fitToPrintArea(): void {
    const o = this.canvas?.getActiveObject();
    if (!o || !this.canvas) return;
    const a = this.printArea;
    if (!a.width || !a.height) return;
    // Measure unscaled, so repeated calls converge instead of shrinking.
    const w = (o.width ?? 1) || 1;
    const h = (o.height ?? 1) || 1;
    const scale = Math.min(a.width / w, a.height / h);
    o.set({
      scaleX: scale, scaleY: scale,
      angle: 0,
      originX: 'center', originY: 'center',
      left: a.left + a.width / 2,
      top: a.top + a.height / 2,
    });
    o.setCoords();
    this.canvas.requestRenderAll();
    this.pushHistory();
    this.syncActive();
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
      // Re-assert the helpers at the bottom of the stack. The mockup and the
      // print guide live in the same object list as the user's artwork, so an
      // unguarded reorder can drop a design *behind the garment* and make it
      // vanish - it is still selected, just painted over.
      this.restackBackground();
      this.canvas.requestRenderAll();
      this.pushHistory();
    }
  }

  sendBackward(): void {
    const obj = this.canvas?.getActiveObject();
    if (obj && this.canvas) {
      this.canvas.sendObjectBackwards(obj);
      // Same guard as bringForward: never let artwork sink below the mockup.
      this.restackBackground();
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
