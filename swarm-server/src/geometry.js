'use strict';

const cfg = require('./config');

// Master'ın seçtiği iki köşeyi alır, uzun yatay eksen boyunca WORKER_COUNT eşit
// segmente böler. Köşe1 = toplayıcı (hattın başı) kabul edilir; çıkış hunileri o
// uca doğru zincirlenir.
//
// Dönen: { totalLength, width, lengthAxis, segments:[ {
//   segmentId, origin:{x,y,z}, length, lengthAxis, buildDirSign, rotationY,
//   worldStart, worldEnd
// } ] }
//
// NOT (rotationY): Litematica yerleştirmesinde şemayı dünyaya hizalamak için
// worker mod'un uygulayacağı Y-rotasyonu. Yerel -Z (kuzey/çıkış yüzü) Köşe1'e
// bakacak şekilde seçilir. Kesin kalibrasyon oyun-içi testte (Modül 3) doğrulanır.

function abs(n) {
  return n < 0 ? -n : n;
}

function splitSelection(corner1, corner2, opts = {}) {
  const workers = opts.workers || cfg.WORKER_COUNT;
  const gap = opts.gap != null ? opts.gap : cfg.SAFETY_GAP;

  const minX = Math.min(corner1.x, corner2.x);
  const minY = Math.min(corner1.y, corner2.y);
  const minZ = Math.min(corner1.z, corner2.z);
  const maxX = Math.max(corner1.x, corner2.x);
  const maxZ = Math.max(corner1.z, corner2.z);

  const spanX = maxX - minX + 1;
  const spanZ = maxZ - minZ + 1;

  const lengthAxis = spanZ >= spanX ? 'z' : 'x';
  const totalLength = lengthAxis === 'z' ? spanZ : spanX;
  const width = lengthAxis === 'z' ? spanX : spanZ;

  // Köşe1'in uzunluk ekseni üzerindeki konumu min uçta mı?
  const c1coord = lengthAxis === 'z' ? corner1.z : corner1.x;
  const c2coord = lengthAxis === 'z' ? corner2.z : corner2.x;
  const collectionAtMin = c1coord <= c2coord;
  // Segmentler Köşe1'den Köşe2'ye doğru ilerler.
  const buildDirSign = collectionAtMin ? 1 : -1;

  // rotationY: yerel -Z (çıkış) -> Köşe1'e doğru.
  let rotationY;
  if (lengthAxis === 'z') {
    rotationY = collectionAtMin ? 0 : 180;
  } else {
    // length X boyunca: çıkış +X ya da -X'e bakmalı
    rotationY = collectionAtMin ? 90 : 270;
  }

  // Kullanılabilir uzunluğu boşluklarla böl.
  const totalGap = gap * (workers - 1);
  const usable = totalLength - totalGap;
  if (usable < workers) {
    throw new Error(
      `Seçilen alan çok kısa: uzunluk=${totalLength}, worker=${workers}, gap=${gap}`
    );
  }
  const base = Math.floor(usable / workers);
  let remainder = usable % workers;

  const segments = [];
  // Uzunluk ekseninde Köşe1 ucundan başlayan yerel ofset (blok).
  let cursor = 0;
  for (let i = 0; i < workers; i++) {
    const segLen = base + (remainder > 0 ? 1 : 0);
    if (remainder > 0) remainder--;

    // Bu segmentin uzunluk-ekseni başlangıcı (Köşe1 ucuna göre yerel ofset).
    const localStart = cursor;

    // Dünya koordinatına çevir.
    const startAlong = collectionAtMin
      ? (lengthAxis === 'z' ? minZ : minX) + localStart
      : (lengthAxis === 'z' ? maxZ : maxX) - localStart;

    const origin = { x: minX, y: minY, z: minZ };
    if (lengthAxis === 'z') {
      origin.z = collectionAtMin ? startAlong : startAlong - (segLen - 1);
    } else {
      origin.x = collectionAtMin ? startAlong : startAlong - (segLen - 1);
    }

    segments.push({
      segmentId: i,
      origin,
      length: segLen,
      lengthAxis,
      buildDirSign,
      rotationY,
      localStart,
    });

    cursor += segLen + gap;
  }

  return { totalLength, width, lengthAxis, collectionAtMin, rotationY, segments };
}

module.exports = { splitSelection };
