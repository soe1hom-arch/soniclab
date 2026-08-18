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
- **Info format lagu** — di layar player ditampilkan codec, sample rate, bit depth, dan kanal (mis. `FLAC • 96.0 kHz • 24-bit • Stereo`) yang dibaca real-time dari decoder.
- **Tampilan antrean**: hanya menampilkan **Antrean Berikutnya** (lagu setelah lagu yang diputar); jika belum ada lagu berikutnya, bagian ini disembunyikan. Tap untuk lompat, susun ulang dengan panah atas/bawah. Dari daftar lagu, menu **Putar Berikutnya** / **Tambahkan ke Antrean** tersedia di setiap lagu.
- Crossfade (0–12 dtk), sleep timer (15/30/60 mnt), dan **auto normalisasi menuju −14 LUFS** — pengukuran **EBU R128 asli** (K-weighting + blok 400 ms + gating absolut/relatif, bukan RMS biasa).
- **Mode 3D / 8D**: preset **Mati / 3D / 8D / 8D+Tengah / Surround** (chips cepat di player, lengkap di Equalizer). **8D+Tengah** menerapkan rotasi 8D pada audio normal (tanpa pelebaran mid/side agar tidak terdengar pecah di headset) sambil tetap menjaga suara tengah agar tidak hilang saat efek berputar. Pan 8D kini **tidak lagi mematikan salah satu channel** (slider **Kedalaman Pan**); mode **Surround** menambah gema ruang tanpa rotasi. **Berfungsi di semua lagu — termasuk mono dan hi-res/FLAC — dan toggle langsung berlaku, tanpa perlu ganti lagu**.

**Sound & Effects (Equalizer)**
- Layar Equalizer berupa **menu per-bagian** (tap untuk membuka/menutup): **Prasetel Equalizer**, **Mode 3D/8D**, **Tone & Efek**. Hanya bagian EQ yang terbuka saat masuk — tidak lagi acak-acakan dalam satu halaman memanjang.
- **Mode 3D / 8D** — preset siap pakai **atau racik sendiri**: switch **3D**, **8D**, dan **Surround** bisa dikombinasikan bebas (mode berubah jadi "Custom"), plus slider **Kekuatan 3D**, **Kecepatan Putaran 8D**, dan **Kedalaman Pan 8D** (membatasi pergerakan pan agar suara di tengah tetap terdengar di headset). Preset **8D+Tengah** = 8D pada audio normal dengan jangkar tengah, tanpa pelebaran stereo.
- **Tone & Efek Real-time** (selalu aktif, langsung terdengar saat diputar — ala Poweramp):
  - **Limiter (anti-pecah)** — tahap akhir rantai DSP: puncak di atas 0 dB dikecilkan per-frame (attack instan, release halus) jadi preset boost penuh atau lagu keras tidak lagi terpotong keras,
  - **Treble** −12..+12 dB (shelf filter biquad real-time),
  - **Reverb (Room)** 0–100% + slider **Ukuran Ruangan** (jaringan Schroeder: 4 comb + 2 all-pass per kanal dengan detune stereo),
  - **Pitch (langsung)** −6..+6 st.
- **Equalizer software 10-band** — band peaking (31.25 Hz–16 kHz, ±15 dB) berjalan **di dalam rantai DSP**, konsisten di semua perangkat (tidak lagi bergantung pada API `AudioEffect` yang deprecated), langsung berlaku bahkan sebelum lagu diputar. Ditampilkan sebagai **visual slider vertikal** gaya pemutar premium (boost ke atas, cut ke bawah). **Bass** kini shelf biquad real-time (bukan BassBoost ganda), plus **Balance** stereo dan prasetel.
- Tombol **Atur Ulang** (semua / per-bagian) kini meminta **konfirmasi** sebelum mereset, jadi penyetelan tidak hilang karena salah tap.
- **Prasetel di-retune**: `Music HD` lebih halus (boost ekstrem dikurangi), `Gaming` (V-shape + virtualizer) kini berbeda dari `Car Audio` (bass kuat + mid naik untuk noise jalan), `BT Speaker` fokus mid-bass 100–250 Hz (sub-bass yang tidak bisa direproduksi speaker kecil dihilangkan), plus preset baru **Bass + Vokal**, **Acoustic**, dan **Jazz** (dengan reverb ruang hangat).
- **Penyetelan tersimpan otomatis** — balance, mode 3D/8D, treble, reverb, pitch, bass, dan band EQ dipulihkan saat app dibuka lagi (tidak reset setelah keluar).

