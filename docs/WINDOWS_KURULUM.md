# Windows'ta Derleme ve Kurulum

> **Not:** Bu bulut oturumu senin PC'ne erişemez ve Fabric/Minecraft indirme
> sunucuları bu container'da ağ politikasıyla engelli. Bu yüzden **derleme ve
> kurulum senin PC'nde** yapılır. Aşağıdaki adımlar bunun içindir.

## 0. Gerekenler (senin PC'nde)
- **JDK 21** (Temurin/Adoptium önerilir). `java -version` → 21 görmeli.
- Repoyu PC'ne çek (GitHub'a push edilince) ya da `swarm-mod` klasörünü kopyala.

## 1. Mod'u derle

`swarm-mod` klasöründe (PowerShell/CMD):

```bat
cd swarm-mod
gradlew.bat build
```

- İlk derleme Minecraft + Fabric + Litematica + malilib + Baritone indirir (birkaç dk).
- Çıktı: `swarm-mod\build\libs\swarm-mod-0.1.0.jar`
  (varsa `-sources.jar`'ı kurma, sadece ana jar).

`gradle.properties` içindeki sürümleri kullandığın sürüme göre kontrol et:
`minecraft_version`, `yarn_mappings`, `loader_version`, `fabric_version`,
`litematica_version`, `malilib_version`, `baritone_version`.

## 2. "farmbuilder11" sürümü — mod'lar NEREYE gider?

Dikkat: mod jar'ı `versions\farmbuilder11` klasörüne **konmaz**. O klasör oyunun
client jar+json'unu tutar. Mod'lar oyunun **game directory**'sindeki `mods\`
klasörüne konur.

Önce `farmbuilder11` sürümünün **Fabric Loader'lı** olduğundan emin ol:
- https://fabricmc.net/use/installer/ → Fabric Installer'ı çalıştır → hedef MC
  sürümünü seç → kur. Bu, `.minecraft\versions\` altında `fabric-loader-...` bir
  sürüm oluşturur. `farmbuilder11` bunun kopyası/yeniden adlandırılmışıysa Fabric
  içermeli.

Her instance için `mods\` klasörüne şu jar'ları koy:
```
swarm-mod-0.1.0.jar          (bizim mod)
fabric-api-*.jar             (mc sürümüne uygun)
litematica-fabric-*.jar
malilib-fabric-*.jar
baritone-*.jar               (fabric build)
```

## 3. 5 instance kurulumu (1 master + 4 worker)

En temizi her instance'ın **ayrı game directory**si olması (aynı `.minecraft`
altında mod'lar çakışmasın diye profil/dizin ayır). Launcher'da (vanilla launcher,
MultiMC ya da Prism) her hesap için ayrı profil + ayrı game dir aç.

Her instance'ın `config\swarm.json`'ı:

```jsonc
// Master (senin oynadığın)
{ "role": "master", "serverUrl": "http://127.0.0.1:8765", "token": "GIZLI" }

// Worker 1..4 (her birinde workerId farklı)
{ "role": "worker", "serverUrl": "http://127.0.0.1:8765", "token": "GIZLI", "workerId": "1" }
```

`token` üçünde de (server + master + worker) **aynı** olmalı.

## 4. Sunucuyu çalıştır (Node.js, senin PC'nde)

```bat
cd swarm-server
npm install
set SWARM_TOKEN=GIZLI
set SWARM_MC_DATA_VERSION=<oyunun F3 data version'u>
npm start
```

## 5. Çalıştırma akışı
1. `swarm-server` açık.
2. 4 worker client açılır → otomatik bağlanır.
3. Master elde tahta balta: **sol tık = Köşe1** (toplayıcı ucu), **sağ tık = Köşe2**.
4. Master chat: `/swarm start smelter`.
5. Worker'lar segmentlerini inşa eder. "Malzeme bekliyor" derse üstlerine at.

## İlk derlemede hata çıkarsa
`gradlew.bat build` çıktısındaki ilk hatayı bana at — özellikle Litematica/Baritone
maven adresleri veya sürüm uyuşmazlıkları (`build.gradle` repolarını/sürümlerini
gerçek jar'lara göre sabitleriz).

---

## (Alternatif) Bu bulut oturumunda derlemek istersen
Container'ın ağ politikası şu an sadece paket registry'lerine izin veriyor.
Fabric derlemesi için oturum ayarlarından (başlık çubuğundaki cloud environment
menüsü → Edit → Network access) şu host'lara izin vermen gerekir:
`maven.fabricmc.net`, `libraries.minecraft.net`, `piston-data.mojang.com`,
`piston-meta.mojang.com`, `repo1.maven.org`, `plugins.gradle.org`,
`services.gradle.org`, `masa.dev`, `maven.meteordev.org`.
İzin verirsen yeni bir oturumda derlemeyi ben de yapabilirim. Erişim seviyeleri:
https://code.claude.com/docs/en/claude-code-on-the-web
