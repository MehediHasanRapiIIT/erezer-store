/**
 * Makes public/models/hoodie.glb: the drop shoulder hoodie.
 *
 *   node model-source/bake_hoodie.mjs "C:/path/to/hoodie"
 *
 * The source is "Hoodie" by yogaminggames (ShoyoX on Sketchfab, CC BY 4.0, see the
 * credit on the Custom Design page), made in Marvelous Designer and downloaded from
 * Sketchfab as glTF. It isn't kept here: it's 14 MB. This makes it light enough for a
 * phone and gives it parts the shop knows:
 *  - the file's four meshes are arbitrary chunks, so the sewn pieces are found by what
 *    joins to what: front, back, sleeveLeft, sleeveRight, hood and edge (cuffs and
 *    waistband). Body and sleeves have an outside and an inside layer: both stay, or
 *    light shows through the seams of a dark hoodie. The outside front and back come
 *    joined at the side seams and are cut apart there,
 *  - the pocket joins the front, with the front's pattern carried onto it from the
 *    cloth under it, so a design runs across the pocket as it would be printed,
 *  - each piece's UVs are its own flat pattern piece in metres. They become pattern
 *    units like tshirt.glb's (u right seen from outside, v down, nothing mirrored,
 *    centred on 0): the file's back and wearer's right sleeve are mirrored, so those flip,
 *  - every triangle faces out, which the print layer (outside only) relies on,
 *  - triangles are reduced piece by piece with meshoptimizer, holding each piece's
 *    edges still so no gap opens at a seam. Marvelous Designer makes very fine, even
 *    triangles, so the folds survive.
 *
 * Uses meshoptimizer from node_modules, which comes with @types/three; the shop itself doesn't load it.
 * Set PLAYWRIGHT to the playwright-core index.mjs to use, if it's not the default.
 */
import { createServer } from 'node:http';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, extname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const store = join(here, '..');
const source = process.argv[2];
if (!source) throw new Error('Give the folder of the downloaded hoodie.');
const { chromium } = await import(process.env.PLAYWRIGHT ?? pathToFileURL('D:/erezer-new/node_modules/playwright-core/index.mjs').href);

const roots = [['/three/', join(store, 'node_modules/three/')], ['/meshopt/', join(store, 'node_modules/meshoptimizer/')], ['/hoodie/', source + '/']];
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.gltf': 'application/json' };
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
}).listen(4414, '127.0.0.1');