**Studio (satu layar terpadu — dibuka dari menu „Lainnya" setiap lagu di Perpustakaan)**
- Lagu yang dipilih di Perpustakaan otomatis menjadi **File 1** (fallback ke lagu yang sedang diputar bila dibuka tanpa pilihan), dengan analyzer + toolkit di satu tempat:
  - **Info File** — info decode/codec,
  - **Konversi ke WAV** — transcode ke WAV via MediaCodec,
  - **Cut** — ekspor 30 detik pertama.
- **DSP engine (on-device)** — terhubung penuh ke UI (pemrosesan file penuh dibatasi ~10 menit per file demi keamanan memori):
  - **Join** — gabungkan dua file menjadi satu WAV,
  - **Reverse** — audio diputar balik,
  - **Pitch** (−12..+12 st) dan **Tempo** (0.5×–2×) — slider WSOLA interaktif,
  - **Normalizer (−14 LUFS)** — normalisasi loudness berbasis **EBU R128**,
  - **Vocal Remover** — keluaran instrumental (butuh file stereo; STFT center-channel dengan soft ratio mask + smoothing).
- **Hasil tool**: setiap tool yang selesai memunculkan kartu **Hasil Siap** dengan aksi **Mainkan** (langsung putar hasilnya), **Bagikan** (share sheet via FileProvider), dan **Simpan ke Download** (MediaStore, Android 10+). Pesan status (berhasil/gagal) tampil sebagai **Snackbar** yang bisa ditutup.
- **Analyzer** — analisis sekali sentuh untuk lagu yang diputar atau file yang dipilih: pratinjau waveform, info file audio, dan analisis loudness **LUFS EBU R128** melalui pipeline `PcmReader`.

**AI (on-device)**
- **AI Enhance** — peningkatan real-time di jalur playback via Media3 `AudioProcessor`; model denoiser TensorFlow Lite dengan fallback **DSP transparan** (tanpa pewarnaan EQ, tanpa hard clip). Kini juga aktif untuk track float/hi-res.
- **Vocal separator** — STFT center-channel dengan soft ratio mask (tanpa model neural; model TFLite per-bin ~4 KB dihapus karena kualitasnya di bawah versi spektral).
- Model bawaan: `denoiser_v1.tflite` (dikirim di dalam aset APK).

**Pengaturan (Settings)**
- Tema **AMOLED** (hitam pekat), toggle **AI Enhance** (real-time), **Auto Normalisasi** (EBU R128), **Mode Langsung (Direct)** — rebuild sink tanpa rantai DSP untuk jalur output paling bersih, slider **Crossfade**, **Timer Tidur**, status model AI, dan **Tentang Aplikasi & Developer** (dialog info lengkap).
- **Output Audio** (kualitas jalur keluaran):
  - **Output Hi-Res 24-bit** — jalur decoder → DSP → AudioTrack tetap float (tidak diturunkan ke 16-bit); player di-rebuild otomatis dengan antrean & posisi dipertahankan,
  - **Dither TPDF + Noise Shaping** — on/off; saat mati konversi 16-bit memakai plain rounding,
  - **Headroom** −3..0 dB — ruang aman sebelum efek agar EQ/preset tidak cepat pecah.

**UI & ikon**
- Bahasa Indonesia konsisten di seluruh aplikasi.
- **Lagu yang sedang diputar ditandai di daftar lagu**: nama lagu berwarna utama + ikon pemutar, jadi mudah dikenali saat menelusuri perpustakaan.
- Tema gelap premium (palet ungu/cyan) dengan opsi AMOLED.
- Ikon launcher premium: adaptive icon (gradient + waveform EQ dengan glow), `round` icon, dan `monochrome` untuk themed icon Android 13+.
- Empty states, transisi antar layar (fade + slide), dialog About, haptic feedback pada kontrol utama, dan cover album di-downsample untuk efisiensi RAM.

## Rantai DSP real-time

Semua efek playback berjalan dalam satu rantai `AudioProcessor` Media3 di dalam `DefaultAudioSink`:

```
balance → headroom → gain (auto-normalisasi) → EQ 10-band → tone (bass/treble) → reverb (room) → AI enhance → spatial (3D/8D) → limiter (anti-pecah)
```

Mode **Direct** mem-bypass seluruh rantai ini (service membangun ulang player
tanpa `AudioProcessor`, posisi antrean tetap dipertahankan).

Semua processor diturunkan dari `PcmAudioProcessor` yang:
- menerima **PCM16 dan PCM float** (tidak lagi gagal pada track hi-res/FLAC),
- selalu aktif sehingga **toggle efek berlaku seketika** tanpa menunggu seek/ganti lagu,
- meng-upmix **mono → stereo** untuk efek 3D/8D,
- melewatkan audio secara utuh (zero-copy) saat efek mati,
- memakai **TPDF dither + noise shaping orde-2** saat re-encode ke PCM16 setelah efek aktif (bisa dimatikan via Settings; passthrough tetap bit-exact),
- dengan **Output Hi-Res** aktif, seluruh rantai berjalan dalam float sampai ke AudioTrack.

## Modul (MVVM + Repository)

Struktur sengaja modular (4 modul) agar pemisahan tanggung jawab jelas: data, pemrosesan audio, playback, dan UI. Kode di dalamnya tetap dikelompokkan per paket (`com.soniclab.*`), jadi lapisan logisnya tidak hilang.

- `:core` — model domain (Track/Playlist/Preset), result wrapper, permissions, util waktu, pemindaian MediaStore, playlist/favorit, preferensi DataStore
- `:dsp` — pemrosesan sinyal audio & analisis: audioengine (Oboe scaffold), toolkit DSP & konversi WAV (MediaCodec), analyzer (FFT, **R128 LUFS**, waveform, info codec), dan AI on-device (TensorFlow Lite + fallback DSP)
- `:player` — Media3 ExoPlayer + MediaSessionService (notifikasi, lock screen, Android Auto), rantai `PcmAudioProcessor` (balance/headroom/gain/**EQ 10-band**/tone/reverb/enhance/spatial/limiter), Direct mode, output hi-res float, dither on/off, kecepatan, crossfade, sleep timer, pitch live via `PlaybackParameters`
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
./gradlew :player:testDebugUnitTest   # unit test rantai DSP player (balance/gain/eq/tone/reverb/spatial/limiter/dither)
./gradlew :app:lintDebug     # Android Lint
```

Status di repo ini: build sukses, unit test lulus (13/13 di `:dsp`, 23/23 di `:player`), lint 0 error. CI menjalankan pemeriksaan yang sama di setiap push.

## Download APK

Repo ini **publik** dan GitHub Actions membangun APK debug di setiap push ke `main`:

1. Buka **Actions** → run **Android Build** terbaru yang hijau.
2. Unduh artifact **soniclab-debug-apk**.
3. Install `app-debug.apk` di perangkat (Android 8.0+ / API 26+).

Atau build lokal dengan perintah di atas.

## Model AI bawaan & pelatihan

Model kecil on-device dibuat dari `scripts/` (NumPy murni + flatbuffers — tanpa instalasi TensorFlow/PyTorch):

```bash
python3 scripts/train_denoiser.py   # regenerasi dsp/.../assets/models/denoiser_v1.tflite
```

## Status jujur

- **Terverifikasi oleh CI**: compile + unit test + lint hijau; APK debug dihasilkan otomatis.
- **Belum terverifikasi**: perilaku runtime di perangkat nyata (Direct mode & reconnect MediaSession, MediaSession/notifikasi, kualitas AI Enhance pada musik asli, pemrosesan toolkit pada file besar, hasil efek DSP real-time). Ini perlu pengujian manual di perangkat Android fisik.

## Roadmap

- Model AI berkualitas lebih tinggi yang dilatih dari data musik asli.
- Lebih banyak knob kualitas WSOLA (prasetel kualitas di UI).
- Migrasi string UI ke resources (`strings.xml`) untuk dukungan multi-bahasa formal.
- Release build dengan signing.

## Lisensi

[Apache License 2.0](LICENSE) © 2026 soe1hom-arch
