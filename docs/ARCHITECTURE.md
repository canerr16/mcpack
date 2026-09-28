# Swarm Smelter — Mimari

Minecraft 1.21.x (Fabric, Grim anti-cheat uyumlu) için Master-Worker
mimarili çift şeritli süper smelter inşa otomasyonu.

## Bileşenler

```
                    ┌─────────────────────────┐
                    │   swarm-server (Node.js) │
                    │   - socket.io + auth     │
                    │   - alan bölme           │
                    │   - .litematic üretimi   │
                    │   - görev/progress state │
                    └───────────┬─────────────┘
              WebSocket         │        WebSocket
        ┌───────────────────────┼───────────────────────┐
        │                       │                        │
┌───────▼───────┐      ┌────────▼────────┐   ...  ┌──────▼──────┐
│ swarm-master  │      │  swarm-worker 1 │        │ swarm-worker4│
│ (Fabric mod)  │      │  (Fabric mod)   │        │ (Fabric mod) │
│ alan seç,     │      │  Litematica +   │        │              │
│ /swarm start  │      │  Baritone build │        │              │
└───────────────┘      └─────────────────┘        └─────────────┘
```

Her şey tek PC'de çalışır: 1 Node.js sunucu + 1 Master client + 4 Worker client.

## Karar Özeti (mutabakat)

| Konu | Karar |
|---|---|
| Ortam | Survival, Grim AC — air-place yok, insan reach (≤4), orta hız + tick jitter, sneak-place |
| Fırın | Smoker |
| Yerleştirme motoru | Litematica (dinamik .litematic) + Baritone `#litematica` |
| Client | Fabric 1.21.x |
| Alan seçimi | Master client, tahta balta: sol tık = Köşe1, sağ tık = Köşe2 |
| Hattın başı | Köşe1 = toplayıcı sandık ucu; çıkış hunileri buraya doğru zincirlenir |
| Besleme | Malzeme bitince worker durur, Master atar, ~5 sn sonra devam |
| Çöken worker | Bekler, progress (kaldığı Z) hafızada tutulur |

## Enine Kesit (genişlik 4, yükseklik 5 — bir Z dilimi)

```
        X0 (Sol Yakıt)  X1 (Sol Fırın)  X2 (Sağ Fırın)  X3 (Sağ Yakıt)
 L4                     İTH (→ -Z)      İTH (→ -Z)
 L3     YTH (→ -Z)      HH  (↓ down)    HH  (↓ down)    YTH (→ -Z)
 L2     YH  (→ +X)      SMOKER          SMOKER          YH  (→ -X)
 L1                     ÇH  (→ -Z)      ÇH  (→ -Z)
 L0                     ZEMİN           ZEMİN
```

Akış (smelter çalışırken):
- **Hammadde:** İTH (L4, hat boyunca taşır) → altındaki HH (L3, huni yukarıdaki huniden otomatik çeker) → aşağı smoker üst slotu.
- **Yakıt:** YTH (L3) → altındaki YH (L2, yukarıdan çeker) → yandan smoker yakıt slotu.
- **Çıktı:** SMOKER → altındaki ÇH (L1) → -Z yönünde Köşe1'deki toplayıcı sandığa.

> Huni mekaniği: bir huni, tam üstündeki envanter bloğundan eşyayı **otomatik çeker**;
> spout (ağız) yönündeki bloğa **iter**. Bu yüzden taşıyıcı huniler (İTH/YTH) yatay
> ilerlerken, altlarındaki HH/YH onlardan yukarıdan çekip smoker'a basar.

Yön eşlemesi (Köşe1 = -Z ucu kabulüyle):
- ÇH, İTH, YTH → **north (-Z)** (Köşe1'e doğru; `TRANSPORT_FACING` ile ayarlanabilir)
- HH → **down**
- X0 YH → **east (+X)**, X3 YH → **west (-X)**
- SMOKER → **north** (görsel; huni akışını etkilemez)

## Segment Bölme

Master'ın seçtiği kutu, uzun yatay eksen (X veya Z) boyunca 4 eşit segmente
bölünür. Segmentler arasında `SAFETY_GAP` (varsayılan 1 blok) tampon bırakılır ki
iki worker sınırda çarpışmasın. Her worker yalnızca kendi segmentini inşa eder.

## WebSocket Protokolü

`swarm-server/src/protocol.js` içindeki mesaj tipleri tek kaynak. Özet:

Master → Server:
- `master:hello`, `swarm:start` (corner1, corner2, structure)

Worker → Server:
- `worker:hello` (workerId), `worker:progress` (placedZ), `worker:need_material`
  (item, count), `worker:done`, `worker:error`

Server → Worker:
- `task:assign` (segment origin, size, rotationY, litematic base64, materialList)
- `task:resume` (fromZ)

Server → Master:
- `swarm:status` (her worker'ın state + progress'i)
