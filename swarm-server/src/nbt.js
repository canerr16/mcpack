'use strict';

// Minimal, bağımlılıksız NBT (Named Binary Tag) yazıcı.
// Litematica .litematic üretimi için gereken tag tiplerini destekler.
//
// Kullanım:
//   const buf = writeNbt('', compound({ ... }));   // uncompressed
//   const gz  = require('zlib').gzipSync(buf);      // .litematic gzip'lidir
//
// Yardımcılar bir "typed value" sarmalayıcı döndürür ki JS objesindeki
// int/short/long/float/double ayrımı korunabilsin.

const TAG = {
  END: 0,
  BYTE: 1,
  SHORT: 2,
  INT: 3,
  LONG: 4,
  FLOAT: 5,
  DOUBLE: 6,
  BYTE_ARRAY: 7,
  STRING: 8,
  LIST: 9,
  COMPOUND: 10,
  INT_ARRAY: 11,
  LONG_ARRAY: 12,
};

// Typed sarmalayıcılar
const byte = (v) => ({ __t: TAG.BYTE, v });
const short = (v) => ({ __t: TAG.SHORT, v });
const int = (v) => ({ __t: TAG.INT, v });
const long = (v) => ({ __t: TAG.LONG, v: BigInt(v) });
const float = (v) => ({ __t: TAG.FLOAT, v });
const double = (v) => ({ __t: TAG.DOUBLE, v });
const string = (v) => ({ __t: TAG.STRING, v: String(v) });
const compound = (obj) => ({ __t: TAG.COMPOUND, v: obj });
const longArray = (arr) => ({ __t: TAG.LONG_ARRAY, v: arr.map((x) => BigInt(x)) });
const intArray = (arr) => ({ __t: TAG.INT_ARRAY, v: arr });
// list: elemanların tümü aynı tip olmalı; typed sarmalayıcılarla verilir.
const list = (elemType, items) => ({ __t: TAG.LIST, elemType, v: items });

class ByteSink {
  constructor() {
    this.chunks = [];
  }
  push(buf) {
    this.chunks.push(buf);
  }
  u8(v) {
    const b = Buffer.allocUnsafe(1);
    b.writeUInt8(v & 0xff, 0);
    this.push(b);
  }
  i8(v) {
    const b = Buffer.allocUnsafe(1);
    b.writeInt8(v, 0);
    this.push(b);
  }
  i16(v) {
    const b = Buffer.allocUnsafe(2);
    b.writeInt16BE(v, 0);
    this.push(b);
  }
  i32(v) {
    const b = Buffer.allocUnsafe(4);
    b.writeInt32BE(v, 0);
    this.push(b);
  }
  i64(v) {
    const b = Buffer.allocUnsafe(8);
    b.writeBigInt64BE(BigInt.asIntN(64, BigInt(v)), 0);
    this.push(b);
  }
  f32(v) {
    const b = Buffer.allocUnsafe(4);
    b.writeFloatBE(v, 0);
    this.push(b);
  }
  f64(v) {
    const b = Buffer.allocUnsafe(8);
    b.writeDoubleBE(v, 0);
    this.push(b);
  }
  str(s) {
    const data = Buffer.from(String(s), 'utf8');
    this.i16(data.length);
    this.push(data);
  }
  toBuffer() {
    return Buffer.concat(this.chunks);
  }
}

function writePayload(sink, node) {
  switch (node.__t) {
    case TAG.BYTE:
      sink.i8(node.v);
      break;
    case TAG.SHORT:
      sink.i16(node.v);
      break;
    case TAG.INT:
      sink.i32(node.v);
      break;
    case TAG.LONG:
      sink.i64(node.v);
      break;
    case TAG.FLOAT:
      sink.f32(node.v);
      break;
    case TAG.DOUBLE:
      sink.f64(node.v);
      break;
    case TAG.STRING:
      sink.str(node.v);
      break;
    case TAG.LONG_ARRAY:
      sink.i32(node.v.length);
      for (const x of node.v) sink.i64(x);
      break;
    case TAG.INT_ARRAY:
      sink.i32(node.v.length);
      for (const x of node.v) sink.i32(x);
      break;
    case TAG.LIST: {
      const items = node.v;
      const elemType = node.elemType;
      sink.u8(elemType);
      sink.i32(items.length);
      for (const it of items) {
        // liste elemanları typed sarmalayıcı ya da compound olabilir
        writePayload(sink, it);
      }
      break;
    }
    case TAG.COMPOUND: {
      for (const [name, child] of Object.entries(node.v)) {
        if (child == null) continue;
        sink.u8(child.__t);
        sink.str(name);
        writePayload(sink, child);
      }
      sink.u8(TAG.END);
      break;
    }
    default:
      throw new Error('Bilinmeyen NBT tag tipi: ' + node.__t);
  }
}

// Kök tag her zaman bir compound'dur. name genelde '' (Litematica boş kök adı kullanır).
function writeNbt(rootName, rootCompound) {
  if (rootCompound.__t !== TAG.COMPOUND) {
    throw new Error('Kök tag compound olmalı');
  }
  const sink = new ByteSink();
  sink.u8(TAG.COMPOUND);
  sink.str(rootName || '');
  writePayload(sink, rootCompound);
  return sink.toBuffer();
}

module.exports = {
  TAG,
  byte,
  short,
  int,
  long,
  float,
  double,
  string,
  compound,
  list,
  longArray,
  intArray,
  writeNbt,
};
