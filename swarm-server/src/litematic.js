'use strict';

const zlib = require('zlib');
const nbt = require('./nbt');
const cfg = require('./config');

// { size:{x,y,z}, blocks:[{x,y,z,name,properties}] } -> gzip'li .litematic Buffer
//
// Litematica bit-packing: her blok, paletteye index olarak `bits` bit kaplar;
// değerler long dizisinde LSB-first, long sınırlarını AŞARAK (straddling) paketlenir.
// (Pre-1.16 chunk formatıyla aynı; LitematicaBitArray ile bire bir.)

function bitsFor(paletteSize) {
  if (paletteSize <= 1) return 2;
  // ceil(log2(paletteSize)), min 2
  let b = 32 - Math.clz32(paletteSize - 1);
  return Math.max(2, b);
}

function packStates(indices, bits) {
  const totalBits = indices.length * bits;
  const longCount = Math.max(1, Math.ceil(totalBits / 64));
  const arr = new Array(longCount).fill(0n);
  const mask = (1n << BigInt(bits)) - 1n;
  const U64 = (1n << 64n) - 1n;
  for (let i = 0; i < indices.length; i++) {
    const value = BigInt(indices[i]) & mask;
    const startOffset = i * bits;
    const startLong = Math.floor(startOffset / 64);
    const startBit = startOffset % 64;
    arr[startLong] = (arr[startLong] | ((value << BigInt(startBit)) & U64)) & U64;
    const endBit = startBit + bits;
    if (endBit > 64) {
      arr[startLong + 1] =
        (arr[startLong + 1] | (value >> BigInt(64 - startBit))) & U64;
    }
  }
  // signed 64-bit'e çevir (NBT LONG signed)
  return arr.map((x) => BigInt.asIntN(64, x));
}

function stateKey(name, properties) {
  const props = properties || {};
  const keys = Object.keys(props).sort();
  return name + '|' + keys.map((k) => `${k}=${props[k]}`).join(',');
}

function buildPalette(blocks) {
  // index 0 daima air
  const palette = [];
  const keyToIndex = new Map();
  const air = { name: cfg.BLOCKS.AIR, properties: {} };
  keyToIndex.set(stateKey(air.name, air.properties), 0);
  palette.push(air);

  for (const b of blocks) {
    const k = stateKey(b.name, b.properties);
    if (!keyToIndex.has(k)) {
      keyToIndex.set(k, palette.length);
      palette.push({ name: b.name, properties: b.properties || {} });
    }
  }
  return { palette, keyToIndex };
}

function paletteToNbt(palette) {
  return palette.map((entry) => {
    const c = { Name: nbt.string(entry.name) };
    const props = entry.properties || {};
    const propKeys = Object.keys(props);
    if (propKeys.length > 0) {
      const p = {};
      for (const k of propKeys) p[k] = nbt.string(String(props[k]));
      c.Properties = nbt.compound(p);
    }
    return nbt.compound(c);
  });
}

// name: schematic/region adı. metadata alanları opsiyonel.
function build({ size, blocks }, opts = {}) {
  const name = opts.name || 'swarm_smelter';
  const author = opts.author || 'swarm-server';
  const description = opts.description || 'Auto-generated smelter segment';

  const sx = size.x;
  const sy = size.y;
  const sz = size.z;
  const volume = sx * sy * sz;

  const { palette, keyToIndex } = buildPalette(blocks);

  // Tüm hacmi air (0) ile doldur, sonra blokları yerleştir.
  const indices = new Array(volume).fill(0);
  const idx = (x, y, z) => (y * sz + z) * sx + x;
  for (const b of blocks) {
    indices[idx(b.x, b.y, b.z)] = keyToIndex.get(stateKey(b.name, b.properties));
  }

  const bits = bitsFor(palette.length);
  const packed = packStates(indices, bits);

  const now = nbt.long(Date.now());

  const region = nbt.compound({
    Position: nbt.compound({ x: nbt.int(0), y: nbt.int(0), z: nbt.int(0) }),
    Size: nbt.compound({ x: nbt.int(sx), y: nbt.int(sy), z: nbt.int(sz) }),
    BlockStatePalette: nbt.list(nbt.TAG.COMPOUND, paletteToNbt(palette)),
    BlockStates: nbt.longArray(packed),
    TileEntities: nbt.list(nbt.TAG.COMPOUND, []),
    Entities: nbt.list(nbt.TAG.COMPOUND, []),
    PendingBlockTicks: nbt.list(nbt.TAG.COMPOUND, []),
    PendingFluidTicks: nbt.list(nbt.TAG.COMPOUND, []),
  });

  const metadata = nbt.compound({
    Name: nbt.string(name),
    Author: nbt.string(author),
    Description: nbt.string(description),
    RegionCount: nbt.int(1),
    TotalVolume: nbt.int(volume),
    TotalBlocks: nbt.int(blocks.length),
    TimeCreated: now,
    TimeModified: now,
    EnclosingSize: nbt.compound({
      x: nbt.int(sx),
      y: nbt.int(sy),
      z: nbt.int(sz),
    }),
  });

  const root = nbt.compound({
    MinecraftDataVersion: nbt.int(cfg.MC_DATA_VERSION),
    Version: nbt.int(cfg.LITEMATIC_VERSION),
    Metadata: metadata,
    Regions: nbt.compound({ [name]: region }),
  });

  const raw = nbt.writeNbt('', root);
  return zlib.gzipSync(raw);
}

module.exports = { build, bitsFor, packStates };
