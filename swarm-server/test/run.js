'use strict';

// Bağımlılıksız temel doğrulama testleri (assert). `npm test` ile çalışır.
const assert = require('assert');
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

const smelter = require('../src/smelter');
const litematic = require('../src/litematic');
const geometry = require('../src/geometry');
const { Orchestrator } = require('../src/orchestrator');

let passed = 0;
function test(name, fn) {
  fn();
  passed++;
  console.log('  ok -', name);
}

console.log('smelter geometrisi:');
test('kesit blok sayısı doğru (14/dilim)', () => {
  const g = smelter.generate(1);
  assert.strictEqual(g.size.x, 4);
  assert.strictEqual(g.size.y, 5);
  assert.strictEqual(g.size.z, 1);
  assert.strictEqual(g.blocks.length, 14); // 2+5+5+2
});

test('uzunluk ölçekleniyor', () => {
  const g = smelter.generate(10);
  assert.strictEqual(g.blocks.length, 140);
  const total = g.materialList.reduce((a, b) => a + b.count, 0);
  assert.strictEqual(total, 140);
});

test('smoker ve floor sayıları', () => {
  const g = smelter.generate(5);
  const map = Object.fromEntries(g.materialList.map((m) => [m.item, m.count]));
  assert.strictEqual(map['minecraft:smoker'], 10); // 2/dilim * 5
  assert.strictEqual(map['minecraft:smooth_stone'], 10); // zemin 2/dilim * 5
  assert.strictEqual(map['minecraft:hopper'], 50); // 10/dilim * 5
});

console.log('litematic yazımı:');
test('bitsFor doğru', () => {
  assert.strictEqual(litematic.bitsFor(1), 2);
  assert.strictEqual(litematic.bitsFor(2), 2);
  assert.strictEqual(litematic.bitsFor(4), 2);
  assert.strictEqual(litematic.bitsFor(5), 3);
  assert.strictEqual(litematic.bitsFor(8), 3);
  assert.strictEqual(litematic.bitsFor(9), 4);
});

test('packStates round-trip (straddle)', () => {
  const bits = 3;
  const vals = [0, 1, 2, 3, 4, 5, 6, 7, 1, 2, 3];
  const packed = litematic.packStates(vals, bits);
  // geri oku
  const U64 = (1n << 64n) - 1n;
  const mask = (1n << BigInt(bits)) - 1n;
  const read = [];
  for (let i = 0; i < vals.length; i++) {
    const off = i * bits;
    const sl = Math.floor(off / 64);
    const sb = off % 64;
    let v = (BigInt.asUintN(64, packed[sl]) >> BigInt(sb)) & mask;
    if (sb + bits > 64) {
      const low = BigInt.asUintN(64, packed[sl + 1]);
      v = (v | (low << BigInt(64 - sb))) & mask;
    }
    read.push(Number(v));
  }
  assert.deepStrictEqual(read, vals);
});

test('.litematic gzip üretiliyor ve okunabilir NBT başlığı taşıyor', () => {
  const g = smelter.generate(8);
  const buf = litematic.build(g, { name: 'test_seg' });
  assert.ok(buf.length > 0);
  const raw = zlib.gunzipSync(buf);
  // NBT kökü: 0x0A (compound) + boş isim (00 00)
  assert.strictEqual(raw[0], 0x0a);
  assert.strictEqual(raw[1], 0x00);
  assert.strictEqual(raw[2], 0x00);
  // örnek çıktı dosyası yaz (elle Litematica'da açıp doğrulanabilir)
  const outDir = path.join(__dirname, 'out');
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(path.join(outDir, 'test_seg.litematic'), buf);
});

