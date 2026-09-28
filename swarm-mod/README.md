# swarm-mod (Fabric)

Master + Worker tek jar. `config/swarm.json` içindeki `role` alanına göre davranır.
Baritone + Litematica **doğrudan** kullanılır (yeniden yazılmaz).

- **Modül 2 — Master**: tahta balta ile alan seçimi + `/swarm start smelter`.
- **Modül 3 — Worker**: .litematic yükle → Litematica placement → Baritone build,
  Grim-uyumlu ayarlar + sneak-place + malzeme bekleme.

## Kurulum (5 instance = 1 master + 4 worker)

Her instance'ın `config/swarm.json`'ı:

```jsonc
// Master
{ "role": "master", "serverUrl": "http://127.0.0.1:8765", "token": "..." }

// Worker 1..4 (workerId farklı)
{ "role": "worker", "serverUrl": "http://127.0.0.1:8765", "token": "...", "workerId": "1" }
```

`token` üç yerde de (server + master + worker) aynı olmalı.

## Kullanım

1. `swarm-server` çalışır (`npm start`).
2. 4 worker client açılır → otomatik bağlanır (`worker:hello`).
3. Master elde tahta balta:
   - **Sol tık** = Köşe1 (toplayıcı/hattın başı)
   - **Sağ tık** = Köşe2
4. Master: `/swarm start smelter` → sunucu alanı böler, her worker'a segment +
   .litematic gönderir, worker'lar inşaya başlar.
5. Bir worker "malzeme bekliyor" derse Master chat'te uyarı görür; malzemeyi
   worker'ın üstüne atarsın → ~5 sn sonra devam eder.

Komutlar: `/swarm start [structure]`, `/swarm stop`, `/swarm status`, `/swarm pos1/pos2`.

## Build

```bash
cd swarm-mod
./gradlew build      # (Loom Minecraft + bağımlılıkları indirir; internet gerekir)
# çıktı: build/libs/swarm-mod-<ver>.jar  -> her instance'ın mods/ klasörüne
```

`gradle.properties` içindeki sürümleri hedefine göre ayarla:
`minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`,
`litematica_version`, `malilib_version`, `baritone_version`.

## Anti-cheat (Grim) yaklaşımı

Kullanıcı teyidi: "çok hızlı + uzak reach + air-place olmadıkça sorun yok."

**Bakma / dönme bug'larına karşı** (`AntiCheatTuning`):
- `antiCheatCompatibility=true` — rotasyonları sunucuyla tutarlı gönderir; Grim'de
  "client baktı sandı, server aynı fikirde değil" desync'ini (= havaya bakma /
  yetişemediği yere koymaya çalışma) önleyen asıl ayar.
- `freeLook=true` — vücut sabit, sadece kafa hedefe döner → **360 dönme yok**.
- `smoothLook=true` — kademeli, insani kafa hareketi.
- `randomLooking=false` — hedefi ıskalayıp havayı yumruklamayı önler.

**Havayı yumruklama / takılmaya karşı**:
- `allowBreak=false` — worker **sadece blok koyar, hiç kırmaz** → yanlış/boş bloğa
  vurma tamamen elenir. (İnşa alanı boş olmalı.)
- `WorkerController.watchdog` — ilerleme durur ve Baritone da boştaysa inşayı
  otomatik yeniden tetikler (öylece takılıp kalmayı kırar).

**Reach / hız**:
- `blockReachDistance=4.5` (vanilla) — aşmıyor; `blockPlacementPenalty` ile
  seri hızlı koyma kırılıp insani ritim veriliyor.

**Sneak-place (huni/smoker GUI'si açılmasın)** — `SneakPlaceMixin`:
- Huni yönü **tıklanan blok yüzeyiyle** belirlenir, bakış yönüyle değil; shift'in
  tek işi konteynere tıklarken GUI açılmasını engellemek.
- Mixin, worker inşa halindeyken `shouldCancelInteraction()`'ı `true` döndürür →
  GUI açılmaz **ama karakter fiilen ÇÖMELMEZ** (sneak tuşuna basılmaz). Böylece
  hareket yavaşlamaz, kenardan düşme/takılma artmaz. Grim için ideal.
- Air-place kapalı; her blok komşu yüzeye dayanır.

## ⚠️ Oyun-içi doğrulanacak dikişler (Modül 3 test)

Bu ortamda Fabric derlemesi yapılamadı (Loom/Minecraft indirmesi gerekiyor).
Aşağıdakiler pinlenen sürümlerle **oyunda** doğrulanmalı:

1. **Litematica API imzaları** (`LitematicaBridge`): `createFromFile`,
   `SchematicPlacement.createFor`, `setRotation`, `addSchematicPlacement`,
   `setSelectedSchematicPlacement`. Yansıma ile bağlandı; sürüm farkında log basar.
2. **Baritone komut yürütme** (`BaritoneBridge.execute`): `ICommandManager.execute`
   imzası; `litematica` komutunun seçili placement'ı bina ettiği doğrulanmalı.
3. **rotationY kalibrasyonu** (`geometry.js`): yerel -Z (çıkış yüzü) Köşe1'e
   bakacak şekilde. X-eksenli hatlarda 90/270 değeri gözle kontrol edilmeli.
4. **Progress metriği**: şu an envanter tüketimine dayalı kaba tahmin; gerekiyorsa
   Litematica'nın "eksik blok" sayısıyla değiştirilebilir.
5. **socket.io include/shadow**: jar içine gömülme (Fabric classpath) test edilmeli;
   gerekirse `shadow`/`jarJar` ile relocate.
6. **SneakPlaceMixin**: `PlayerEntity.shouldCancelInteraction()` yarn adı 1.21.x'te
   doğrulanmalı (mapping değişirse mixin `method` adı güncellenir).
