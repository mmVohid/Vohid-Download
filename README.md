# Vohid Download

Internetdagi videolarni Android telefoningizga yuklab beruvchi ilova. Ichida **yt-dlp** va **FFmpeg** ishlaydi (Seal ilovasi ishlatadigan `youtubedl-android` kutubxonasi orqali), interfeysi esa Jetpack Compose + Material 3 (Material You) da yozilgan.

## Imkoniyatlar

- Havola orqali video yuklash (havolani qoʻyish yoki boshqa ilovadan **Ulashish** orqali yuborish)
- Playlistlar: kerakli videolarni tanlab, navbat bilan yuklash
- Format va sifat tanlash: **MP4 / MKV** (144p dan 4K gacha, taxminiy hajm koʻrsatiladi), **MP3 / M4A** (audio)
- Yuklash navbati, bir vaqtda 1–3 ta yuklash, bekor qilish va qayta urinish
- Foreground servis va bildirishnoma: ilova yopilganda ham yuklash davom etadi
- Fayllar `Movies/VohidDownload` va `Music/VohidDownload` papkalariga tushadi (galereya va musiqa ilovalarida koʻrinadi, ruxsat talab qilinmaydi)
- Yorugʻ/qorongʻi mavzu, dinamik ranglar (Android 12+), oʻzbek va ingliz tillari
- yt-dlp ni ilovaning oʻzida yangilash (saytlar oʻzgarganda kerak boʻladi) va avtomatik yangilash

## APK olish

### Variant 1: GitHub Actions (Android Studio shart emas)

1. Loyihani GitHub repozitoriyasiga yuklang.
2. **Actions → APK yigʻish → Run workflow** ni bosing (yoki `main` ga push qiling).
3. Tugagach, **Artifacts → VohidDownload-apk** ichidan APK ni yuklab oling. Koʻpchilik telefonlar uchun `arm64-v8a` varianti mos; ishonchingiz komil boʻlmasa `universal` ni oling.
4. `v1.0.0` kabi teg qoʻysangiz, APK avtomatik **Releases** ga ham qoʻshiladi.

Bir xil kalit bilan imzolash uchun (yangilanganda ilovani oʻchirmasdan ustidan oʻrnatish uchun) repozitoriy **Secrets** ga `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` ni qoʻshing. Aks holda har yigʻishda debug kalit ishlatiladi.

### Variant 2: Android Studio

1. Papkani **Android Studio** (Ladybug yoki yangiroq) da oching, Gradle sinxronlashini kuting.
2. **Build → Build APK(s)** yoki telefonni ulab **Run**.

## Talablar

- Android 10 (API 29) va undan yangi
- Birinchi ishga tushishda yuklovchi tayyorlanadi (bir necha soniya)

## Muhim eslatmalar

- **Google Play**: YouTube kabi saytlardan yuklab beruvchi ilovalar Play Market qoidalariga zid, shuning uchun ilova APK koʻrinishida tarqatiladi.
- **Huquqiy tomon**: faqat oʻzingizga tegishli yoki yuklashga ruxsat berilgan materiallar uchun foydalaning. Mualliflik huquqi va saytlarning foydalanish shartlariga rioya qilish foydalanuvchi zimmasida.
- **Litsenziya**: `youtubedl-android` GPL-3.0 ostida, shuning uchun bu loyiha ham **GPL-3.0** (`LICENSE` fayliga qarang). APK ni boshqalarga tarqatsangiz, manba kodini ham ochiq qoldirishingiz kerak.
- Ayrim saytlar (masalan, YouTube) vaqti-vaqti bilan himoyani oʻzgartiradi. Yuklash xato bersa, **Sozlamalar → Hozir yangilash** ni bosing.

## Tuzilma

```
app/src/main/java/uz/vohid/download/
├── VohidApp.kt, AppGraph.kt      ilova boshlangʻichi, umumiy obyektlar
├── MainActivity.kt               navigatsiya (Bosh sahifa / Yuklamalar / Sozlamalar)
├── core/
│   ├── Engine.kt                 yt-dlp ishga tushirish, havola tahlili, yangilash
│   ├── Downloader.kt             navbat, parallel yuklash, progress, bekor qilish
│   ├── DownloadService.kt        foreground servis + bildirishnoma
│   ├── Publisher.kt              faylni MediaStore orqali galereyaga joylash
│   └── Notifications.kt, Errors.kt
├── data/                         modellar, DataStore sozlamalari, tarix (JSON)
└── ui/                           Compose ekranlari, tema, ViewModel
```
