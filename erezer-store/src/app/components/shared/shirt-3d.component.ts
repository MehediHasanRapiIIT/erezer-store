import { AfterViewInit, Component, ElementRef, NgZone, OnDestroy, effect, inject, input, output, signal, viewChild } from '@angular/core';
import type { CanvasTexture, Group, Material, Mesh, MeshStandardMaterial, PerspectiveCamera, Raycaster, Scene, Texture, Vector2, Vector3, WebGLRenderer } from 'three';
import type { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import type { CustomDesignView } from '../../core/api.models';
import { REGULAR_SHIRT, type ShirtModel } from '../../core/shirt-models';

/**
 * Where the view goes round to for each side, in radians around the shirt.
 * Left and right are the wearer's: the right sleeve is on your left as you look
 * at the front of the shirt, which is the way the print team reads them.
 */
const FACING: Record<CustomDesignView, number> = {
  front: 0,
  back: Math.PI,
  rightSleeve: -1.15,
  leftSleeve: 1.15,
};

/** Clear border round the artwork, so its edge pixels don't smear across the rest of the cloth. */
const PAD = 24;

/** What the shirt asks of the design tool when it's touched. Positions are fractions of a side's printable area. */
export interface ShirtDesigner {
  artAt(view: CustomDesignView, x: number, y: number): boolean;
  /** A finger came down on artwork or tapped the shirt. Selects it (changing side if need be). */
  grab(view: CustomDesignView, x: number, y: number): void;
  moveBy(dx: number, dy: number): void;
  hasSelection(): boolean;
  beginPinch(): void;
  pinch(scale: number, turnDegrees: number): void;
  /** The mouse wheel over the selected design: a step bigger or smaller. */
  sizeStep(bigger: boolean): void;
  release(): void;
  deselect(): void;
}

/** A selected thing's outline on the cloth. */
export interface ShirtBox {
  x: number;
  y: number;
  w: number;
  h: number;
  angle: number;
}

/** The order's pictures: the plain backdrop behind the shirt, and their size in pixels. */
const PICTURE_BACKDROP = '#e5e7eb';
const PICTURE_SIZE = 1200;
/** How far the camera stands from a sleeve's design: the sleeve fills the picture, with a little of the body. */
const SLEEVE_PICTURE_DISTANCE = 1.15;

/** Fingers moving less than this (pixels) are a tap, not a drag. */
const TAP_SLOP = 8;
/** How close to a corner (pixels on screen) a finger counts as on its handle. */
const HANDLE_REACH = 26;
/** A corner handle's size, in the pattern's units. */
const HANDLE_SIZE = 26;

/**
 * The T-shirt in 3D: drag to spin it, and it takes the colour it's given. Three.js
 * and the model load only when this appears, so no other page pays for them.
 * "T Shirt" by funlab117, CC BY 4.0 (see the credit on the Custom Design page).
 */
@Component({
  selector: 'app-shirt-3d',
  standalone: true,
  template: `
    <div #host class="relative h-full w-full select-none" data-testid="shirt-3d">
      @if (loading()) {
        <p class="absolute inset-0 flex items-center justify-center text-sm text-neutral-500 dark:text-neutral-400">Loading 3D shirt…</p>
      }
    </div>
  `,
  styles: [':host { display: block; width: 100%; height: 100%; }'],
})
export class Shirt3dComponent implements AfterViewInit, OnDestroy {
  private readonly zone = inject(NgZone);

  /** Which shirt this is. It's chosen once: the page makes a new one to change it. */
  readonly model = input<ShirtModel>(REGULAR_SHIRT);
  /** The shirt's colour, like #1a2b3c. */
  readonly color = input('#ffffff');
  /** The skin colour of the man wearing it, in the Preview. */
  readonly skin = input('#ffffff');
  /** 3D can't run on this device: the page goes back to the flat view. */
  readonly failed = output<void>();
  /** The customer spun round to another side. */
  readonly sideChange = output<CustomDesignView>();
  /** Set by the page: what a touch on the artwork does. */
  designer: ShirtDesigner | null = null;

  protected readonly loading = signal(true);

  private readonly host = viewChild.required<ElementRef<HTMLDivElement>>('host');
  private renderer?: WebGLRenderer;
  private scene?: Scene;
  private camera?: PerspectiveCamera;
  private controls?: OrbitControls;
  private cloth?: MeshStandardMaterial;
  private resize?: ResizeObserver;
  private THREE?: typeof import('three');
  private parts = new Map<string, Mesh>();
  /** The artwork in place on the cloth, side by side. */
  private prints = new Map<CustomDesignView, { mesh: Mesh; texture: CanvasTexture; sheet: HTMLCanvasElement }>();
  /** Where each side's printable area sits on the cloth, in pattern coordinates. */
  private areas = new Map<CustomDesignView, { left: number; top: number; width: number; height: number }>();
  /** Artwork that arrived before the model finished loading. */
  private waiting = new Map<CustomDesignView, HTMLCanvasElement | null>();
  private frame = 0;
  private ray?: Raycaster;
  private pointer?: Vector2;
  /** Fingers on the screen. */
  private fingers = new Map<number, { x: number; y: number }>();
  private gesture: {
    kind: 'drag' | 'pinch' | 'tap' | 'resize';
    startX: number;
    startY: number;
    moved: boolean;
    view: CustomDesignView;
    x: number;
    y: number;
    distance?: number;
    angle?: number;
  } | null = null;
  private spinTimer?: ReturnType<typeof setTimeout>;
  private facing: CustomDesignView = 'front';
  /** The selected design's frame, side by side, for its corner handles. */
  private boxes = new Map<CustomDesignView, ShirtBox>();
  private destroyed = false;
  private dirty = true;
  /** The man who wears the shirt in the Preview. He loads the first time he's asked for. */
  private person?: Group;
  private personLoad?: Promise<boolean>;
  private personSkin?: MeshStandardMaterial;
  private personPictures: Texture[] = [];
  private fabricPictures: Texture[] = [];
  private wearing = false;
  /** Going round to a chosen side. */
  private turn: { from: number; to: number; at: number } | null = null;

  constructor() {
    effect(() => this.paint(this.color()));
    effect(() => {
      const skin = this.skin();
      this.personSkin?.color.set(skin);
      this.dirty = true;
    });
  }

  ngAfterViewInit(): void {
    // Drawing runs outside Angular, so a spinning shirt doesn't re-check the page 60 times a second.
    this.zone.runOutsideAngular(() => void this.start());
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    cancelAnimationFrame(this.frame);
    clearTimeout(this.spinTimer);
    this.renderer?.domElement.parentElement?.removeEventListener('wheel', this.onWheel);
    window.removeEventListener('pointermove', this.onMove);
    window.removeEventListener('pointerup', this.onUp);
    window.removeEventListener('pointercancel', this.onUp);
    this.resize?.disconnect();
    this.controls?.dispose();
    this.prints.forEach((p) => p.texture.dispose());
    this.personPictures.forEach((t) => t.dispose());
    this.fabricPictures.forEach((t) => t.dispose());
    this.scene?.traverse((o) => {
      const mesh = o as { geometry?: { dispose(): void }; material?: Material | Material[] };
      mesh.geometry?.dispose();
      (Array.isArray(mesh.material) ? mesh.material : mesh.material ? [mesh.material] : []).forEach((m) => m.dispose());
    });
    this.renderer?.dispose();
    this.renderer?.domElement.remove();
  }

  /** The Preview: the shirt turns slowly by itself (unless the customer's device asks for less motion). */
  showcase(on: boolean): void {
    if (!this.controls) return;
    const calm = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    this.controls.autoRotate = on && !calm;
    this.controls.autoRotateSpeed = 2;
    this.dirty = true;
  }

  /** Puts the shirt on the man, or takes it off him. False if he couldn't be loaded. */
  async wear(on: boolean): Promise<boolean> {
    this.wearing = on;
    if (on) {
      this.personLoad ??= this.loadPerson();
      if (!(await this.personLoad)) {
        this.personLoad = undefined;
        this.wearing = false;
        return false;
      }
    }
    if (this.person) this.person.visible = this.wearing;
    this.fit();
    return true;
  }

  private async loadPerson(): Promise<boolean> {
    const { THREE, scene } = this;
    if (!THREE || !scene) return false;
    try {
      const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
      const pictures = new THREE.TextureLoader();
      const [gltf, skin, bumps, eye] = await Promise.all([
        new GLTFLoader().loadAsync(this.model().person),
        pictures.loadAsync('models/man-skin.jpg'),
        pictures.loadAsync('models/man-skin-normal.jpg'),
        pictures.loadAsync('models/man-eye.jpg'),
      ]);
      this.personPictures = [skin, bumps, eye];
      if (this.destroyed) {
        this.personPictures.forEach((t) => t.dispose());
        return false;
      }
      // The model is laid out like its own pictures, top left first.
      for (const t of this.personPictures) t.flipY = false;
      skin.colorSpace = THREE.SRGBColorSpace;
      eye.colorSpace = THREE.SRGBColorSpace;
      // Nearly matt, with the fine shape turned down: any shinier and the skin shows white patches.
      this.personSkin = new THREE.MeshStandardMaterial({
        map: skin, normalMap: bumps, normalScale: new THREE.Vector2(0.5, 0.5), color: this.skin(), roughness: 0.95, metalness: 0,
      });
      const hair = new THREE.MeshStandardMaterial({ color: '#17120f', roughness: 0.8, metalness: 0 });
      const looks: Record<string, Material> = {
        skin: this.personSkin,
        trousers: new THREE.MeshStandardMaterial({ color: '#2f3239', roughness: 0.85, metalness: 0 }),
        // Matt too: a shiny eye turns blank white whenever it faces a light.
        eyes: new THREE.MeshStandardMaterial({ map: eye, roughness: 0.9, metalness: 0 }),
        brows: hair,
        hair,
      };
      gltf.scene.traverse((o) => {
        const mesh = o as Mesh;
        if (mesh.isMesh) mesh.material = looks[mesh.name.replace(/_\d+$/, '')] ?? hair;
      });
      this.person = gltf.scene;
      this.person.visible = this.wearing;
      scene.add(this.person);
      return true;
    } catch (e) {
      console.warn('The man for the Preview could not load', e);
      return false;
    }
  }

  /** Goes round to one side of the shirt (the Front, Back and sleeve buttons). */
  face(view: CustomDesignView): void {
    if (!this.controls) return;
    const now = this.controls.getAzimuthalAngle();
    // The short way round.
    const goal = FACING[view];
    const delta = Math.atan2(Math.sin(goal - now), Math.cos(goal - now));
    this.facing = view;
    this.turn = { from: now, to: now + delta, at: performance.now() };
    this.dirty = true;
  }

  /**
   * Pictures of the finished shirt from the given sides, for the order. The real
   * shirt is drawn, so the design sits exactly where the customer put it. The
   * page takes the guides off first. A side that can't be shown is left out.
   */
  async pictures(views: CustomDesignView[]): Promise<{ view: CustomDesignView; blob: Blob }[]> {
    const { renderer, scene, camera, controls, THREE } = this;
    if (!renderer || !scene || !camera || !controls || !THREE) return [];
    const shown = views.filter((v) => this.prints.get(v)?.mesh.visible);
    const place = camera.position.clone(), aspect = camera.aspect, ratio = renderer.getPixelRatio();
    const { clientWidth: w, clientHeight: h } = renderer.domElement;
    // All in one go, so the customer never sees the shirt jump.
    renderer.setPixelRatio(1);
    renderer.setSize(PICTURE_SIZE, PICTURE_SIZE, false);
    scene.background = new THREE.Color(PICTURE_BACKDROP);
    camera.aspect = 1;
    camera.updateProjectionMatrix();
    const urls = shown.map((view) => {
      // A sleeve is small and curves away, so its picture looks straight at the design.
      const spot = view === 'leftSleeve' || view === 'rightSleeve' ? this.designSpot(view) : null;
      if (spot) {
        camera.position.copy(spot.point).addScaledVector(spot.normal, SLEEVE_PICTURE_DISTANCE);
        camera.lookAt(spot.point);
      } else {
        camera.position.setFromSphericalCoords(2.15, Math.PI / 2, FACING[view]);
        camera.lookAt(controls.target);
      }
      renderer.render(scene, camera);
      return { view, url: renderer.domElement.toDataURL('image/png') };
    });
    scene.background = null;
    renderer.setPixelRatio(ratio);
    renderer.setSize(w, h, false);
    camera.aspect = aspect;
    camera.position.copy(place);
    camera.lookAt(controls.target);
    camera.updateProjectionMatrix();
    this.dirty = true;
    return Promise.all(urls.map(async ({ view, url }) => ({ view, blob: await (await fetch(url)).blob() })));
  }

  /** Where a side's design is on the shirt, and the way that bit of cloth faces. Null if it can't tell. */
  private designSpot(view: CustomDesignView): { point: Vector3; normal: Vector3 } | null {
    const THREE = this.THREE, print = this.prints.get(view), area = this.areas.get(view);
    const part = this.parts.get(this.model().places[view].part);
    if (!THREE || !print || !area || !part) return null;
    // The middle of what's drawn on this side.
    const { sheet } = print;
    const pixels = sheet.getContext('2d')?.getImageData(0, 0, sheet.width, sheet.height).data;
    if (!pixels) return null;
    let left = Infinity, top = Infinity, right = -1, bottom = -1;
    for (let y = 0; y < sheet.height; y += 2) {
      for (let x = 0; x < sheet.width; x += 2) {
        if (pixels[(y * sheet.width + x) * 4 + 3] < 40) continue;
        left = Math.min(left, x); right = Math.max(right, x); top = Math.min(top, y); bottom = Math.max(bottom, y);
      }
    }
    if (right < 0) return null;
    const w = sheet.width - PAD * 2, h = sheet.height - PAD * 2;
    const u = area.left + (((left + right) / 2 - PAD) / w) * area.width;
    const v = area.top + (((top + bottom) / 2 - PAD) / h) * area.height;
    // The cloth there. A shirt with an inside layer has two points in the same place of
    // the pattern: the outside one is farther from the middle of the shirt.
    const { uv, position, normal } = part.geometry.attributes;
    const point = new THREE.Vector3(), best = new THREE.Vector3(), bestNormal = new THREE.Vector3();
    let closest = Infinity;
    for (let i = 0; i < uv.count; i++) {
      const gap = Math.hypot(uv.getX(i) - u, uv.getY(i) - v);
      if (gap > closest + 2) continue;
      point.fromBufferAttribute(position, i).applyMatrix4(part.matrixWorld);
      const better = gap < closest - 2 || Math.hypot(point.x, point.z) > Math.hypot(best.x, best.z);
      if (!better) continue;
      closest = Math.min(closest, gap);
      best.copy(point);
      bestNormal.fromBufferAttribute(normal, i);
    }
    if (closest === Infinity) return null;
    bestNormal.applyMatrix3(new THREE.Matrix3().getNormalMatrix(part.matrixWorld));
    // Mostly level, or a design near the shoulder would be seen from far above.
    bestNormal.y *= 0.3;
    // Out from the shirt, whichever way the file's normal points.
    if (bestNormal.x * best.x + bestNormal.z * best.z < 0) bestNormal.negate();
    if (bestNormal.lengthSq() < 1e-6) return null;
    return { point: best, normal: bestNormal.normalize() };
  }

  /** The side facing the customer, nearest to where the view has turned. */
  private nearestSide(): CustomDesignView {
    const a = this.controls?.getAzimuthalAngle() ?? 0;
    if (Math.abs(a) < 0.65) return 'front';
    if (Math.abs(a) > 2.5) return 'back';
    return a < 0 ? 'rightSleeve' : 'leftSleeve';
  }

  /** What the finger is over: a side of the shirt, and where on it, in printable-area fractions. */
  private hit(clientX: number, clientY: number): { view: CustomDesignView; x: number; y: number } | null {
    const el = this.renderer?.domElement;
    if (!el || !this.ray || !this.pointer || !this.camera) return null;
    const box = el.getBoundingClientRect();
    this.pointer.set(((clientX - box.left) / box.width) * 2 - 1, -((clientY - box.top) / box.height) * 2 + 1);
    this.ray.setFromCamera(this.pointer, this.camera);
    const found = this.ray.intersectObjects([...this.parts.values()], false)[0];
    if (!found?.uv) return null;
    const places = this.model().places;
    const view = (Object.keys(places) as CustomDesignView[]).find((v) => places[v].part === found.object.name);
    if (!view) return null;
    const area = this.areas.get(view) ?? this.defaultArea(view);
    return { view, x: (found.uv.x - area.left) / area.width, y: (found.uv.y - area.top) / area.height };
  }

  /** The frame's four corners, in the pattern's units. */
  private corners(view: CustomDesignView, box: ShirtBox): { u: number; v: number }[] {
    const area = this.areas.get(view) ?? this.defaultArea(view);
    const cx = area.left + box.x * area.width, cy = area.top + box.y * area.height;
    const hw = (box.w * area.width) / 2, hh = (box.h * area.height) / 2;
    const a = (box.angle * Math.PI) / 180;
    return [[-1, -1], [1, -1], [1, 1], [-1, 1]].map(([sx, sy]) => {
      const x = sx * hw, y = sy * hh;
      return { u: cx + x * Math.cos(a) - y * Math.sin(a), v: cy + x * Math.sin(a) + y * Math.cos(a) };
    });
  }

  /** Is the finger on a corner handle of the selected design? Returns its distance from the design's middle. */
  private onHandle(spot: { view: CustomDesignView; x: number; y: number }): number | null {
    const box = this.boxes.get(spot.view);
    const el = this.renderer?.domElement;
    if (!box || !el) return null;
    const area = this.areas.get(spot.view) ?? this.defaultArea(spot.view);
    const u = area.left + spot.x * area.width, v = area.top + spot.y * area.height;
    // The shirt is about this tall on screen, so a finger's reach is the same size on a phone and a monitor.
    const unitsPerPixel = 703 / (el.clientHeight * 0.8);
    const reach = HANDLE_REACH * unitsPerPixel;
    if (!this.corners(spot.view, box).some((c) => Math.hypot(c.u - u, c.v - v) <= reach)) return null;
    // On a small design a finger reaches the corners from its middle, which must still move it.
    const middle = this.fromMiddle(spot, box);
    return middle < Math.hypot(box.w * area.width, box.h * area.height) * 0.3 ? null : middle;
  }

  private fromMiddle(spot: { view: CustomDesignView; x: number; y: number }, box: ShirtBox): number {
    const area = this.areas.get(spot.view) ?? this.defaultArea(spot.view);
    return Math.hypot((spot.x - box.x) * area.width, (spot.y - box.y) * area.height);
  }

  private defaultArea(view: CustomDesignView): { left: number; top: number; width: number; height: number } {
    const p = this.model().places[view];
    return { left: p.u - p.width / 2, top: p.top, width: p.width, height: p.width };
  }

  private listen(el: HTMLElement): void {
    // On the capture path, so the decision is made before the spin control hears the finger.
    el.addEventListener('pointerdown', this.onDown, { capture: true });
    el.addEventListener('wheel', this.onWheel, { passive: false });
    window.addEventListener('pointermove', this.onMove);
    window.addEventListener('pointerup', this.onUp);
    window.addEventListener('pointercancel', this.onUp);
  }

  private onDown = (e: PointerEvent): void => {
    this.fingers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const designer = this.designer;
    if (!designer || !this.controls) return;

    if (this.fingers.size === 2 && designer.hasSelection()) {
      const [a, b] = [...this.fingers.values()];
      this.controls.enabled = false;
      this.gesture = {
        kind: 'pinch', startX: e.clientX, startY: e.clientY, moved: true, view: this.facing, x: 0, y: 0,
        distance: Math.hypot(b.x - a.x, b.y - a.y), angle: Math.atan2(b.y - a.y, b.x - a.x),
      };
      this.zone.run(() => designer.beginPinch());
      return;
    }
    if (this.fingers.size !== 1) return;
    const spot = this.hit(e.clientX, e.clientY);
    // A corner of the selected design: resize, even where the corner is past the artwork.
    const reach = spot ? this.onHandle(spot) : null;
    if (spot && reach !== null) {
      this.controls.enabled = false;
      this.gesture = { kind: 'resize', startX: e.clientX, startY: e.clientY, moved: false, view: spot.view, x: spot.x, y: spot.y, distance: reach };
      this.zone.run(() => designer.beginPinch());
      return;
    }
    const onArt = !!spot && designer.artAt(spot.view, spot.x, spot.y);
    this.controls.enabled = !onArt;
    this.gesture = {
      kind: onArt ? 'drag' : 'tap', startX: e.clientX, startY: e.clientY, moved: false,
      view: spot?.view ?? this.facing, x: spot?.x ?? 0, y: spot?.y ?? 0,
    };
    if (onArt && spot) this.zone.run(() => designer.grab(spot.view, spot.x, spot.y));
  };

  private onMove = (e: PointerEvent): void => {
    if (!this.fingers.has(e.pointerId)) return;
    this.fingers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    const g = this.gesture;
    const designer = this.designer;
    if (!g || !designer) return;
    if (!g.moved && Math.hypot(e.clientX - g.startX, e.clientY - g.startY) > TAP_SLOP) g.moved = true;
    if (g.kind === 'pinch' && this.fingers.size >= 2) {
      const [a, b] = [...this.fingers.values()];
      const scale = Math.hypot(b.x - a.x, b.y - a.y) / (g.distance || 1);
      const turn = ((Math.atan2(b.y - a.y, b.x - a.x) - (g.angle ?? 0)) * 180) / Math.PI;
      this.zone.run(() => designer.pinch(scale, turn));
    } else if (g.kind === 'resize' && g.moved) {
      const spot = this.hit(e.clientX, e.clientY);
      const box = this.boxes.get(g.view);
      if (!spot || spot.view !== g.view || !box) return;
      const scale = this.fromMiddle(spot, box) / (g.distance || 1);
      this.zone.run(() => designer.pinch(scale, 0));
    } else if (g.kind === 'drag' && g.moved) {
      const spot = this.hit(e.clientX, e.clientY);
      // Off the edge of that part: the design waits at the edge for the finger to come back.
      if (!spot || spot.view !== g.view) return;
      const dx = spot.x - g.x, dy = spot.y - g.y;
      g.x = spot.x;
      g.y = spot.y;
      this.zone.run(() => designer.moveBy(dx, dy));
    }
  };

  private onUp = (e: PointerEvent): void => {
    if (!this.fingers.delete(e.pointerId)) return;
    const g = this.gesture;
    const designer = this.designer;
    if (this.fingers.size > 0 || !g) return;
    this.gesture = null;
    if (this.controls) this.controls.enabled = true;
    if (!designer) return;
    if (g.kind === 'tap' && !g.moved) this.zone.run(() => designer.deselect());
    else if (g.kind !== 'tap' && g.moved) this.zone.run(() => designer.release());
  };

  /** The mouse wheel over the selected design makes it bigger or smaller; anywhere else the page scrolls. */
  private onWheel = (e: WheelEvent): void => {
    const designer = this.designer;
    if (!designer?.hasSelection()) return;
    const spot = this.hit(e.clientX, e.clientY);
    const box = spot ? this.boxes.get(spot.view) : undefined;
    if (!spot || !box || !designer.artAt(spot.view, spot.x, spot.y)) return;
    e.preventDefault();
    this.zone.run(() => designer.sizeStep(e.deltaY < 0));
  };

  /** Puts one side's artwork on the shirt (null takes it off). Follows every change to the design. */
  setDesign(view: CustomDesignView, layer: HTMLCanvasElement | null, selected: ShirtBox | null = null): void {
    if (!this.THREE || this.parts.size === 0) {
      this.waiting.set(view, layer);
      return;
    }
    if (selected) this.boxes.set(view, selected);
    else this.boxes.delete(view);
    const print = this.prints.get(view);
    if (!layer) {
      if (print) {
        print.mesh.visible = false;
        this.dirty = true;
      }
      return;
    }
    const ready = print ?? this.makePrint(view);
    if (!ready) return;
    const { sheet, texture, mesh } = ready;
    const w = layer.width, h = layer.height;
    const size = sheet.width !== w + PAD * 2 || sheet.height !== h + PAD * 2;
    if (size) {
      sheet.width = w + PAD * 2;
      sheet.height = h + PAD * 2;
      texture.dispose();
    }
    const ctx = sheet.getContext('2d');
    if (!ctx) return;
    ctx.clearRect(0, 0, sheet.width, sheet.height);
    ctx.drawImage(layer, PAD, PAD);
    // Never quite to the sheet's edge: whatever touches it would smear across the cloth.
    if (selected) {
      ctx.save();
      ctx.beginPath();
      ctx.rect(4, 4, sheet.width - 8, sheet.height - 8);
      ctx.clip();
      // Never bigger than a quarter of a small design.
      const handle = Math.min((HANDLE_SIZE / this.model().places[view].width) * w, Math.min(selected.w * w, selected.h * h) * 0.25);
      this.outline(ctx, w, h, selected, handle);
      ctx.restore();
    }
    try {
      // A picture from a server that doesn't allow copying can't go on the shirt;
      // the 3D drawing would stop for good if it tried.
      ctx.getImageData(0, 0, 1, 1);
    } catch {
      console.warn('The 3D shirt could not use this picture');
      mesh.visible = false;
      return;
    }
    texture.needsUpdate = true;

    // From pattern coordinates to this picture: the printable area keeps its shape.
    const place = this.model().places[view];
    const width = place.width, height = width * (h / w);
    const left = place.u - width / 2;
    const top = place.top;
    const su = (w / sheet.width) / width, sv = (h / sheet.height) / height;
    texture.matrix.set(su, 0, PAD / sheet.width - left * su, 0, sv, PAD / sheet.height - top * sv, 0, 0, 1);
    this.areas.set(view, { left, top, width, height });
    mesh.visible = true;
    this.dirty = true;
  }

  /** The selected thing's frame, drawn on the cloth while editing. */
  private outline(ctx: CanvasRenderingContext2D, w: number, h: number, box: ShirtBox, handle: number): void {
    ctx.save();
    const line = Math.max(2, w / 220);
    // Each line twice, white under blue, so it shows on a white shirt and a black one alike.
    const both = (draw: () => void) => {
      ctx.lineWidth = line * 2;
      ctx.strokeStyle = 'rgba(255, 255, 255, 0.75)';
      draw();
      ctx.lineWidth = line;
      ctx.strokeStyle = '#2563eb';
      draw();
    };
    ctx.translate(PAD + box.x * w, PAD + box.y * h);
    ctx.rotate((box.angle * Math.PI) / 180);
    const fw = box.w * w, fh = box.h * h;
    both(() => ctx.strokeRect(-fw / 2, -fh / 2, fw, fh));
    // The corner handles: drag one to make the design bigger or smaller.
    for (const [sx, sy] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const x = (sx * fw) / 2 - handle / 2, y = (sy * fh) / 2 - handle / 2;
      ctx.fillStyle = '#ffffff';
      ctx.fillRect(x, y, handle, handle);
      ctx.lineWidth = line;
      ctx.strokeStyle = '#2563eb';
      ctx.strokeRect(x, y, handle, handle);
    }
    ctx.restore();
  }

  /** A second skin over one part of the shirt, carrying that side's artwork in its true colours. */
  private makePrint(view: CustomDesignView): { mesh: Mesh; texture: CanvasTexture; sheet: HTMLCanvasElement } | null {
    const THREE = this.THREE;
    const part = this.parts.get(this.model().places[view].part);
    if (!THREE || !part) return null;
    const sheet = document.createElement('canvas');
    const texture = new THREE.CanvasTexture(sheet);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.flipY = false;
    texture.matrixAutoUpdate = false;
    texture.anisotropy = this.renderer?.capabilities.getMaxAnisotropy() ?? 1;
    const material = new THREE.MeshStandardMaterial({
      map: texture, transparent: true, depthWrite: false, roughness: 0.85, metalness: 0,
      // The outside of the cloth only: looking into a sleeve must not show the print.
      side: THREE.FrontSide, polygonOffset: true, polygonOffsetFactor: -2, polygonOffsetUnits: -2,
    });
    const mesh = new THREE.Mesh(part.geometry, material);
    mesh.renderOrder = 1;
    part.add(mesh);
    const print = { mesh, texture, sheet };
    this.prints.set(view, print);
    return print;
  }

  private async start(): Promise<void> {
    try {
      const THREE = await import('three');
      this.THREE = THREE;
      const { GLTFLoader } = await import('three/examples/jsm/loaders/GLTFLoader.js');
      const { OrbitControls } = await import('three/examples/jsm/controls/OrbitControls.js');
      if (this.destroyed) return;

      const box = this.host().nativeElement;
      const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
      renderer.toneMapping = THREE.NoToneMapping;
      box.appendChild(renderer.domElement);
      renderer.domElement.style.touchAction = 'none';
      renderer.domElement.setAttribute('aria-label', 'T-shirt in 3D. Drag to spin it.');
      renderer.domElement.setAttribute('data-testid', 'shirt-3d-canvas');
      this.renderer = renderer;

      const scene = new THREE.Scene();
      this.scene = scene;
      const camera = new THREE.PerspectiveCamera(30, 1, 0.01, 20);
      camera.position.set(0, 0.02, 2.15);
      this.camera = camera;

      // Soft light from everywhere, plus a little from the front-left for the folds.
      scene.add(new THREE.HemisphereLight(0xffffff, 0x9a9a9a, 1.75));
      const key = new THREE.DirectionalLight(0xffffff, 1.5);
      key.position.set(-1.2, 1.6, 2.4);
      scene.add(key);
      const back = new THREE.DirectionalLight(0xffffff, 1.5);
      back.position.set(1.2, 1, -2.4);
      scene.add(back);

      const model = this.model();
      const gltf = await new GLTFLoader().loadAsync(model.file);
      if (this.destroyed) return;
      const cloth = new THREE.MeshStandardMaterial({ color: this.color(), roughness: 0.92, metalness: 0, side: THREE.DoubleSide });
      if (model.fabric) {
        const pictures = new THREE.TextureLoader();
        const [map, bumps] = await Promise.all([pictures.loadAsync(model.fabric.map), pictures.loadAsync(model.fabric.normal)]);
        this.fabricPictures = [map, bumps];
        if (this.destroyed) return;
        // These follow the file's own layout, its second UV set: the first is where designs go.
        for (const t of this.fabricPictures) {
          t.flipY = false;
          t.channel = 1;
        }
        map.colorSpace = THREE.SRGBColorSpace;
        cloth.map = map;
        cloth.normalMap = bumps;
        // Nearly matt: any shinier and dark shirts show bright streaks along the folds.
        cloth.normalScale.set(0.6, 0.6);
        cloth.roughness = 0.97;
      }
      gltf.scene.traverse((o) => {
        const mesh = o as Mesh;
        if (mesh.isMesh) {
          mesh.material = cloth;
          this.parts.set(mesh.name || mesh.parent?.name || '', mesh);
        }
      });
      this.cloth = cloth;
      for (const [view, layer] of this.waiting) this.setDesign(view, layer);
      this.waiting.clear();
      scene.add(gltf.scene);

      // Dragging goes round the shirt; up and down only tips the view a little.
      const controls = new OrbitControls(camera, renderer.domElement);
      controls.enablePan = false;
      controls.enableZoom = false;
      controls.enableDamping = true;
      controls.dampingFactor = 0.1;
      controls.rotateSpeed = 0.8;
      controls.minPolarAngle = Math.PI * 0.36;
      controls.maxPolarAngle = Math.PI * 0.6;
      controls.addEventListener('change', () => { this.dirty = true; });
      // When the spin settles, the side now in front is the one new artwork goes on.
      controls.addEventListener('end', () => {
        clearTimeout(this.spinTimer);
        this.spinTimer = setTimeout(() => this.settle(), 350);
      });
      this.controls = controls;
      this.ray = new THREE.Raycaster();
      this.pointer = new THREE.Vector2();
      this.listen(box);

      this.resize = new ResizeObserver(() => this.fit());
      this.resize.observe(box);
      this.fit();
      this.zone.run(() => this.loading.set(false));
      this.draw();
    } catch (e) {
      console.warn('The 3D shirt could not start', e);
      this.zone.run(() => { this.loading.set(false); this.failed.emit(); });
    }
  }

  private settle(): void {
    const side = this.nearestSide();
    if (side === this.facing) return;
    this.facing = side;
    this.zone.run(() => this.sideChange.emit(side));
  }

  private fit(): void {
    const box = this.host().nativeElement;
    const { clientWidth: w, clientHeight: h } = box;
    if (!w || !h || !this.renderer || !this.camera) return;
    this.renderer.setSize(w, h);
    this.camera.aspect = w / h;
    // Far enough back that the sleeves fit on a tall, narrow screen as well as a wide one.
    // The man is seen down to his upper legs, and his hands fit on a phone too.
    const distance = this.wearing ? Math.max(4.1, 2.4 / this.camera.aspect) : Math.max(2.15, 1.9 / this.camera.aspect);
    this.camera.position.setLength(distance);
    this.camera.updateProjectionMatrix();
    this.dirty = true;
  }

  private paint(hex: string): void {
    this.dirty = true;
    this.cloth?.color.set(hex || '#ffffff');
  }

  private draw = (): void => {
    if (this.destroyed) return;
    this.frame = requestAnimationFrame(this.draw);
    if (this.turn && this.camera && this.controls) {
      const t = Math.min((performance.now() - this.turn.at) / 450, 1);
      const eased = 1 - (1 - t) ** 3;
      const angle = this.turn.from + (this.turn.to - this.turn.from) * eased;
      this.camera.position.setFromSphericalCoords(this.camera.position.length(), this.controls.getPolarAngle(), angle);
      this.dirty = true;
      if (t >= 1) this.turn = null;
    }
    this.controls?.update();
    // Nothing moving: nothing to draw, which keeps a phone cool.
    if (!this.dirty || !this.renderer || !this.scene || !this.camera) return;
    this.dirty = false;
    this.renderer.render(this.scene, this.camera);
  };
}
