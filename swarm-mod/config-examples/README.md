# swarm.json örnek config'leri

Her Minecraft instance'ı kendi `config/swarm.json` dosyasıyla master ya da worker olur.
Dosya, oyunun **game-directory**'sindeki `config/` klasörüne konur (mods klasörünün yanı).

## Kurulum
1. Bu instance master ise `swarm.master.json`, worker ise `swarm.worker.json` içeriğini
   `config/swarm.json` olarak kopyala.
2. Her worker instance'ında `workerId` değerini benzersiz yap: "1", "2", "3", "4".
3. `token` üç yerde de AYNI olmalı: swarm-server (SWARM_TOKEN), master ve tüm worker'lar.
   Tek PC/localhost için varsayılan "change-me-swarm-token" yeterli; istersen hepsinde
   aynı anda değiştir.

## Bu kuruluma göre eşleme
- farmbuilder11  -> master  (swarm.master.json)
- farmbotworker  -> worker  (swarm.worker.json, workerId="1"; ek worker'lar için 2/3/4)

Not: Mod, config/swarm.json yoksa ilk açılışta varsayılan bir dosya oluşturur;
o dosyayı yukarıdaki değerlere göre düzenlemen yeterli.