console.log('alan bölme:');
test('Z ekseni 4 segmente bölünür (+gap)', () => {
  const plan = geometry.splitSelection(
    { x: 0, y: 64, z: 0 },
    { x: 3, y: 68, z: 199 },
    { workers: 4, gap: 1 }
  );
  assert.strictEqual(plan.lengthAxis, 'z');
  assert.strictEqual(plan.totalLength, 200);
  assert.strictEqual(plan.segments.length, 4);
  // usable = 200 - 3 = 197; 197/4 = 49 kalan 1 -> [50,49,49,49]
  const lens = plan.segments.map((s) => s.length);
  assert.deepStrictEqual(lens, [50, 49, 49, 49]);
  assert.strictEqual(lens.reduce((a, b) => a + b, 0), 197);
});

test('köşe1 max uçtaysa yön ters (buildDirSign=-1)', () => {
  const plan = geometry.splitSelection(
    { x: 0, y: 64, z: 199 },
    { x: 3, y: 68, z: 0 },
    { workers: 4, gap: 1 }
  );
  assert.strictEqual(plan.collectionAtMin, false);
  assert.strictEqual(plan.segments[0].buildDirSign, -1);
  assert.strictEqual(plan.rotationY, 180);
});

test('çok kısa alan hata verir', () => {
  assert.throws(() =>
    geometry.splitSelection(
      { x: 0, y: 64, z: 0 },
      { x: 3, y: 68, z: 2 },
      { workers: 4, gap: 1 }
    )
  );
});

console.log('orkestratör:');
test('start -> 4 worker task:assign alır', () => {
  const sent = [];
  const orch = new Orchestrator({
    workerCount: 4,
    emit: (id, type, payload) => sent.push({ id, type, payload }),
    emitMaster: () => {},
  });
  for (let i = 0; i < 4; i++) orch.registerWorker(String(i), 'bot' + i);
  orch.startJob({
    corner1: { x: 0, y: 64, z: 0 },
    corner2: { x: 3, y: 68, z: 199 },
    structure: 'smelter',
  });
  const assigns = sent.filter((s) => s.type === 'task:assign');
  assert.strictEqual(assigns.length, 4);
  assert.ok(assigns[0].payload.litematic.length > 0);
  assert.ok(assigns[0].payload.materialList.length > 0);
});

test('disconnect progress korur, reconnect resume eder', () => {
  const sent = [];
  const orch = new Orchestrator({
    workerCount: 1,
    emit: (id, type, payload) => sent.push({ id, type, payload }),
    emitMaster: () => {},
  });
  orch.registerWorker('w1', 'bot1');
  orch.startJob({
    corner1: { x: 0, y: 64, z: 0 },
    corner2: { x: 3, y: 68, z: 20 },
    structure: 'smelter',
  });
  orch.onProgress('w1', { placedZ: 7, placedCount: 98 });
  orch.markDisconnected('w1');
  assert.strictEqual(orch.workers.get('w1').state, 'disconnected');
  assert.strictEqual(orch.workers.get('w1').progress.placedZ, 7);
  orch.registerWorker('w1', 'bot1');
  const resume = sent.filter((s) => s.type === 'task:resume');
  assert.strictEqual(resume.length, 1);
  assert.strictEqual(resume[0].payload.fromZ, 7);
});

test('need_material -> master bilgilendirilir', () => {
  const masterMsgs = [];
  const orch = new Orchestrator({
    workerCount: 1,
    emit: () => {},
    emitMaster: (type, payload) => masterMsgs.push({ type, payload }),
  });
  orch.registerWorker('w1', 'bot1');
  orch.startJob({
    corner1: { x: 0, y: 64, z: 0 },
    corner2: { x: 3, y: 68, z: 20 },
    structure: 'smelter',
  });
  orch.onNeedMaterial('w1', { item: 'minecraft:hopper', count: 64 });
  const nm = masterMsgs.filter((m) => m.type === 'swarm:need_material');
  assert.strictEqual(nm.length, 1);
  assert.strictEqual(nm[0].payload.item, 'minecraft:hopper');
  assert.strictEqual(orch.workers.get('w1').state, 'waiting_material');
});

console.log(`\n${passed} test geçti.`);
