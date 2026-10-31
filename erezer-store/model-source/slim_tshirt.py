"""
Makes the T-shirt model small enough for phones. Run from this folder:

    python slim_tshirt.py tshirt-original.glb ../public/models/tshirt.glb

Takes "T Shirt" by funlab117 (https://skfb.ly/ozHJJ, CC BY 4.0) and:
  - drops the hidden inner layer of the front and the back (the shirt is drawn
    from both sides instead), keeping the thin edge that joins the layers;
  - names the parts: front, back, sleeveLeft, sleeveRight, collar;
  - centres the shirt, one unit tall, standing up and facing +Z;
  - stores positions and normals compactly in 16 bits (KHR_mesh_quantization;
    8-bit normals showed as stripes across the cloth).
The shirt's shape and its texture coordinates are not changed.
"""
import json
import math
import struct
import sys

src, dst = sys.argv[1], sys.argv[2]
data = open(src, 'rb').read()
json_len = struct.unpack('<I', data[12:16])[0]
gltf = json.loads(data[20:20 + json_len])
binary = data[20 + json_len + 8:]

FORMATS = {5120: 'b', 5121: 'B', 5122: 'h', 5123: 'H', 5125: 'I', 5126: 'f'}
SIZES = {'SCALAR': 1, 'VEC2': 2, 'VEC3': 3, 'VEC4': 4}


def read(index):
    """An accessor as a list of tuples."""
    acc = gltf['accessors'][index]
    view = gltf['bufferViews'][acc['bufferView']]
    fmt = '<' + FORMATS[acc['componentType']] * SIZES[acc['type']]
    item = struct.calcsize(fmt)
    stride = view.get('byteStride') or item
    start = view.get('byteOffset', 0) + acc.get('byteOffset', 0)
    return [struct.unpack_from(fmt, binary, start + i * stride) for i in range(acc['count'])]


meshes = []
for m in gltf['meshes']:
    p = m['primitives'][0]
    a = p['attributes']
    meshes.append({'pos': read(a['POSITION']), 'nor': read(a['NORMAL']), 'uv': read(a['TEXCOORD_0']),
                   'idx': [i[0] for i in read(p['indices'])], 'name': m.get('name')})

# Which part each mesh is, from the name of the node above it.
parent = {}
for i, n in enumerate(gltf['nodes']):
    for c in n.get('children', []):
        parent[c] = i
print({n['mesh']: gltf['nodes'][parent[i]].get('name', '') for i, n in enumerate(gltf['nodes']) if 'mesh' in n})

everything = [p for m in meshes for p in m['pos']]
low = [min(p[k] for p in everything) for k in range(3)]
high = [max(p[k] for p in everything) for k in range(3)]
centre = [(low[k] + high[k]) / 2 for k in range(3)]
scale = 1.0 / (high[1] - low[1])


def mean(i, axis):
    return sum(p[axis] for p in meshes[i]['pos']) / len(meshes[i]['pos'])


def outer_first(a, b, sign):
    """Of two layers of one body panel, the one further out (front: +Z, back: -Z)."""
    return (a, b) if sign * mean(a, 2) >= sign * mean(b, 2) else (b, a)


front_out, front_in = outer_first(2, 3, +1)
back_out, back_in = outer_first(5, 6, -1)
sleeve_left, sleeve_right = (8, 9) if mean(8, 0) < 0 else (9, 8)
parts = [('front', [front_out]), ('back', [back_out]), ('sleeveLeft', [sleeve_left]), ('sleeveRight', [sleeve_right]),
         ('collar', [0, 1]), ('edge', [4, 7])]
print('dropped layers', front_in, back_in)

out = bytearray()
views, accessors, gltf_meshes, nodes = [], [], [], []


def add(raw, target, stride=None):
    while len(out) % 4:
        out.append(0)
    start = len(out)
    out.extend(raw)
    view = {'buffer': 0, 'byteOffset': start, 'byteLength': len(raw), 'target': target}
    if stride:
        view['byteStride'] = stride
    views.append(view)
    return len(views) - 1


