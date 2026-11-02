/**
 * Makes public/models/man.glb and its pictures: the man who wears the shirt in the
 * Custom Design Preview. Each 3D shirt has its own fitting of him, the drop shoulder
 * one in man-drop.glb (it shares the pictures):
 *
 *   node model-source/bake_man.mjs "C:/path/to/Universal Base Characters[Standard]"
 *   node model-source/bake_man.mjs "C:/path/to/Universal Base Characters[Standard]" drop
 *
 * The man is "Universal Base Characters" by Quaternius (CC0). The pack is not kept
 * here. This poses him for the shop's shirt (public/models/tshirt.glb) and fixes that
 * pose into one plain model, so the shop needs no skeleton:
 *  - slims the superhero's arms and legs, and curls his fingers a little,
 *  - aims each arm down its sleeve,
 *  - removes all of him the shirt covers, so nothing can poke through the cloth,
 *  - shapes the neck into a column that goes down inside the collar,
 *  - drops the lower legs, which the Preview never shows.
 *
 * It runs in a browser because three.js needs one to read the pack's pictures.
 * Set PLAYWRIGHT to the playwright-core index.mjs to use, if it's not the default.
 */
import { createServer } from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const store = join(here, '..');
const pack = process.argv[2];
const shirt = process.argv[3] ?? 'regular';
/** His hair, from the pack's Hairstyles: one or more of SimpleParted, Buzzed, Beard. */
const hairstyles = (process.env.HAIR ?? 'Buzzed').split(',');
if (!pack) throw new Error('Give the folder of the Universal Base Characters pack.');
const { chromium } = await import(process.env.PLAYWRIGHT ?? pathToFileURL('D:/erezer-new/node_modules/playwright-core/index.mjs').href);

const roots = [
  ['/three/', join(store, 'node_modules/three/')],
  ['/models/', join(store, 'public/models/')],
  ['/pack/', join(pack, 'Base Characters/Godot - UE/')],
  ['/hair/', join(pack, 'Hairstyles/Origin at 0/glTF (Godot)/')],
  ['/tex/', join(pack, 'Base Characters/Textures/')],
];
const types = { '.js': 'text/javascript', '.png': 'image/png', '.gltf': 'application/json' };
const page = `<!doctype html><script type="importmap">{"imports":{"three":"/three/build/three.module.js","three/addons/":"/three/examples/jsm/"}}</script>`;
const server = createServer(async (req, res) => {
  const path = decodeURIComponent(req.url.split('?')[0]);
  const root = roots.find(([p]) => path.startsWith(p));
  try {
    if (!root) { res.writeHead(200, { 'Content-Type': 'text/html' }); res.end(page); return; }
    const body = await readFile(join(root[1], path.slice(root[0].length)));
    res.writeHead(200, { 'Content-Type': types[extname(path)] ?? 'application/octet-stream' });
    res.end(body);
  } catch { res.writeHead(404); res.end(); }
}).listen(4412, '127.0.0.1');

