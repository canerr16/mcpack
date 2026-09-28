'use strict';

// Ortam değişkenleriyle ezilebilen merkezi ayarlar.
module.exports = {
  PORT: parseInt(process.env.SWARM_PORT || '8765', 10),
  HOST: process.env.SWARM_HOST || '0.0.0.0',

  // Basit paylaşımlı token. Tüm client'lar (master + worker) aynısını gönderir.
  // Tek PC / localhost için yeterli; gerçek dünyada güçlü bir sırla değiştir.
  AUTH_TOKEN: process.env.SWARM_TOKEN || 'change-me-swarm-token',

  // Kaç worker beklenir (segment sayısı).
  WORKER_COUNT: parseInt(process.env.SWARM_WORKERS || '4', 10),

  // Segmentler arası güvenlik tamponu (blok). Sınırda çarpışmayı önler.
  SAFETY_GAP: parseInt(process.env.SWARM_GAP || '1', 10),

  // Minecraft data version — 1.21.x hedef sürümüne göre AYARLA.
  // Litematica dosyasının MinecraftDataVersion alanına yazılır.
  // (1.21.x için oyununun F3 ekranındaki data version değerini gir.)
  MC_DATA_VERSION: parseInt(process.env.SWARM_MC_DATA_VERSION || '3953', 10),

  // Litematica schematic format sürümü.
  LITEMATIC_VERSION: 6,

  // Smelter'da kullanılacak blok kimlikleri.
  BLOCKS: {
    HOPPER: 'minecraft:hopper',
    SMOKER: 'minecraft:smoker',
    FLOOR: process.env.SWARM_FLOOR_BLOCK || 'minecraft:smooth_stone',
    AIR: 'minecraft:air',
  },

  // Taşıyıcı/çıkış hunilerinin yatay yönü. Köşe1 (toplayıcı) -Z ucunda kabul edilir,
  // bu yüzden 'north' = -Z = akış Köşe1'e doğru.
  TRANSPORT_FACING: process.env.SWARM_TRANSPORT_FACING || 'north',
};
