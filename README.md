# SonicLab — AI Audio Studio

[![Android Build](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml/badge.svg)](https://github.com/soe1hom-arch/soniclab/actions/workflows/android-build.yml)

Premium, **fully offline** Android music player & audio toolkit (Kotlin + Jetpack Compose + Media3).
Player dengan efek DSP real-time, audio toolkit, analyzer, dan AI enhancement on-device — tanpa cloud, tanpa akun, tanpa internet.

## Offline-first

- Manifest aplikasi **tidak mendeklarasikan izin `INTERNET`** — tidak ada satu pun yang "phone home".
- Semua pemrosesan (efek playback, toolkit, analisis, model AI) berjalan on-device.
- Izin dibatasi hanya untuk fitur: membaca audio lokal, media playback di foreground, notifikasi, wake lock, dan kontrol audio session.

## Fitur (sesuai APK saat ini)

**Perpustakaan (Library)**
- Memindai media lokal via MediaStore (dengan alur izin "akses audio" bawaan aplikasi).
- Tab **Lagu / Album / Artis**: grid album dengan cover art, daftar artis, dan drill-down ke setiap album/artis.
- Pencarian lagu/artis/album, favorit, dan daftar lagu dengan thumbnail cover.
- Empty state yang jelas saat perpustakaan kosong, pencarian tanpa hasil, atau izin belum diberikan.

**Mini-player (semua layar)**
- Bar lagu yang sedang diputar di atas bottom navigation: cover, play/pause, next, dan garis progres tipis; tap untuk membuka player penuh.

**Player**
- Media3 ExoPlayer + `MediaSessionService`: notifikasi media, kontrol lock screen, dan dukungan Android Auto; decoder fallback untuk kompatibilitas hi-res/FLAC.
- Header cover, acak, previous / play-pause / next, repeat, dan preset kecepatan (0.75×–2×).
- **Pitch live** (−6..+6 st) — ubah nada langsung saat lagu diputar, tanpa mengubah kecepatan.
- **Tampilan antrean**: tap lagu untuk lompat, susun ulang dengan panah atas/bawah.
- Crossfade (0–12 dtk), sleep timer (15/30/60 mnt), dan **auto normalisasi (ala ReplayGain)** menuju −14 LUFS.
- **Mode 3D / 8D**: toggle cepat di layar player atau Equalizer > Mode 3D/8D. 3D melebarkan citra stereo (slider kekuatan), 8D menerapkan pan berputar lambat dengan feedback echo (slider kecepatan). Kini **berfungsi di semua lagu — termasuk mono dan hi-res/FLAC — dan toggle langsung berlaku, tanpa perlu ganti lagu**.

**Sound & Effects (Equalizer)**
- **Tone & Efek Real-time** (selalu aktif, langsung terdengar saat diputar — ala Poweramp):
  - **Treble** −12..+12 dB (shelf filter biquad real-time),
  - **Reverb (Room)** 0–100% + slider **Ukuran Ruangan** (jaringan Schroeder: 4 comb + 2 all-pass per kanal dengan detune stereo),
  - **Pitch (langsung)** −6..+6 st.
- **Efek Sistem Audio** (butuh lagu berjalan / audio session aktif): band equalizer 5-band (jumlah band sesuai perangkat `AudioEffect` API), prasetel, reset, **Bass** (BassBoost), **Virtualizer**, dan **Balance** stereo.
- Mode **3D/8D** + slider kekuatan/kecepatan.

**Studio (satu layar terpadu dari player)**
- Terikat langsung ke lagu yang sedang diputar (otomatis jadi File 1 saat dibuka), dengan analyzer + toolkit di satu tempat:
  - **Info File** — info decode/codec,
  - **Konversi ke WAV** — transcode ke WAV via MediaCodec,
  - **Cut** — ekspor 30 detik pertama.
- **DSP engine (on-device)** — terhubung penuh ke UI (pemrosesan file penuh dibatasi ~10 menit per file demi keamanan memori):
  - **Join** — gabungkan dua file menjadi satu WAV,
  - **Reverse** — audio diputar balik,
  - **Pitch** (−12..+12 st) dan **Tempo** (0.5×–2×) — slider WSOLA interaktif,
  - **Normalizer (−14 LUFS)** — normalisasi loudness,
  - **Vocal Remover** — keluaran instrumental (butuh file stereo; mask TFLite + fallback spektral).
- **Hasil tool**: setiap tool yang selesai memunculkan kartu **Hasil Siap** dengan aksi **Mainkan** (langsung putar hasilnya), **Bagikan** (share sheet via FileProvider), dan **Simpan ke Download** (MediaStore, Android 10+).
- **Analyzer** — analisis sekali sentuh untuk lagu yang diputar atau file yang dipilih: pratinjau waveform, info file audio, dan analisis loudness (LUFS) melalui pipeline `PcmReader`.

**AI (on-device)**
- **AI Enhance** — peningkatan real-time di jalur playback via Media3 `AudioProcessor`; model denoiser TensorFlow Lite dengan fallback DSP klasik. Kini juga aktif untuk track float/hi-res.
- **Vocal separator** — model mask TFLite bawaan (dengan fallback spektral/STFT center-channel).
- Model bawaan: `denoiser_v1.tflite`, `separator_v1.tflite` (dikirim di dalam aset APK).

**Pengaturan (Settings)**
- Tema **AMOLED** (hitam pekat), toggle **AI Enhance** (real-time), **Auto Normalisasi**, slider **Crossfade**, **Timer Tidur**, status model AI, dan **Tentang Aplikasi & Developer** (dialog info lengkap).

**UI & ikon**
- Bahasa Indonesia konsisten di seluruh aplikasi.
- Tema gelap premium (palet ungu/cyan) dengan opsi AMOLED.
- Ikon launcher premium: adaptive icon (gradient + waveform EQ dengan glow), `round` icon, dan `monochrome` untuk themed icon Android 13+.
- Empty states, transisi antar layar (fade + slide), dialog About, haptic feedback pada kontrol utama, dan cover album di-downsample untuk efisiensi RAM.

## Rantai DSP real-time

Semua efek playback berjalan dalam satu rantai `AudioProcessor` Media3 di dalam `DefaultAudioSink`:

```
balance → gain (auto-normalisasi) → tone (bass/treble) → reverb (room) → AI enhance → spatial (3D/8D)
```

Semua processor diturunkan dari `PcmAudioProcessor` yang:
- menerima **PCM16 dan PCM float** (tidak lagi gagal pada track hi-res/FLAC),
- selalu aktif sehingga **toggle efek berlaku seketika** tanpa menunggu seek/ganti lagu,
- meng-upmix **mono → stereo** untuk efek 3D/8D,
- melewatkan audio secara utuh (zero-copy) saat efek mati.

## Modul (MVVM + Repository)

Struktur sengaja modular (4 modul) agar pemisahan tanggung jawab jelas: data, pemrosesan audio, playback, dan UI. Kode di dalamnya tetap dikelompokkan per paket (`com.soniclab.*`), jadi lapisan logisnya tidak hilang.

- `:core` — model domain (Track/Playlist/Preset), result wrapper, permissions, util waktu, pemindaian MediaStore, playlist/favorit, preferensi DataStore
- `:dsp` — pemrosesan sinyal audio & analisis: AudioEffect/Oboe, toolkit DSP & konversi WAV (MediaCodec), analyzer (FFT, LUFS, waveform, info codec), dan AI on-device (TensorFlow Lite + fallback DSP)
- `:player` — Media3 ExoPlayer + MediaSessionService (notifikasi, lock screen, Android Auto), rantai `PcmAudioProcessor` (balance/gain/tone/reverb/enhance/spatial), kecepatan, crossfade, sleep timer, pitch live via `PlaybackParameters`
- `:app` — UI Compose (theme, navigasi, layar, dialog About) + visualizer spektrum/waveform

## Build

Requirements: JDK 17+, Android SDK dengan `platforms;android-36` dan `build-tools;36.0.0`.

```bash
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Test, lint & analysis

```bash
./gradlew test               # semua unit test JVM
./gradlew :dsp:testDebugUnitTest      # unit test DSP/PCM toolkit
./gradlew :player:testDebugUnitTest   # unit test rantai DSP player (balance/gain/tone/reverb/spatial/enhance)
./gradlew :app:lintDebug     # Android Lint
```

Status di repo ini: build sukses, unit test lulus (9/9 di `:dsp`, 14/14 di `:player`), lint 0 error. CI menjalankan pemeriksaan yang sama di setiap push.

## Download APK

Repo ini **publik** dan GitHub Actions membangun APK debug di setiap push ke `main`:

1. Buka **Actions** → run **Android Build** terbaru yang hijau.
2. Unduh artifact **soniclab-debug-apk**.
3. Install `app-debug.apk` di perangkat (Android 8.0+ / API 26+).

Atau build lokal dengan perintah di atas.

## Model AI bawaan & pelatihan

Model kecil on-device dibuat dari `scripts/` (NumPy murni + flatbuffers — tanpa instalasi TensorFlow/PyTorch):

```bash
python3 scripts/train_denoiser.py   # regenerasi ai/.../assets/models/denoiser_v1.tflite
python3 scripts/train_separator.py  # regenerasi ai/.../assets/models/separator_v1.tflite
```

## Status jujur

- **Terverifikasi oleh CI**: compile + unit test + lint hijau; APK debug dihasilkan otomatis.
- **Belum terverifikasi**: perilaku runtime di perangkat nyata (perilaku audio session EQ per perangkat, MediaSession/notifikasi, kualitas AI Enhance pada musik asli, pemrosesan toolkit pada file besar, hasil efek DSP real-time). Ini perlu pengujian manual di perangkat Android fisik.

## Roadmap

- Model AI berkualitas lebih tinggi yang dilatih dari data musik asli.
- Lebih banyak knob kualitas WSOLA (prasetel kualitas di UI).
- Migrasi string UI ke resources (`strings.xml`) untuk dukungan multi-bahasa formal.
- Snackbar global untuk notifikasi lintas layar.
- Release build dengan signing.

## Lisensi

[Apache License 2.0](LICENSE) © 2026 soe1hom-arch
