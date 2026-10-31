import type { CustomDesignView } from './api.models';

/**
 * The 3D shirts. Each one's details live here, so the shirt on screen and the design
 * tool behind it agree: which file, where each side sits on the cloth, and the shape
 * of the cloth a design can move on. To add a shirt, add it here and to shirtModelFor.
 */
export interface ShirtModel {
  file: string;
  /**
   * Where each side's artwork sits, in the flat sewing pattern the model comes with
   * (u runs to the right seen from outside, v runs down, so nothing is mirrored): the
   * part, the middle of the area across, its width and its top. Each is the whole
   * piece, so a design goes anywhere on it. The wearer's right sleeve is on your left
   * as you look at the shirt, which is how the model's parts are named.
   */
  places: Record<CustomDesignView, { part: string; u: number; width: number; top: number }>;
  /** Width over height of a front or back panel, and of a sleeve laid flat. */
  bodyShape: number;
  sleeveShape: number;
  /** Where something new starts on a sleeve: the side that faces out, as fractions of the sleeve. */
  sleeveStart: { rightSleeve: number; leftSleeve: number; y: number };
  /** Pictures of the cloth, tinted by the shirt colour. Without them it's plain colour. */
  fabric?: { map: string; normal: string };
  /** Its licence (CC BY 4.0) asks for this wherever it's shown. */
  credit: { title: string; url: string; by: string };
  /** The man in the Preview is fitted to this shirt. */
  person: boolean;
}

export const REGULAR_SHIRT: ShirtModel = {
  file: 'models/tshirt.glb',
  places: {
    front: { part: 'front', u: 0, width: 512, top: -406 },
    back: { part: 'back', u: 0, width: 512, top: -385 },
    rightSleeve: { part: 'sleeveLeft', u: 0, width: 398, top: -102 },
    leftSleeve: { part: 'sleeveRight', u: 0, width: 398, top: -102 },
  },
  bodyShape: 512 / 703,
  sleeveShape: 398 / 204,
  sleeveStart: { rightSleeve: 0.47, leftSleeve: 0.54, y: 0.64 },
  credit: { title: 'T Shirt', url: 'https://skfb.ly/ozHJJ', by: 'funlab117' },
  person: true,
};

export const DROP_SHOULDER_SHIRT: ShirtModel = {
  file: 'models/tshirt-drop.glb',
  places: {
    front: { part: 'front', u: 0, width: 512, top: -320 },
    back: { part: 'back', u: 0, width: 512, top: -320 },
    rightSleeve: { part: 'sleeveLeft', u: 0, width: 401, top: -100 },
    leftSleeve: { part: 'sleeveRight', u: 0, width: 401, top: -100 },
  },
  bodyShape: 512 / 640,
  sleeveShape: 401 / 200,
  sleeveStart: { rightSleeve: 0.55, leftSleeve: 0.43, y: 0.58 },
  fabric: { map: 'models/tshirt-drop-cloth.jpg', normal: 'models/tshirt-drop-normal.jpg' },
  credit: {
    title: 'oversized_t-shirt',
    url: 'https://sketchfab.com/3d-models/oversized-t-shirt-4b64d0c95d2248aa9dd096c9cafe1d8d',
    by: 'tech.service.allwe',
  },
  person: false,
};

/** The 3D shirt for a garment, by the name the admin gave it; null means it has none. */
export function shirtModelFor(itemName: string): ShirtModel | null {
  if (/drop[\s-]*shoulder|over[\s-]*size/i.test(itemName)) return DROP_SHOULDER_SHIRT;
  return /t-?\s?shirt|\btee\b/i.test(itemName) ? REGULAR_SHIRT : null;
}