async function bake() {
  const THREE = await import('three');
  const { GLTFLoader } = await import('three/addons/loaders/GLTFLoader.js');
  const { GLTFExporter } = await import('three/addons/exporters/GLTFExporter.js');
  const { MeshoptSimplifier } = await import('/meshopt/meshopt_simplifier.js');
  /** Its size in the viewer, next to the T-shirts. */
  const SCALE = 1.15;
  /** Pattern units for one metre of pattern: the front panel is 512 wide, as on tshirt.glb. */
  const UNITS = 512 / 0.536;
  /** How many triangles each part keeps (both layers together). */
  const KEEP = { front: 30000, back: 26000, sleeveLeft: 12000, sleeveRight: 12000, hood: 20000, edge: 6000 };

  const gltf = await new GLTFLoader().loadAsync('/hoodie/scene.gltf');
  gltf.scene.updateMatrixWorld(true);
  const box = new THREE.Box3().setFromObject(gltf.scene);
  const middle = box.getCenter(new THREE.Vector3());

  // Every vertex once: the file repeats those on the edges of its chunks.
  const seen = new Map(), P = [], N = [], U = [], tris = [];
  const v = new THREE.Vector3(), n = new THREE.Vector3();
  gltf.scene.traverse((m) => {
    if (!m.isMesh) return;
    const { position, normal, uv } = m.geometry.attributes;
    const turn = new THREE.Matrix3().getNormalMatrix(m.matrixWorld);
    const id = new Int32Array(position.count);
    for (let i = 0; i < position.count; i++) {
      v.fromBufferAttribute(position, i).applyMatrix4(m.matrixWorld).sub(middle).multiplyScalar(SCALE);
      const key = `${v.x.toFixed(5)},${v.y.toFixed(5)},${v.z.toFixed(5)},${uv.getX(i).toFixed(5)},${uv.getY(i).toFixed(5)}`;
      let at = seen.get(key);
      if (at === undefined) {
        at = U.length / 2;
        seen.set(key, at);
        n.fromBufferAttribute(normal, i).applyMatrix3(turn).normalize();
        P.push(v.x, v.y, v.z);
        N.push(n.x, n.y, n.z);
        U.push(uv.getX(i), uv.getY(i));
      }
      id[i] = at;
    }
    const index = m.geometry.index.array;
    for (let t = 0; t < index.length; t++) tris.push(id[index[t]]);
  });
  const count = U.length / 2, triCount = tris.length / 3;

  // The pieces: triangles that share an edge. Each piece's triangles are first turned
  // to agree with their neighbours (the file mixes both ways round).
  const sides = new Map();
  for (let t = 0; t < triCount; t++) {
    for (let k = 0; k < 3; k++) {
      const a = tris[t * 3 + k], b = tris[t * 3 + ((k + 1) % 3)];
      const key = a < b ? `${a}_${b}` : `${b}_${a}`;
      if (!sides.has(key)) sides.set(key, []);
      sides.get(key).push(t);
    }
  }
  const hasEdge = (t, a, b) => [0, 1, 2].some((k) => tris[t * 3 + k] === a && tris[t * 3 + ((k + 1) % 3)] === b);
  const piece = new Int32Array(triCount).fill(-1);
  const groups = [];
  for (let first = 0; first < triCount; first++) {
    if (piece[first] >= 0) continue;
    const list = [first], g = groups.length;
    piece[first] = g;
    for (let q = 0; q < list.length; q++) {
      const t = list[q];
      for (let k = 0; k < 3; k++) {
        const a = tris[t * 3 + k], b = tris[t * 3 + ((k + 1) % 3)];
        const near = sides.get(a < b ? `${a}_${b}` : `${b}_${a}`);
        if (near.length !== 2) continue;
        const o = near[0] === t ? near[1] : near[0];
        if (piece[o] >= 0) continue;
        // Agreeing neighbours run a shared edge in opposite directions.
        if (hasEdge(o, a, b)) { const x = tris[o * 3 + 1]; tris[o * 3 + 1] = tris[o * 3 + 2]; tris[o * 3 + 2] = x; }
        piece[o] = g;
        list.push(o);
      }
    }
    groups.push(list);
  }
  // Name each piece by where it is: x across (wearer's right is -x), y up, z to the front.
  const named = { front: [], back: [], sleeveLeft: [], sleeveRight: [], hood: [], edge: [], pocket: [] };
  const report = [], body = [], inner = [];
  for (const list of groups) {
    if (list.length < 50) continue;
    let cx = 0, cy = 0, cz = 0, umin = Infinity, umax = -Infinity, vmin = Infinity, vmax = -Infinity;
    for (const t of list) for (let k = 0; k < 3; k++) {
      const i = tris[t * 3 + k];
      cx += P[i * 3]; cy += P[i * 3 + 1]; cz += P[i * 3 + 2];
      umin = Math.min(umin, U[i * 2]); umax = Math.max(umax, U[i * 2]);
      vmin = Math.min(vmin, U[i * 2 + 1]); vmax = Math.max(vmax, U[i * 2 + 1]);
    }
    const c = list.length * 3;
    cx /= c; cy /= c; cz /= c;
    const w = umax - umin, h = vmax - vmin;
    let name;
    if (w > 0.6) name = 'hood';
    else if (h < 0.3 && Math.abs(cx) > 0.2 * SCALE) name = 'edge';           // cuffs
    else if (h < 0.15) name = 'edge';                                       // waistband
    else if (Math.abs(cx) > 0.2 * SCALE) name = cx < 0 ? 'sleeveLeft' : 'sleeveRight';
    else if (h < 0.3) name = 'pocket';
    else if (Math.abs(cz) < 0.05) name = 'body';                            // the outside front and back, joined
    else name = cz > 0 ? 'front' : 'back';
    if (name === 'body') body.push(list);
    else {
      named[name].push(...list);
      if (name === 'front' || name === 'back') inner.push({ name, list });
    }
    report.push(`${name} ${list.length} at ${cx.toFixed(2)},${cy.toFixed(2)},${cz.toFixed(2)} uv ${w.toFixed(3)}x${h.toFixed(3)}`);
  }

  // Every piece faces out (the print layer shows on the outside only): turn the whole
  // piece round when most of it faces in. Out is away from the body's middle, a sleeve's
  // middle line, or the head inside the hood.
  const outward = (name, x, y, z) => {
    if (name === 'sleeveLeft' || name === 'sleeveRight') {
      const side = name === 'sleeveLeft' ? -1 : 1;
      const along = new THREE.Vector3(side * 0.82, -0.57, 0).normalize();
      const p = new THREE.Vector3(x - side * 0.25 * SCALE, y - 0.1 * SCALE, z);
      return p.sub(along.multiplyScalar(p.dot(along)));
    }
    if (name === 'hood') return new THREE.Vector3(x, y - 0.25 * SCALE, z + 0.05 * SCALE);
    // A cuff faces out like the end of its sleeve.
    if (name === 'edge' && Math.abs(x) > 0.4 * SCALE) return outward(x < 0 ? 'sleeveLeft' : 'sleeveRight', x, y, z);
    return new THREE.Vector3(x, 0, z);
  };
  const ta = new THREE.Vector3(), tb = new THREE.Vector3(), tc = new THREE.Vector3(), face = new THREE.Vector3();
  const facesIn = (name, t) => {
    const [i, j, k] = [tris[t * 3], tris[t * 3 + 1], tris[t * 3 + 2]];
    ta.set(P[i * 3], P[i * 3 + 1], P[i * 3 + 2]); tb.set(P[j * 3], P[j * 3 + 1], P[j * 3 + 2]); tc.set(P[k * 3], P[k * 3 + 1], P[k * 3 + 2]);
    face.subVectors(tb, ta).cross(tc.clone().sub(ta));
    const m = ta.clone().add(tb).add(tc).divideScalar(3);
    return face.dot(outward(name, m.x, m.y, m.z)) < 0;
  };
  const turned = {};
  const turnRound = (name, list) => {
    const inward = list.filter((t) => facesIn(name, t)).length;
    turned[name] = (turned[name] ?? '') + `${inward}/${list.length} `;
    if (inward * 2 <= list.length) return;
    for (const t of list) { const x = tris[t * 3 + 1]; tris[t * 3 + 1] = tris[t * 3 + 2]; tris[t * 3 + 2] = x; }
  };
  for (const list of groups) {
    if (list.length < 50) continue;
    const t0 = list[0];
    const name = Object.keys(named).find((nm) => named[nm].includes(t0)) ?? 'body';
    turnRound(name === 'pocket' ? 'front' : name, list);
  }

  // The outside body: each triangle goes with the inside piece just under it.
  const cell = 0.02, grid = new Map();
  const cellOf = (x, y, z) => [Math.floor(x / cell), Math.floor(y / cell), Math.floor(z / cell)];
  for (const { name, list } of inner) for (const t of list) {
    const i = tris[t * 3];
    const key = cellOf(P[i * 3], P[i * 3 + 1], P[i * 3 + 2]).join(',');
    if (!grid.has(key)) grid.set(key, []);
    grid.get(key).push([i, name]);
  }
  const nearest = (x, y, z, accept = () => true) => {
    const [gx, gy, gz] = cellOf(x, y, z);
    let best = null, gap = Infinity;
    for (let reach = 1; reach <= 4 && !best; reach++) {
      for (let dx = -reach; dx <= reach; dx++) for (let dy = -reach; dy <= reach; dy++) for (let dz = -reach; dz <= reach; dz++) {
        for (const item of grid.get(`${gx + dx},${gy + dy},${gz + dz}`) ?? []) {
          const j = item[0];
          const d = (P[j * 3] - x) ** 2 + (P[j * 3 + 1] - y) ** 2 + (P[j * 3 + 2] - z) ** 2;
          if (d < gap && accept(item)) { gap = d; best = item; }
        }
      }
    }
    return best;
  };
  for (const list of body) for (const t of list) {
    const [i, j, k] = [tris[t * 3], tris[t * 3 + 1], tris[t * 3 + 2]];
    const x = (P[i * 3] + P[j * 3] + P[k * 3]) / 3, y = (P[i * 3 + 1] + P[j * 3 + 1] + P[k * 3 + 1]) / 3, z = (P[i * 3 + 2] + P[j * 3 + 2] + P[k * 3 + 2]) / 3;
    named[nearest(x, y, z)?.[1] ?? (z > 0 ? 'front' : 'back')].push(t);
  }


  // Pattern units for a piece's own UVs: centred, metres to units, v down, mirrored ones flipped.
  const centre = { front: [0, 0.043], back: [0, 0.043], sleeveLeft: [0.0125, -0.018], sleeveRight: [0.0125, -0.018] };
  const flip = { front: 1, back: -1, sleeveLeft: -1, sleeveRight: 1 };
  const pattern = new Float32Array(count * 2);
  for (const name of Object.keys(centre)) {
    for (const t of named[name]) for (let k = 0; k < 3; k++) {
      const i = tris[t * 3 + k];
      pattern[i * 2] = flip[name] * (U[i * 2] - centre[name][0]) * UNITS;
      pattern[i * 2 + 1] = -(U[i * 2 + 1] - centre[name][1]) * UNITS;
    }
  }
  // The pocket takes the front's pattern from the cloth just behind it, blended over a
  // few centimetres so lines run smoothly across it. (Both layers of the front carry
  // the same pattern, so either will do.)
  const frontVerts = [...new Set(named.front.flatMap((t) => [tris[t * 3], tris[t * 3 + 1], tris[t * 3 + 2]]))];
  const fine = 0.01, frontGrid = new Map();
  const cellKey = (x, y) => `${Math.floor(x / fine)},${Math.floor(y / fine)}`;
  for (const i of frontVerts) {
    const key = cellKey(P[i * 3], P[i * 3 + 1]);
    if (!frontGrid.has(key)) frontGrid.set(key, []);
    frontGrid.get(key).push(i);
  }
  const pocketVerts = [...new Set(named.pocket.flatMap((t) => [tris[t * 3], tris[t * 3 + 1], tris[t * 3 + 2]]))];
  for (const i of pocketVerts) {
    const x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
    let su = 0, sv = 0, sw = 0;
    for (let dx = -3; dx <= 3; dx++) for (let dy = -3; dy <= 3; dy++) {
      for (const j of frontGrid.get(`${Math.floor(x / fine) + dx},${Math.floor(y / fine) + dy}`) ?? []) {
        // The front facing the same way as the pocket, behind it (not the back's inside).
        if (P[j * 3 + 2] < z - 0.06) continue;
        const d2 = (P[j * 3] - x) ** 2 + (P[j * 3 + 1] - y) ** 2;
        if (d2 > 0.03 ** 2) continue;
        const w = 1 / (d2 + 1e-6);
        su += pattern[j * 2] * w; sv += pattern[j * 2 + 1] * w; sw += w;
      }
    }
    if (sw > 0) { pattern[i * 2] = su / sw; pattern[i * 2 + 1] = sv / sw; }
  }
  named.front.push(...named.pocket);
  delete named.pocket;

  await MeshoptSimplifier.ready;
  const out = new THREE.Group(), counts = {};
  for (const [name, list] of Object.entries(named)) {
    const wanted = new Uint32Array(list.length * 3);
    list.forEach((t, x) => { wanted[x * 3] = tris[t * 3]; wanted[x * 3 + 1] = tris[t * 3 + 1]; wanted[x * 3 + 2] = tris[t * 3 + 2]; });
    const [index] = MeshoptSimplifier.simplify(wanted, new Float32Array(P), 3, KEEP[name] * 3, 0.01, ['LockBorder']);
    const local = new Map(), pos = [], nor = [], uv = [], small = [];
    for (const i of index) {
      let c = local.get(i);
      if (c === undefined) {
        c = local.size;
        local.set(i, c);
        pos.push(P[i * 3], P[i * 3 + 1], P[i * 3 + 2]);
        nor.push(N[i * 3], N[i * 3 + 1], N[i * 3 + 2]);
        uv.push(pattern[i * 2], pattern[i * 2 + 1]);
      }
      small.push(c);
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    geometry.setIndex(new THREE.BufferAttribute(pos.length / 3 < 65536 ? new Uint16Array(small) : new Uint32Array(small), 1));
    // The file's normals point either way; light them from the side each triangle faces.
    geometry.computeVertexNormals();
    const mesh = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial());
    mesh.name = name;
    out.add(mesh);
    counts[name] = { from: list.length, triangles: small.length / 3, vertices: pos.length / 3, turned: turned[name] };
  }

  const size = new THREE.Box3().setFromObject(out).getSize(new THREE.Vector3());
  const glb = await new GLTFExporter().parseAsync(out, { binary: true });
  let s = '';
  new Uint8Array(glb).forEach((x) => { s += String.fromCharCode(x); });
  return { counts, report, size: size.toArray().map((x) => x.toFixed(3)), glb: btoa(s) };
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const tab = await browser.newPage();
  tab.on('pageerror', (e) => console.log('page error:', String(e)));
  await tab.goto('http://127.0.0.1:4414/bake');
  const { counts, report, size, glb } = await tab.evaluate(bake);
  console.log(report.join('\n'));
  console.log(JSON.stringify(counts));
  console.log('size', size.join(' x '));
  const bytes = Buffer.from(glb, 'base64');
  await writeFile(join(store, 'public/models/hoodie.glb'), bytes);
  console.log('hoodie.glb', bytes.length);
} finally {
  await browser.close();
  server.close();
}
