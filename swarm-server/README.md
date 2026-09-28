# swarm-server

Master-Worker swarm smelter orkestrasyon sunucusu (Modül 1).

- WebSocket (socket.io) + paylaşımlı token auth
- Master'ın seçtiği alanı 4 segmente böler (güvenlik tamponu ile)
- Her segment için dinamik **.litematic** üretir (Litematica + Baritone yer)
- Worker durum/progress takibi, malzeme-bekleme, disconnect'te resume

## Çalıştırma

```bash
cd swarm-server
npm install
npm start           # varsayılan 0.0.0.0:8765
npm test            # birim testleri
```

## Ortam değişkenleri

| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `SWARM_PORT` | 8765 | Dinlenen port |
| `SWARM_HOST` | 0.0.0.0 | Bind adresi |
| `SWARM_TOKEN` | change-me-swarm-token | Master+Worker ile aynı olmalı |
| `SWARM_WORKERS` | 4 | Segment/worker sayısı |
| `SWARM_GAP` | 1 | Segmentler arası tampon (blok) |
| `SWARM_MC_DATA_VERSION` | 3953 | Hedef 1.21.x data version — **oyununa göre ayarla** |
| `SWARM_FLOOR_BLOCK` | minecraft:smooth_stone | Zemin bloğu |
| `SWARM_TRANSPORT_FACING` | north | Taşıyıcı/çıkış huni yönü (-Z=north) |

## Örnek .litematic üretimi

`npm test` çalışınca `test/out/test_seg.litematic` oluşur. Bunu Minecraft'ta
Litematica'ya yükleyip kesit geometrisini gözle doğrulayabilirsin (worker mod'u
beklemeden). Yön/rotasyon kalibrasyonu Modül 3 (worker) testinde kesinleşecek.

## Protokol

`src/protocol.js` tek kaynak. Master `swarm:start` gönderir; sunucu her worker'a
`task:assign` (segment + base64 .litematic + malzeme listesi) yollar. Worker
`worker:progress` / `worker:need_material` / `worker:done` ile durum bildirir.