async function bake({ choice, hairstyles }) {
  /** Each shirt: its file, the man's file, and the inside of its collar, where his neck goes. */
  const SHIRTS = {
    regular: { file: 'tshirt.glb', out: 'man.glb', collar: { z: -0.036, rx: 0.09, rz: 0.08 }, pictures: true },
    drop: { file: 'tshirt-drop.glb', out: 'man-drop.glb', collar: { z: -0.038, rx: 0.1, rz: 0.08 }, pictures: false },
  };
  const fit = SHIRTS[choice];
  if (!fit) throw new Error(`No shirt called ${choice}`);
  const THREE = await import('three');
  const { GLTFLoader } = await import('three/addons/loaders/GLTFLoader.js');
  const { GLTFExporter } = await import('three/addons/exporters/GLTFExporter.js');
  const SCALE = 1.32, OFFSET = new THREE.Vector3(0, -1.58, -0.01);
  const HEM = -0.49, LEGS_END = -1.45;
  const COLLAR = fit.collar;

  const loader = new GLTFLoader();
  const [shirt, gltf, hairFiles] = await Promise.all([
    loader.loadAsync(`/models/${fit.file}`),
    loader.loadAsync('/pack/Superhero_Male_FullBody.gltf'),
    Promise.all(hairstyles.map((h) => loader.loadAsync(`/hair/Hair_${h}.gltf`))),
  ]);
  const man = gltf.scene;
  man.scale.setScalar(SCALE);
  man.position.copy(OFFSET);
  shirt.scene.updateMatrixWorld(true);

  const parts = {}, bone = {};
  man.traverse((o) => { if (o.isSkinnedMesh) parts[o.name] = o; if (o.isBone) bone[o.name] = o; });

  // The middle of each sleeve's opening: the sleeve points farthest from the chest.
  const sleeveEnd = (name) => {
    const mesh = shirt.scene.getObjectByName(name), pos = mesh.geometry.attributes.position, pts = [];
    for (let i = 0; i < pos.count; i += 3) pts.push(mesh.localToWorld(new THREE.Vector3().fromBufferAttribute(pos, i)));
    pts.sort((a, b) => (Math.abs(b.x) * 0.8 - b.y * 0.6) - (Math.abs(a.x) * 0.8 - a.y * 0.6));
    const end = pts.slice(0, Math.ceil(pts.length * 0.12));
    return end.reduce((sum, p) => sum.add(p), new THREE.Vector3()).divideScalar(end.length);
  };
  const ends = { l: sleeveEnd('sleeveRight'), r: sleeveEnd('sleeveLeft') };

  const at = (o) => o.getWorldPosition(new THREE.Vector3());
  const turn = (b, q) => {
    const parent = b.parent.getWorldQuaternion(new THREE.Quaternion());
    b.quaternion.premultiply(parent.clone().invert().multiply(q).multiply(parent));
    b.updateWorldMatrix(false, true);
  };
  const aim = (b, child, target) => {
    const from = at(child).sub(at(b)).normalize(), to = target.clone().sub(at(b)).normalize();
    turn(b, new THREE.Quaternion().setFromUnitVectors(from, to));
  };
  const down = new THREE.Vector3(0, 0, 1);

  for (const s of ['l', 'r']) {
    // An everyday build, not the pack's superhero.
    bone[`upperarm_${s}`].scale.set(0.64, 1, 0.64);
    bone[`lowerarm_${s}`].scale.set(0.84, 1, 0.84);
    bone[`hand_${s}`].scale.set(1.08, 1, 1.08);
    bone[`thigh_${s}`].scale.set(0.9, 1, 0.9);
  }
  man.updateMatrixWorld(true);
  for (const s of ['l', 'r']) {
    const side = s === 'l' ? 1 : -1;
    // He stands with his arms straight out, palms down: fingers curl towards the floor.
    for (const finger of ['index', 'middle', 'ring', 'pinky']) {
      [0.15, 0.2, 0.15].forEach((angle, i) => {
        turn(bone[`${finger}_0${i + 1}_${s}`], new THREE.Quaternion().setFromAxisAngle(down, -side * angle));
      });
    }
    aim(bone[`upperarm_${s}`], bone[`lowerarm_${s}`], ends[s]);
    aim(bone[`lowerarm_${s}`], bone[`hand_${s}`], at(bone[`lowerarm_${s}`]).add(new THREE.Vector3(-side * 0.2, -1, 0.1)));
  }
  man.updateMatrixWorld(true);

  /** A skinned mesh's vertices as they stand now, with their normals. */
  const frozen = (mesh) => {
    mesh.skeleton.update();
    const { position, normal } = mesh.geometry.attributes;
    const v = new THREE.Vector3(), o = new THREE.Vector3(), n = new THREE.Vector3();
    const points = [], normals = [];
    for (let i = 0; i < position.count; i++) {
      v.fromBufferAttribute(position, i);
      n.fromBufferAttribute(normal, i);
      o.copy(v).addScaledVector(n, 0.01);
      mesh.applyBoneTransform(i, v);
      mesh.applyBoneTransform(i, o);
      v.applyMatrix4(mesh.matrixWorld);
      o.applyMatrix4(mesh.matrixWorld);
      points.push(v.clone());
      normals.push(o.sub(v).normalize());
    }
    return { points, normals };
  };

  /** A plain mesh from some of those triangles, without the vertices they don't use. */
  const piece = (name, source, { points, normals }, triangles, keepUv) => {
    const seen = new Map(), pos = [], nor = [], uv = [], index = [];
    const { uv: uvs } = source.geometry.attributes;
    for (const i of triangles) {
      if (!seen.has(i)) {
        seen.set(i, seen.size);
        pos.push(points[i].x, points[i].y, points[i].z);
        nor.push(normals[i].x, normals[i].y, normals[i].z);
        if (keepUv) uv.push(uvs.getX(i), uvs.getY(i));
      }
      index.push(seen.get(i));
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    if (keepUv) geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    geometry.setIndex(index);
    const mesh = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial());
    mesh.name = name;
    return mesh;
  };

  // What the shirt covers goes, by which bone moves each vertex.
  const body = parts.SuperHero_Male, shape = frozen(body), count = shape.points.length;
  const { skinIndex, skinWeight } = body.geometry.attributes;
  const mainBone = (i) => {
    let best = 0, most = -1;
    for (let k = 0; k < 4; k++) {
      if (skinWeight.getComponent(i, k) > most) { most = skinWeight.getComponent(i, k); best = skinIndex.getComponent(i, k); }
    }
    return body.skeleton.bones[best].name;
  };
  const neck = at(bone.neck_01), shoulders = { l: at(bone.upperarm_l), r: at(bone.upperarm_r) };
  const gone = new Array(count).fill(false);
  for (let i = 0; i < count; i++) {
    const v = shape.points[i], j = mainBone(i);
    gone[i] = /^(spine|clavicle)/.test(j) || (j === 'pelvis' && v.y > HEM + 0.04) || (/^thigh/.test(j) && v.y > HEM + 0.14);
    if (/^(neck_01|spine_03|clavicle)/.test(j)) {
      gone[i] = v.y < 0.33 || Math.hypot(v.x - neck.x, (v.z - neck.z) * 0.9) > 0.16;
      if (!gone[i] && v.y < 0.64) {
        // Eases from his own neck into the middle of the collar on the way down.
        const t = Math.min((0.64 - v.y) / 0.22, 1);
        const z = neck.z + (COLLAR.z - neck.z) * t;
        const rx = 0.098 + (COLLAR.rx - 0.098) * t, rz = 0.105 + (COLLAR.rz - 0.105) * t;
        const reach = Math.hypot((v.x - neck.x) / rx, (v.z - z) / rz);
        if (reach > 1) { v.x = neck.x + (v.x - neck.x) / reach; v.z = z + (v.z - z) / reach; }
      }
    }
    const arm = j.match(/^upperarm_(.)/);
    if (arm) {
      const start = shoulders[arm[1]], along = ends[arm[1]].clone().sub(start);
      gone[i] = v.clone().sub(start).dot(along) / along.lengthSq() < 0.74;
    }
    if (v.y < LEGS_END) gone[i] = true;
  }
  const skin = [], trousers = [], index = body.geometry.index.array;
  for (let t = 0; t < index.length; t += 3) {
    const tri = [index[t], index[t + 1], index[t + 2]];
    const marked = tri.filter((i) => gone[i]).length;
    const middle = tri.reduce((sum, i) => sum.add(shape.points[i]), new THREE.Vector3()).divideScalar(3);
    if (marked >= 2 || (marked === 1 && middle.y > 0.1)) continue;
    (middle.y < HEM + 0.2 && Math.abs(middle.x) < 0.3 ? trousers : skin).push(...tri);
  }

  const all = (mesh) => [...mesh.geometry.index.array];
  const out = new THREE.Group();
  out.add(piece('skin', body, shape, skin, true), piece('trousers', body, shape, trousers, false));
  for (const [name, source, keepUv] of [['eyes', parts.Eyes, true], ['brows', parts.Eyebrows, false]]) {
    out.add(piece(name, source, frozen(source), all(source), keepUv));
  }
  for (const hair of hairFiles) man.add(hair.scene);
  man.updateMatrixWorld(true);
  for (const hair of hairFiles) hair.scene.traverse((o) => {
    if (!o.isMesh) return;
    const points = [], normals = [], { position, normal } = o.geometry.attributes;
    const turnNormal = new THREE.Matrix3().getNormalMatrix(o.matrixWorld);
    for (let i = 0; i < position.count; i++) {
      points.push(new THREE.Vector3().fromBufferAttribute(position, i).applyMatrix4(o.matrixWorld));
      normals.push(new THREE.Vector3().fromBufferAttribute(normal, i).applyMatrix3(turnNormal).normalize());
    }
    out.add(piece('hair', o, { points, normals }, all(o), false));
  });

  const glb = await new GLTFExporter().parseAsync(out, { binary: true });
  const picture = async (url, size, quality, filter = 'none') => {
    const image = await new THREE.ImageLoader().loadAsync(url);
    const canvas = document.createElement('canvas');
    canvas.width = canvas.height = size;
    const ctx = canvas.getContext('2d');
    ctx.filter = filter;
    ctx.drawImage(image, 0, 0, size, size);
    return canvas.toDataURL('image/jpeg', quality).split(',')[1];
  };
  const base64 = (buffer) => { let s = ''; new Uint8Array(buffer).forEach((b) => { s += String.fromCharCode(b); }); return btoa(s); };
  if (!fit.pictures) return { [fit.out]: base64(glb) };
  return {
    [fit.out]: base64(glb),
    // Brighter than the pack's, so the lightest skin tone can be reached by tinting down.
    'man-skin.jpg': await picture('/tex/T_Superhero_Male_Ligh.png', 1024, 0.82, 'brightness(1.3)'),
    'man-skin-normal.jpg': await picture('/pack/T_Superhero_Male_Normal.png', 1024, 0.8),
    'man-eye.jpg': await picture('/tex/T_Eye_Brown.png', 256, 0.85),
  };
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const tab = await browser.newPage();
  tab.on('pageerror', (e) => console.log('page error:', String(e)));
  await tab.goto('http://127.0.0.1:4412/bake');
  const files = await tab.evaluate(bake, { choice: shirt, hairstyles });
  for (const [name, data] of Object.entries(files)) {
    const bytes = Buffer.from(data, 'base64');
    await writeFile(join(store, 'public/models', name), bytes);
    console.log(name, bytes.length);
  }
} finally {
  await browser.close();
  server.close();
}
