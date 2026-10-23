# Akış

Sakin, sonlu ve gizliliğe saygılı bir RSS okuyucusu. Jetpack Compose ve Material 3 Expressive ile yazıldı.

- Yalnızca internet izni; yalnızca HTTPS; WebView yok; arka planda çalışma yok.
- Günlük sınırlı, okunmamış noktası olmayan, bitebilen bir akış.
- Kaynak ekleme: adres yapıştır (RSS otomatik bulunur), OPML içe aktar ya da yerleşik **Keşfet** kataloğu.

## Derleme

```bash
./gradlew detekt lintDebug testDebugUnitTest verifyRoborazziDebug assembleRelease
```

Ekran görüntüsü testleri `app/src/test/screenshots/` altındadır; arayüz değişince `./gradlew recordRoborazziDebug` ile yeniden kaydedilir.
Proje kuralları ve mimari için `CLAUDE.md` dosyasına bak.

## Yayın

`v*` etiketi (ör. `v1.18.0`) gönderilince iş akışı APK'yı derleyip GitHub sürümü olarak yayımlar.
