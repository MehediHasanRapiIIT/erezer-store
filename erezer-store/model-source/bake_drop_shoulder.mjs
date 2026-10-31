/**
 * Makes public/models/tshirt-drop.glb and its pictures: the drop shoulder T-shirt.
 *
 *   node model-source/bake_drop_shoulder.mjs "C:/path/to/oversized_t-shirt"
 *
 * The source is "oversized_t-shirt" by tech.service.allwe (CC BY 4.0, see the credit
 * on the Custom Design page), downloaded from Sketchfab as glTF. It isn't kept here:
 * it's 42 MB. This makes it light enough for a phone and gives it the same parts as
 * tshirt.glb, so the shop treats both shirts alike:
 *  - the file's four meshes are arbitrary chunks, so the real sewn pieces are found
 *    from its flat pattern, and named front, back, sleeveLeft, sleeveRight, collar, edge,
 *  - triangles are reduced piece by piece with meshoptimizer, holding each piece's
 *    edges still so no gap opens at a seam (the normal map keeps the folds). The cloth
 *    has an outside and an inside layer about 2 mm apart: both stay, or light shows
 *    through the seams of a dark shirt,
 *  - the first set of UVs becomes the flat pattern in the same units as tshirt.glb
 *    (u right, v down, nothing mirrored), which is where a design goes. The second set
 *    is the file's own layout, which the cloth pictures use.
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
if (!source) throw new Error('Give the folder of the downloaded oversized_t-shirt.');
const { chromium } = await import(process.env.PLAYWRIGHT ?? pathToFileURL('D:/erezer-new/node_modules/playwright-core/index.mjs').href);

const roots = [['/three/', join(store, 'node_modules/three/')], ['/meshopt/', join(store, 'node_modules/meshoptimizer/')], ['/shirt/', source + '/']];
const types = { '.js': 'text/javascript', '.mjs': 'text/javascript', '.png': 'image/png', '.gltf': 'application/json' };
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
}).listen(4413, '127.0.0.1');

async function bake() {
  const THREE = await import('three');
  const { GLTFLoader } = await import('three/addons/loaders/GLTFLoader.js');
  const { GLTFExporter } = await import('three/addons/exporters/GLTFExporter.js');
  const { MeshoptSimplifier } = await import('/meshopt/meshopt_simplifier.js');
  /** Same height on screen as tshirt.glb. */
  const SCALE = 1.4;
  /** Pattern units for one unit of the file's layout: the front panel is 512 wide, as on tshirt.glb. */
  const UNITS = 512 / 0.443;
  /** Each piece's place in the file's layout, and how many triangles it keeps. */
  const PANELS = {
    front: { u: 0.2895, v: 0.363, keep: 28000 },
    back: { u: 0.7395, v: 0.363, keep: 28000 },
    sleeveLeft: { u: 0.2895, v: 0.872, keep: 7000 },
    sleeveRight: { u: 0.71, v: 0.872, keep: 7000 },
    collar: { u: 0.5, v: 0.69, keep: 2500 },
    edge: { u: 0.5, v: 0.05, keep: 9000 },
  };
  const pieceOf = (u, v) => {
    if (v < 0.07 || (v > 0.74 && v < 0.78)) return 'edge';
    if (v < 0.645) return u < 0.515 ? 'front' : 'back';
    if (v < 0.71) return 'collar';
    return u < 0.5 ? 'sleeveLeft' : 'sleeveRight';
  };

  const gltf = await new GLTFLoader().loadAsync('/shirt/scene.gltf');
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
      const key = `${v.x.toFixed(4)},${v.y.toFixed(4)},${v.z.toFixed(4)},${uv.getX(i).toFixed(5)},${uv.getY(i).toFixed(5)}`;
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
  const count = U.length / 2;

  // Which piece each vertex is in. Each piece is simplified alone with its edges held
  // still, so the seams between pieces stay exactly where they were.
  const piece = new Array(count);
  for (let i = 0; i < count; i++) piece[i] = pieceOf(U[i * 2], U[i * 2 + 1]);
  await MeshoptSimplifier.ready;
  const out = new THREE.Group(), counts = {};
  for (const [name, panel] of Object.entries(PANELS)) {
    const wanted = [];
    for (let t = 0; t < tris.length; t += 3) if (piece[tris[t]] === name) wanted.push(tris[t], tris[t + 1], tris[t + 2]);
    const [index] = MeshoptSimplifier.simplify(new Uint32Array(wanted), new Float32Array(P), 3, panel.keep * 3, 0.01, ['LockBorder']);
    const local = new Map(), pos = [], nor = [], uv = [], layout = [], small = [];
    for (const i of index) {
      let c = local.get(i);
      if (c === undefined) {
        c = local.size;
        local.set(i, c);
        pos.push(P[i * 3], P[i * 3 + 1], P[i * 3 + 2]);
        nor.push(N[i * 3], N[i * 3 + 1], N[i * 3 + 2]);
        layout.push(U[i * 2], U[i * 2 + 1]);
        uv.push((U[i * 2] - panel.u) * UNITS, (panel.v - U[i * 2 + 1]) * UNITS);
      }
      small.push(c);
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(pos, 3));
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(nor, 3));
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uv, 2));
    geometry.setAttribute('uv1', new THREE.Float32BufferAttribute(layout, 2));
    geometry.setIndex(small);
    const mesh = new THREE.Mesh(geometry, new THREE.MeshBasicMaterial());
    mesh.name = name;
    out.add(mesh);
    counts[name] = { from: wanted.length / 3, triangles: small.length / 3, vertices: pos.length / 3 };
  }

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
  return {
    counts,
    files: {
      'tshirt-drop.glb': base64(glb),
      // Brighter than the file's, or a white shirt comes out grey.
      'tshirt-drop-cloth.jpg': await picture('/shirt/textures/Material.001_baseColor.png', 2048, 0.8, 'brightness(1.28)'),
      'tshirt-drop-normal.jpg': await picture('/shirt/textures/Material.001_normal.png', 1536, 0.78),
    },
  };
}

const browser = await chromium.launch({ channel: 'chrome', headless: true });
try {
  const tab = await browser.newPage();
  tab.on('pageerror', (e) => console.log('page error:', String(e)));
  await tab.goto('http://127.0.0.1:4413/bake');
  const { counts, files } = await tab.evaluate(bake);
  console.log(JSON.stringify(counts));
  for (const [name, data] of Object.entries(files)) {
    const bytes = Buffer.from(data, 'base64');
    await writeFile(join(store, 'public/models', name), bytes);
    console.log(name, bytes.length);
  }
} finally {
  await browser.close();
  server.close();
}
