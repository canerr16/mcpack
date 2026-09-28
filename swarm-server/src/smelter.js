'use strict';

const cfg = require('./config');

// Çift şeritli süper smelter'ın enine kesitini (genişlik 4, yükseklik 5) verilen
// uzunluk boyunca üretir. Yerel koordinatlar:
//   X: 0..3  (X0 sol yakıt, X1 sol fırın, X2 sağ fırın, X3 sağ yakıt)
//   Y: 0..4  (L0 zemin ... L4 üst taşıyıcı)
//   Z: 0..length-1  (Köşe1/toplayıcı Z=0 ucunda kabul edilir)
//
// Dönen yapı:
//   { size:{x,y,z}, blocks:[{x,y,z,name,properties}], materialList:[{item,count}] }

const H = cfg.BLOCKS.HOPPER;
const S = cfg.BLOCKS.SMOKER;
const F = cfg.BLOCKS.FLOOR;

const T = cfg.TRANSPORT_FACING; // taşıyıcı/çıkış huni yönü (varsayılan north = -Z)

function hopper(facing) {
  return { name: H, properties: { facing, enabled: 'true' } };
}
function smoker(facing) {
  return { name: S, properties: { facing, lit: 'false' } };
}
function floor() {
  return { name: F, properties: {} };
}

// Bir Z dilimindeki sütun tanımları. Her giriş [x, y, blockFactory].
// Simetrik: X0/X3 yakıt sütunları, X1/X2 fırın sütunları.
function sliceColumns() {
  return [
    // X0 — Sol yakıt sütunu
    [0, 3, () => hopper(T)], // YTH  ileri taşıyan yakıt
    [0, 2, () => hopper('east')], // YH  yandan basan (+X -> X1'e)

    // X1 — Sol fırın sütunu
    [1, 4, () => hopper(T)], // İTH  ileri taşıyan hammadde
    [1, 3, () => hopper('down')], // HH  aşağı hammadde
    [1, 2, () => smoker(T)], // SMOKER
    [1, 1, () => hopper(T)], // ÇH  çıkış
    [1, 0, () => floor()], // ZEMİN

    // X2 — Sağ fırın sütunu
    [2, 4, () => hopper(T)],
    [2, 3, () => hopper('down')],
    [2, 2, () => smoker(T)],
    [2, 1, () => hopper(T)],
    [2, 0, () => floor()],

    // X3 — Sağ yakıt sütunu
    [3, 3, () => hopper(T)], // YTH
    [3, 2, () => hopper('west')], // YH  yandan basan (-X -> X2'ye)
  ];
}

function generate(length) {
  if (!Number.isInteger(length) || length < 1) {
    throw new Error('smelter length pozitif tamsayı olmalı: ' + length);
  }
  const cols = sliceColumns();
  const blocks = [];
  const counts = new Map();

  for (let z = 0; z < length; z++) {
    for (const [x, y, make] of cols) {
      const b = make();
      blocks.push({ x, y, z, name: b.name, properties: b.properties });
      counts.set(b.name, (counts.get(b.name) || 0) + 1);
    }
  }

  const materialList = [...counts.entries()]
    .map(([item, count]) => ({ item, count }))
    .sort((a, b) => b.count - a.count);

  return {
    size: { x: 4, y: 5, z: length },
    blocks,
    materialList,
  };
}

module.exports = { generate };