for name, ids in parts:
    pos = [p for i in ids for p in meshes[i]['pos']]
    nor = [p for i in ids for p in meshes[i]['nor']]
    uv = [p for i in ids for p in meshes[i]['uv']]
    idx, offset = [], 0
    for i in ids:
        idx += [v + offset for v in meshes[i]['idx']]
        offset += len(meshes[i]['pos'])
    u_low = [round(min(p[k] for p in uv), 3) for k in range(2)]
    u_high = [round(max(p[k] for p in uv), 3) for k in range(2)]
    print(f"{name}: {len(pos)} verts, {len(idx) // 3} tris, uv {u_low}..{u_high}")

    placed = [[(p[k] - centre[k]) * scale for k in range(3)] for p in pos]
    p_low = [min(p[k] for p in placed) for k in range(3)]
    p_high = [max(p[k] for p in placed) for k in range(3)]
    mid = [(p_low[k] + p_high[k]) / 2 for k in range(3)]
    half = [(p_high[k] - p_low[k]) / 2 or 1 for k in range(3)]

    pos_raw = bytearray()
    for p in placed:
        pos_raw += struct.pack('<4h', *[max(-32767, min(32767, round((p[k] - mid[k]) / half[k] * 32767))) for k in range(3)], 0)
    nor_raw = bytearray()
    for n in nor:
        length = math.sqrt(sum(c * c for c in n)) or 1
        nor_raw += struct.pack('<4h', *[max(-32767, min(32767, round(c / length * 32767))) for c in n], 0)
    uv_raw = b''.join(struct.pack('<2f', *t) for t in uv)
    small = len(pos) < 65536
    idx_raw = struct.pack(f"<{len(idx)}{'H' if small else 'I'}", *idx)

    pv = add(pos_raw, 34962, 8)
    nv = add(nor_raw, 34962, 8)
    uvv = add(uv_raw, 34962)
    iv = add(idx_raw, 34963)

    base = len(accessors)
    accessors += [
        {'bufferView': pv, 'componentType': 5122, 'normalized': True, 'count': len(pos), 'type': 'VEC3', 'min': [-32767, -32767, -32767], 'max': [32767, 32767, 32767]},
        {'bufferView': nv, 'componentType': 5122, 'normalized': True, 'count': len(nor), 'type': 'VEC3'},
        {'bufferView': uvv, 'componentType': 5126, 'count': len(uv), 'type': 'VEC2'},
        {'bufferView': iv, 'componentType': 5123 if small else 5125, 'count': len(idx), 'type': 'SCALAR'},
    ]
    gltf_meshes.append({'name': name, 'primitives': [{'attributes': {'POSITION': base, 'NORMAL': base + 1, 'TEXCOORD_0': base + 2}, 'indices': base + 3, 'material': 0}]})
    nodes.append({'name': name, 'mesh': len(gltf_meshes) - 1, 'translation': mid, 'scale': half})

result = {
    'asset': {'version': '2.0', 'generator': 'slim_tshirt.py', 'extras': gltf['asset'].get('extras')},
    'extensionsUsed': ['KHR_mesh_quantization'], 'extensionsRequired': ['KHR_mesh_quantization'],
    'scene': 0, 'scenes': [{'nodes': list(range(len(nodes)))}], 'nodes': nodes, 'meshes': gltf_meshes,
    'materials': [{'name': 'cloth', 'doubleSided': True, 'pbrMetallicRoughness': {'metallicFactor': 0, 'roughnessFactor': 0.9}}],
    'accessors': accessors, 'bufferViews': views, 'buffers': [{'byteLength': len(out)}],
}
while len(out) % 4:
    out.append(0)
result['buffers'][0]['byteLength'] = len(out)
text = json.dumps(result, separators=(',', ':')).encode()
text += b' ' * (-len(text) % 4)
with open(dst, 'wb') as f:
    f.write(struct.pack('<4sII', b'glTF', 2, 12 + 8 + len(text) + 8 + len(out)))
    f.write(struct.pack('<I4s', len(text), b'JSON') + text)
    f.write(struct.pack('<I4s', len(out), b'BIN\0') + bytes(out))
print('written', dst, 12 + 8 + len(text) + 8 + len(out), 'bytes')
