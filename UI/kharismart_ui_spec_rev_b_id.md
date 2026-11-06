# KharisMart – Spesifikasi Layar UI (SRS Revisi B)

**Konvensi global**

- **Bahasa**: seluruh antarmuka produksi menggunakan Bahasa Indonesia (`NF04`). Semua teks dipusatkan di `strings.xml`.
- **Mata uang**: `Rp49.000` (pemisah ribuan titik, tanpa desimal).
- **Tanggal & waktu**: `dd/MM/yyyy HH:mm WIB` (contoh: `18/10/2026 10:42 WIB`).
- **Label peran** di UI: `admin` dan `kasir`.
- **Data contoh yang disinkronkan (Revisi B)**: Total utang belum lunas **Rp59.000** (Bu Hirah **Rp39.000** dari penjualan utang Rp49.000 dengan pembayaran Rp10.000; Pak Eko **Rp20.000** dari utang manual). Riwayat stok Minyak Goreng 1 L berurutan: 5 → restock +24 (29) → penyesuaian −2 (27, "Barang rusak") → penjualan (26).

---

## 1. Kerangka Aplikasi & Navigasi Global

### 1.1 Kerangka Aplikasi & Navigasi (`SRS-F01`)

- **Tata Letak Responsif**
  - **Mode Potret (Ponsel / Tablet Kecil)**: AppBar atas (tinggi 48–56 dp) dengan Bottom Navigation Bar permanen 5 item (tinggi 80 dp): *Beranda* (ikon rumah), *Pencatatan* (ikon kotak inventaris), *Tombol Pindai Tengah* (FAB bulat 56 dp, menonjol, warna cyan/biru), *Keuangan* (ikon dompet), dan *Riwayat* (ikon jam/struk).
  - **Mode Lanskap (Tablet Kasir 10 Inci)**: Navigation Rail samping (lebar 240 dp) di sisi kiri berisi header toko (*KharisMart* / *Toko Kharisma*), kartu pengguna, dan 8 menu vertikal: *Beranda*, *Kasir*, *Pencatatan*, *Keuangan*, *Riwayat*, *Pengguna*, *Pengaturan*, dan *Cadangan*.
- **Elemen Header Atas Permanen**
  - **Label Merek**: Teks monospace/tebal `KharisMart - Toko Kharisma`.
  - **Kapsul Sesi Aktif**: Badge pil operator yang masuk: `Kasir: [Nama] (kasir)` atau `Ibu Sumarti (admin)`.
  - **Aksi Kunci Cepat**: Tombol merah/outline berlabel `[Kunci]` atau ikon gembok yang langsung menghapus sesi di memori dan membuka layar PIN.
- **Pembatas Visibilitas Berdasarkan Peran**
  - Jika sesi aktif adalah `kasir`, rail/bar navigasi menyembunyikan: *Keuangan* (Laporan/Keuangan), *Pengguna*, *Pengaturan*, dan *Cadangan*.

---

## 2. Autentikasi & Kontrol Akses

### 2.1 Layar Kunci / Ganti Pengguna (`Screen 1`, `SRS-F01`)

```
+-------------------------------------------------------------+
|                          KharisMart                         |
|                   Toko Kharisma - Terkunci                  |
|                                                             |
|       +-------------------+       +-------------------+     |
|       |    Ibu Sumarti    |       |       Rizki       |     |
|       |       admin       |       |       kasir       |     |
|       +-------------------+       +-------------------+     |
|                                                             |
|                        Masukkan PIN                         |
|                          ( * * o o )                        |
|                                                             |
|                     [ 1 ]   [ 2 ]   [ 3 ]                   |
|                     [ 4 ]   [ 5 ]   [ 6 ]                   |
|                     [ 7 ]   [ 8 ]   [ 9 ]                   |
|                     [ < ]   [ 0 ]   [ OK ]                  |
|                                                             |
|     PIN salah 5 kali, tunggu 30 detik                       |
|     Kunci otomatis setelah 5 menit tidak digunakan          |
|                                                             |
|        Lupa PIN admin? Gunakan kode pemulihan               |
+-------------------------------------------------------------+
```

- **Header & Merek**
  - Judul aplikasi `KharisMart` (28 sp, Bold, Biru Utama) di tengah atas.
  - Sub-label `Toko Kharisma - Terkunci` (14 sp, Abu-abu Sedang).
- **Carousel Pilihan Profil Pengguna**
  - Deretan horizontal Material 3 Card (120 × 120 dp) untuk akun aktif dari tabel `users`:
    - Kartu 1: `Ibu Sumarti` dengan badge peran `admin` (container terisi).
    - Kartu 2: Nama kasir (mis. `Rizki`) dengan badge `kasir` (container outline).
  - Kartu terpilih memiliki garis tepi 2 dp berwarna dan ikon centang.
- **Tampilan Input PIN**
  - Teks `Masukkan PIN` (16 sp, Semi-Bold) di tengah.
  - Indikator bertopeng: baris 4–6 titik bulat (diameter 16 dp, jarak 8 dp). Titik kosong berwarna abu-abu netral; digit terisi beranimasi menjadi cakram putih/biru.
- **Keypad Numerik Kontras Tinggi**
  - Matriks 4 × 3 tombol bulat (64 × 64 dp, area sentuh ≥ 48 dp):
    - Baris 1: `[ 1 ]`, `[ 2 ]`, `[ 3 ]`
    - Baris 2: `[ 4 ]`, `[ 5 ]`, `[ 6 ]`
    - Baris 3: `[ 7 ]`, `[ 8 ]`, `[ 9 ]`
    - Baris 4: `[ < ]` (ikon hapus), `[ 0 ]`, `[ OK ]` (ikon centang)
- **Banner Keamanan & Penguncian**
  - Label peringatan: `PIN salah 5 kali, tunggu 30 detik`.
  - Status terkunci: banner merah mengambang setelah 5 kali PIN salah: `Terlalu banyak percobaan salah. Coba lagi dalam {n} detik.` Tombol keypad dinonaktifkan dan hitung mundur ditampilkan.
  - **Persistensi penguncian**: waktu penguncian disimpan di `EncryptedSharedPreferences` / DataStore sehingga menutup paksa aplikasi tidak dapat melewati jeda 30 detik.
- **Aksi Pemulihan PIN Darurat**
  - Tombol teks di bawah: `Lupa PIN admin? Gunakan kode pemulihan`.
  - Membuka AlertDialog dengan kolom alfanumerik (`Kode Pemulihan: RECOVERY-XXXX`), kolom numerik `PIN Baru`, dan tombol `Reset PIN`.
- **Catatan implementasi keamanan**
  - PIN di-hash dengan PBKDF2 minimal 100.000 iterasi.
  - Perlindungan utama di perangkat adalah isolasi sandbox Android (`/data/data/com.byteall.kharismart/`).

---

## 3. Kasir & Pembayaran (Modul Kasir)

### 3.1 Terminal Kasir / Keranjang & Pembayaran (`Screen 6`, `SRS-F05`, `F06`, `F07`, `F08`)

```
+----------------------------------------------------------------------------------------------------+
| [ Pindai atau ketik barcode...                                    ] [ Ikon Kamera ]                |
| [ Semua ] [ Sembako ] [ Jajanan ] [ Rokok ] [ Kebutuhan Harian ]                                   |
+--------------------------------------------------+-------------------------------------------------+
| KATALOG PRODUK (PANEL KIRI)                      | KERANJANG & PEMBAYARAN (PANEL KANAN)            |
| +--------------+ +--------------+ +------------+ | Keranjang:                                      |
| | [Foto/Ikon]  | | [Foto/Ikon]  | | [Foto]     | | 1. Indomie Goreng      x4             14.000    |
| | Indomie G.   | | Minyak 1L    | | Beras 5kg  | |    @3.500                        [-] [+] [Hapus]|
| | Rp3.500      | | Rp18.000     | | Rp68.000   | | 2. Minyak Goreng 1L    x1             18.000    |
| | [Stok 36]    | | [Stok 26]    | | [Habis]    | |    @18.000                       [-] [+] [Hapus]|
| +--------------+ +--------------+ +------------+ | 3. Gula Pasir 1kg      x1             17.000    |
| | Wafer Cokel. | | Keripik Sing | | Rokok 12   | |    @17.000                       [-] [+] [Hapus]|
| | Rp2.000      | | Rp5.000      | | Rp25.000   | |-------------------------------------------------|
| | [Stok 4:Tipis]| | [Stok 12]   | | [Habis]    | | TOTAL                                  Rp49.000 |
| +--------------+ +--------------+ +------------+ | Metode:  [ Tunai ]    [ QRIS ]    [ Utang ]     |
|                                                  | Dibayar: Rp50.000              Kembali: Rp1.000 |
|                                                  | +---------------------------------------------+ |
|                                                  | | [ SELESAI (Hijau Utama) ]                   | |
|                                                  | +---------------------------------------------+ |
+--------------------------------------------------+-------------------------------------------------+
```

- **Kontrol Input Bar Atas**
  - **Kolom Barcode**: `OutlinedTextField` permanen yang otomatis fokus saat layar dibuka. Placeholder `Pindai atau ketik barcode (alat pemindai)` dengan ikon barcode di depan. Menangani tombol Enter dari pemindai USB/Bluetooth.
  - **Tombol Kamera (cadangan)**: Tombol ikon kamera di ujung kolom pencarian.
  - **Carousel Chip Kategori**: Baris chip yang dapat digeser horizontal: `[Semua]`, `[Sembako]`, `[Jajanan]`, `[Rokok]`, `[Kebutuhan Harian]`.
- **Panel Kiri: Grid Katalog Produk**
  - Grid 3 kolom yang dapat digulir berisi Kartu Produk (140 × 160 dp):
    - Atas: Thumbnail foto produk (120 × 80 dp) atau ilustrasi kategori bawaan.
    - Tengah: Nama Produk (14 sp, Bold, maks. 2 baris) dan Harga Jual Satuan (`Rp3.500`).
    - Bawah: Badge Status Stok:
      - `Stok 36` (Aman: pil hijau solid, teks putih).
      - `Stok 4` (Menipis: pil kuning/amber solid, teks gelap).
      - `Habis` (Habis: container merah solid, kartu dinonaktifkan dan keabu-abuan).
    - Perilaku ketuk: menambah 1 unit ke keranjang jika produk aktif dan stok > 0.
- **Panel Kanan: Keranjang Aktif**
  - Header `Keranjang` dengan indikator jumlah total item.
  - Daftar baris item keranjang yang dapat digulir:
    - Judul baris: Nama Produk dan subtotal baris (`14.000`) rata kanan.
    - Sub-baris: Teks harga satuan (`jml 4 @ Rp3.500`) beserta kontrol stepper:
      - Tombol kurang `[-]` (merah/outline, 32 × 32 dp).
      - Kolom jumlah (lebar 40 dp, bilangan bulat di tengah).
      - Tombol tambah `[+]` (hijau/terisi, 32 × 32 dp, nonaktif jika jumlah mencapai batas stok).
      - Tombol ikon hapus (ikon tempat sampah).
- **Konfigurasi Pembayaran & Pemilih Metode**
  - **Blok Ringkasan Total**: Label besar `Total` dan angka tebal `Rp49.000` (24 sp, Bold, Biru Utama).
  - **Kontrol Segmen Metode Pembayaran**: 3 tombol toggle saling eksklusif: `[ Tunai ]` | `[ QRIS ]` | `[ Utang ]`.
  - **Panel Detail Pembayaran Dinamis**
    - **Saat `Tunai` aktif**
      - Kolom `Uang Diterima` yang awalnya terisi sebesar total.
      - Chip nominal cepat: `[Uang Pas]`, `[+Rp10.000]`, `[+Rp20.000]`, `[+Rp50.000]`.
      - Label kembalian: `Kembalian: Rp1.000` (hijau tebal; tampil error merah jika uang diterima < total).
    - **Saat `QRIS` aktif**
      - Kartu bitmap yang menampilkan kode QRIS statis milik pemilik.
      - Info bank & pemilik: `QRIS Toko Kharisma - [Bank BRI / Sumarti]`.
      - Banner instruksi: `Pastikan dana Rp{total} sudah masuk di mobile banking sebelum konfirmasi.`
      - Checkbox konfirmasi wajib: `[ ] Dana sudah diterima (dikonfirmasi kasir)`.
    - **Saat `Utang` aktif**
      - Kolom wajib `Nama Pelanggan`: input teks dengan dropdown autocomplete berisi pengutang sebelumnya (mis. `Bu Hirah`, `Pak Eko`, `Bu Sari`) dan opsi `+ Nama baru...`.
      - **Normalisasi nama**: nama di-`trim()` dan diformat Title Case secara otomatis (mis. `"bu hirah "` menjadi `"Bu Hirah"`). Sumber autocomplete: `SELECT DISTINCT customer_name FROM debts ORDER BY customer_name ASC`. Kolom `debts.customer_name` memakai `COLLATE NOCASE` agar nama dengan huruf besar/kecil berbeda tidak terpecah.
      - Kolom opsional `Jatuh Tempo / Catatan`: input teks (mis. `Bayar tanggal 30/11/2026` atau `Tempo 1 minggu`).
      - Label rincian: `Dibayar: Rp0`, `Kembalian: Rp0`, dan `Sisa: Rp49.000` (merah tebal).
- **Tombol Aksi Pembayaran Utama**
  - Tombol besar kontras tinggi berlabel `Selesai` (tinggi minimum 56 dp, lebar penuh, Hijau Utama).
  - Status nonaktif: redup jika keranjang kosong, uang tunai < total, checkbox QRIS belum dicentang, atau nama pelanggan utang kosong.
- **Dialog Konfirmasi Selesaikan Transaksi (`Modal`)**
  - **Konfirmasi Tunai / QRIS**
    - Teks: `Selesaikan transaksi ini? Total Rp{total}, dibayar Rp{dibayar}, kembalian Rp{kembalian}. Transaksi tidak dapat diubah setelah disimpan.`
    - Tombol: `[Ya, Selesaikan]` (hijau terisi) dan `[Batal]` (tombol teks).
  - **Konfirmasi Utang**
    - Teks: `Selesaikan penjualan utang untuk {Nama Pelanggan}? Total Rp{total}. Stok akan dikurangi dan catatan utang akan dibuat.`
    - Tombol: `[Ya, Selesaikan]` dan `[Batal]`.
  - Tombol konfirmasi langsung dinonaktifkan setelah diketuk untuk mencegah penulisan ganda.
- **Pembekuan harga & modal (BR-06)**: saat transaksi disimpan, `price` dan `cost_price` disalin ke `transaction_items`, dan `transactions.total_cost` serta `transactions.gross_profit` disimpan permanen. Perubahan harga modal di katalog tidak mengubah laporan laba lama.

### 3.2 Tampilan Pemindai Barcode Kamera (`Screen 5`, `SRS-F05`)

- Pratinjau CameraX layar penuh dengan vignette gelap di tepi.
- Bingkai pemindai: persegi panjang bersudut bulat (240 × 160 dp) dengan sudut putih dan garis laser merah berdenyut horizontal.
- Banner instruksi di atas: `Arahkan kamera ke barcode` (teks putih pada pil hitam semi-transparan).
- Kartu deteksi waktu nyata di bawah: kartu putih yang menampilkan `8999999123456 -> Indomie Goreng Rp3.500` segera setelah terbaca.
- Tombol aksi: tombol hijau `Selesai` untuk menutup kamera dan langsung kembali ke keranjang.

### 3.3 Layar Struk Digital (`Screen 7`, `SRS-F09`)

```
+-------------------------------------------------------------+
|                           STRUK                             |
|                                                             |
|                       TOKO KHARISMA                         |
|                 [alamat toko]                               |
|                 ----------------------                      |
|  No : TRX-20261018-003                                      |
|  18/10/2026 10:42 WIB   Kasir: Ibu Sumarti                  |
|  Pelanggan : Bu Hirah                                       |
|  ---------------------------------------------------------  |
|  4 x Indomie Goreng     14.000                              |
|  1 x Minyak Goreng 1 L  18.000                              |
|  1 x Gula Pasir 1 kg    17.000                              |
|  ---------------------------------------------------------  |
|  TOTAL                  49.000                              |
|  Metode : Utang                                             |
|  Dibayar                0                                   |
|  Sisa Utang             49.000                              |
|  Catatan : Bayar tanggal 30/11/2026                         |
|  ---------------------------------------------------------  |
|  Terima kasih                                               |
|                                                             |
|          [ Bagikan (Biru) ]     [ Cetak / PDF (Hijau) ]     |
+-------------------------------------------------------------+
```

- **Kontainer Simulasi Kertas Struk**: Permukaan putih dengan bayangan dan tepi atas/bawah bergerigi atau putus-putus seperti sobekan kertas.
- **Tata Letak Monospace**
  - Header Toko: `TOKO KHARISMA` (Bold) di tengah beserta alamat toko.
  - Blok Meta: garis pemisah `----------------------` di tengah.
  - Baris Kunci-Nilai: rata kiri `No : TRX-yyyymmdd-nnn` (ditambah tag `[SALINAN]` jika dibuka ulang dari riwayat), tanggal (`18/10/2026 10:42 WIB`), dan nama operator (`Kasir: Ibu Sumarti`).
  - Penamaan `Kasir`: siapa pun yang mengoperasikan konter disebut "Kasir" pada struk pelanggan, termasuk saat tingkat otorisasinya admin (Ibu Sumarti).
  - Baris Identitas Utang: `Pelanggan : {Nama Pelanggan}` (hanya untuk penjualan utang).
  - Baris Item: `[Jml] x [Nama Produk]` rata kiri, `[Subtotal]` rata kanan.
  - Pemisah Total: garis putus-putus yang memisahkan item dari ringkasan.
  - Ringkasan Moneter:
    - `TOTAL` dan angka tebal (`49.000`).
    - `Metode : Tunai` / `QRIS` / `Utang`.
    - `Dibayar` (`50.000`, atau `0` untuk utang).
    - `Kembalian` (`1.000`) ATAU `Sisa Utang : 49.000` (untuk penjualan utang).
    - `Catatan : {Catatan Jatuh Tempo}` (untuk penjualan utang, jika diisi).
  - Footer: ucapan yang dapat diatur dari pengaturan, rata tengah (mis. `Terima kasih, sampai jumpa lagi!`).
- **Dock Aksi**
  - Tombol 1: `Bagikan` (biru tonal, membuka Android Share Sheet lewat `Intent.ACTION_SEND` berisi struk teks biasa).
  - Tombol 2: `Cetak / PDF` (hijau utama terisi, membuka pratinjau Android `PrintManager` untuk mencetak di printer thermal atau ekspor PDF).

---

## 4. Inventaris, Katalog & Tata Kelola Stok (Modul Admin)

### 4.1 Pencatatan Barang (`Screen 8`, `SRS-F04`, `F12`)

- **Header & Akordeon Kategori**
  - Judul layar `PENCATATAN BARANG` dengan tombol `Unduh Data` (ekspor CSV) di kanan atas.
  - Kontainer Kategori (`Sembako`, `Jajanan`, `Rokok`, `Kebutuhan Harian`): tiap bagian punya baris header berisi judul kategori, badge jumlah barang, dan tombol `+ Tambah Barang`.
- **Tabel Data Produk per Kategori**
  - Kolom:
    - `Nama`: Nama produk (14 sp, bold) dengan avatar thumbnail opsional.
    - `Harga Jual`: Harga eceran satuan dalam Rupiah (`3.500`).
    - `Harga Modal`: Harga pokok satuan (`3.000`) — **hanya admin, kolom dihapus untuk peran kasir**.
    - `Stok`: Persediaan saat ini berupa bilangan bulat (`24`).
    - `Status`: Badge pil berwarna: `Aman` (hijau), `Menipis` (kuning), `Habis` (merah).
    - `Aksi`: Ikon aksi baris: Ubah (ikon pensil, hijau) dan Nonaktifkan/Hapus (ikon tempat sampah, merah).
  - Pembatasan Tampilan Kasir: kasir hanya melihat Nama, Harga Jual, Stok, dan badge Status; tombol aksi dan harga modal disembunyikan.

### 4.2 Layar Formulir Tambah / Ubah Barang (`Screen 9`, `SRS-F04`)

- **Input Formulir**
  - Kolom Barcode dengan tombol terpadu `Pindai Barcode` yang membuka pemindai kamera.
  - Kolom teks `Nama Produk` (`Indomie Goreng`).
  - Dropdown `Kategori` (Exposed Dropdown Menu) dengan kategori yang sudah tersedia.
  - Kolom numerik `Harga Jual` berawalan `Rp`.
  - Kolom numerik `Harga Modal` berawalan `Rp`.
  - Kolom bilangan bulat `Stok Awal`.
  - Kolom bilangan bulat `Stok Minimum` (teks bantuan: `kosongkan = default 5`).
  - Pemilih `Foto Produk`: kartu yang dapat diketuk berisi thumbnail gambar, tombol `[Pilih Gambar]`, dan ikon hapus.
- **Banner Peringatan Responsif**
  - Teks merah dinamis tepat di bawah kolom harga: `Peringatan: harga jual lebih rendah dari harga modal.` Muncul seketika jika `Harga Jual < Harga Modal`.
- **Aksi Formulir**
  - Tombol utama `Simpan` (mengirim formulir, memvalidasi harga positif bukan nol, dan menulis pergerakan stok awal).
  - Tombol teks `Batal`.

### 4.3 Layar Kelola Kategori (`Screen 10`, `SRS-F03`)

- Tampilan daftar sederhana semua kategori produk (`Sembako`, `Jajanan`, `Rokok`, `Kebutuhan Harian`).
- Setiap baris menampilkan nama kategori, jumlah produk aktif, dan ikon ubah/ganti nama.
- Kartu Tambah Kategori di atas: kolom teks nama kategori baru dan tombol `Tambah Kategori`.
- Pelindung Penonaktifan: baris kategori memiliki toggle aktif/nonaktif. Menonaktifkan kategori yang masih punya produk aktif memunculkan snackbar error: `Kategori ini masih memiliki produk aktif.`

### 4.4 Layar Stok Barang (`Screen 11`, `SRS-F11`)

Ilustrasi menampilkan keadaan setelah seluruh rangkaian contoh di bawah.

```
+-------------------------------------------------------------+
| STOK BARANG                                                 |
|                                                             |
| Minyak Goreng 1 L                                           |
| Stok saat ini: 26 | Status: Aman                            |
|                                                             |
| [ Restock (barang masuk) ]                                  |
| Jumlah:                  [                          ]       |
| Harga modal satuan (Rp): [                          ]       |
| [x] Perbarui harga modal sesuai harga modal satuan ini      |
|                                         [ Simpan Restock ]  |
|                                                             |
| [ Penyesuaian stok (hitung fisik) ]                         |
| Stok hasil hitung: [                                ]       |
| Alasan (wajib):    [                                ]       |
| Selisih yang akan dicatat: -                                |
|                                      [ Simpan Penyesuaian ] |
|                                                             |
| Riwayat stok                                                |
| 18/10 10:42  Penjualan TRX-...003            -1          26 |
| 18/10 09:30  Penyesuaian: Barang rusak       -2          27 |
| 18/10 09:15  Restock                        +24          29 |
| 17/10 17:30  Penjualan TRX-...021            -2           5 |
+-------------------------------------------------------------+
```

- **Banner Produk Terpilih**: Judul SKU aktif, jumlah stok saat ini (`Stok saat ini: 26`), dan badge status berwarna (`Status: Aman`).
- **Bagian 1: Restock (Barang Masuk)**
  - Input 1: `Jumlah` yang ditambahkan (bilangan bulat positif).
  - Input 2: `Harga modal satuan (Rp)`.
  - Checkbox: `[x] Perbarui harga modal sesuai harga modal satuan ini` (menimpa harga modal katalog dengan harga modal restock terbaru, BR-11).
  - Tombol utama: `Simpan Restock` (menambah stok dan membuat pergerakan restock).
  - *Contoh pada riwayat*: dari stok 5, restock jumlah 24 → stok 29.
- **Bagian 2: Penyesuaian Stok (Stock Opname)**
  - Input 1: `Stok hasil hitung` (jumlah fisik sebenarnya).
  - Indikator Selisih Waktu Nyata: label tebal merah/hijau yang menghitung Δ: `Selisih yang akan dicatat: -2`.
  - Input 2: `Alasan (wajib)` (mis. `Barang rusak`, `Kedaluwarsa`, `Selisih hitung`, `Barang retur dari TRX-XXX`).
  - Tombol: `Simpan Penyesuaian` (nonaktif jika stok hasil hitung sama dengan stok tercatat atau alasan kosong).
  - *Contoh pada riwayat*: tercatat 29, hasil hitung 27, selisih −2, alasan `Barang rusak` → stok 27. Penjualan berikutnya (−1) → stok 26.
- **Bagian 3: Riwayat Pergerakan Stok (Audit)**
  - Tabel log kronologis (terbaru di atas):
    - Kolom 1: Tanggal & waktu.
    - Kolom 2: Tag jenis kejadian (`Penjualan TRX-...003`, `Restock`, `Penyesuaian`, `Stok Awal`).
    - Kolom 3: Selisih bertanda (`-1`, `+24`, `-2`) merah/hijau.
    - Kolom 4: Stok setelah kejadian (`26`, `27`, `29`, `5`).

### 4.5 Daftar Stok Menipis (`Screen 12`, `SRS-F12`)

- Header ringkasan peringatan: `{n} produk menipis atau habis`.
- Kartu item yang dapat digulir, diurutkan naik berdasarkan stok tersedia (stok nol disematkan di atas).
- Setiap kartu menampilkan nama produk, thumbnail, kategori, stok saat ini, dan batas stok minimum.
- Pintasan Admin: tombol `Restock` di setiap kartu yang membuka modal restock dengan produk terisi otomatis.

---

## 5. Piutang (Utang) & Buku Arus Kas

### 5.1 Layar Catatan Utang (`Screen 13`, `SRS-F13`)

- **Header & Kartu Total Piutang**
  - Judul layar `CATATAN UTANG` dengan tombol `+ Tambah Catatan` untuk pencatatan utang manual.
  - Banner ringkasan merah menonjol: `Total belum lunas: Rp59.000`.
- **Kontrol Pencarian & Filter**
  - Kolom pencarian berdasarkan nama pengutang.
  - Chip filter: `[ Semua ]`, `[ Belum Lunas ]`, `[ Lunas ]`.
- **Tabel / Daftar Kartu Catatan Utang**
  - Kolom:
    - `Nama`: Nama pengutang (mis. `Bu Hirah`, `Pak Eko`).
    - `Total Utang`: Total utang awal (`49.000`).
    - `Sisa`: Sisa belum dibayar dalam huruf tebal (`39.000`).
    - `Status`: Pil status: `Belum lunas` (merah solid) atau `Lunas` (hijau solid).
    - `Tag Asal`: Sub-baris metadata di bawah nama pelanggan:
      - `Asal: Penjualan Kasir (TRX-yyyymmdd-nnn)`
      - `Asal: Catatan Manual`
    - `Aksi`: Ikon aksi: Bayar (ikon dolar/dompet), Ubah, Hapus.
  - Contoh data: Bu Hirah (`Asal: Penjualan Kasir (TRX-20261018-003)`, Total `49.000`, Sisa `39.000`) dan Pak Eko (`Asal: Catatan Manual`, Sisa `20.000`).
- **Aturan Proteksi Imutabilitas & Rekonsiliasi**
  - Utang berasal dari penjualan kasir:
    - `total_amount` **terkunci** untuk menjaga jejak audit transaksi.
    - **Admin** dapat mengubah **Nama Pelanggan** dan **Catatan** saja (untuk memperbaiki kasus kasir salah memilih pengutang). Ikon ubah hanya tampil untuk admin dan hanya membuka dua kolom tersebut.
    - Ikon hapus tidak pernah ditampilkan; mengetuknya (jika dipicu) memunculkan pesan penjaga.
    - Peran kasir tidak melihat ikon ubah maupun hapus.
  - Utang manual: aksi hapus hanya ditampilkan untuk Admin jika `paid_amount == 0`.

### 5.2 Layar Pembayaran Utang / Bayar Utang (`Screen 14`, `SRS-F13`)

- **Kartu Ringkasan Pengutang**
  - Nama Pelanggan (`Bu Hirah`).
  - Indikator asal dengan pintasan teks yang dapat diketuk: `Asal: Penjualan Kasir (TRX-20261018-003)  [Lihat Struk]` yang membuka ulang struk penjualan asli.
  - Ringkasan total: `Total utang: Rp49.000`, `Dibayar: Rp10.000`, dan sisa merah tebal `Sisa: Rp39.000`.
  - Rincian Barang: jika berasal dari kasir, tampilkan barang yang dibeli (`Indomie Goreng x4`, `Minyak Goreng 1L x1`, `Gula Pasir 1kg x1`).
- **Formulir Pembayaran**
  - Kolom numerik `Jumlah pembayaran (Rp)` terisi otomatis sebesar sisa utang.
  - Toggle metode: `[ Tunai ]` | `[ QRIS ]`.
  - Kolom opsional `Catatan` (mis. `Dibayar tunai di konter`).
  - Tombol kirim: `Simpan Pembayaran` (nonaktif jika jumlah > sisa utang atau jumlah ≤ 0).
- **Riwayat Pembayaran (Audit)**
  - Daftar cicilan sebelumnya: tanggal, tag metode pembayaran, dan jumlah (mis. `18/10/2026  Dibayar tunai  Rp10.000`).

### 5.3 Catatan Kas Manual (`Screen 18`, `SRS-F14`)

- **Kontainer Formulir Tambah Catatan**
  - Kolom pemilih `Tanggal`, default hari ini.
  - Toggle jenis: `[ Pengeluaran ]` (outline merah) | `[ Pemasukan ]` (outline hijau).
  - Dropdown / input teks `Kategori` (mis. `Listrik`, `Sewa Lapak`, `Kantong Plastik`, `Gaji`, `Kemasan`).
  - Input numerik `Jumlah (Rp)`.
  - Kolom `Keterangan`.
  - Tombol: `Simpan`.
- **Tabel Buku Kas**
  - Kolom yang dapat diurutkan: `Tanggal`, `Kategori`, `Jumlah` (hijau bertanda `+100.000` untuk pemasukan, merah bertanda `-10.000` untuk pengeluaran), dan `Aksi` (ikon Ubah, Hapus).
  - Aturan isolasi: pendapatan penjualan, pembayaran utang, dan restock pemasok tidak ditampilkan di sini (mencegah hitung ganda).

### 5.4 Prosedur Rekonsiliasi Tanpa Void (Revisi B)

Transaksi bersifat final: tidak ada retur, void, diskon, atau keranjang ditunda (SRS Rev B bagian 1.2 dan 2.4). Koreksi dilakukan oleh **admin** sebagai berikut:

- **Salah memilih pengutang pada penjualan utang**: admin membuka Catatan Utang → ikon Ubah → perbaiki `Nama Pelanggan` dan/atau `Catatan`. Total utang tidak berubah.
- **Barang dikembalikan secara fisik**:
  1. Catat stok masuk lewat **Stok Barang → Penyesuaian Stok** dengan selisih positif (+Jumlah) dan alasan `Barang retur dari TRX-XXX`.
  2. Catat entri penyeimbang di **Catatan Kas** (tabel `finance`).
- **Selisih stok lain** (rusak, kedaluwarsa, salah hitung): Penyesuaian Stok dengan alasan wajib.

---

## 6. Analitik, Pelaporan & Dasbor

### 6.1 Dasbor Utama / Beranda (`Screen 3`, `SRS-F16`)

Wireframe di bawah adalah tampilan **admin**; kartu kinerja tidak ditampilkan untuk kasir.

```
+-------------------------------------------------------------+
| KharisMart                                       100%       |
|                                                             |
| [ Cari Barang...                                          ] |
|                                                             |
| +-----------------------------+ +-------------------------+ |
| | Total Pendapatan Hari Ini   | | Total Keuntungan Hari Ini| |
| | Rp 1.000.000                | | Rp 500.000              | |
| | ^ 8% dari kemarin           | | ^ 2% dari kemarin       | |
| +-----------------------------+ +-------------------------+ |
|                                                             |
| Stok Menipis                                   Lihat Semua  |
| ----------------------------------------------------------- |
| Beras 5 kg                                            0 pcs |
| Rokok Filter 12                                       0 pcs |
| Wafer Cokelat                                         4 pcs |
|                                                             |
| Catatan Utang                                  Lihat Semua  |
| ----------------------------------------------------------- |
| Bu Hirah                                          Rp 39.000 |
| Pak Eko                                           Rp 20.000 |
+-------------------------------------------------------------+
```

- **Header Atas & Kotak Pencarian**
  - Kolom pencarian besar `Cari Barang` yang membuka katalog saat difokuskan.
- **Kartu Metrik Kinerja Admin** (disembunyikan dari peran kasir)
  - **Kartu 1 (Omzet Hari Ini)**: Judul `Total Pendapatan Hari Ini`, angka tebal `Rp1.000.000`, dan badge persentase hijau `^ 8% dari kemarin`.
  - **Kartu 2 (Laba Kotor Hari Ini)**: Judul `Total Keuntungan Hari Ini`, angka tebal `Rp500.000`, dan badge persentase hijau `^ 2% dari kemarin`.
  - **Kartu Peringatan Cadangan**: Banner amber yang muncul jika tidak ada pencadangan selama > 3 hari: `Belum ada pencadangan selama {n} hari. Cadangkan sekarang?`
- **Bagian Snapshot Stok Kritis**
  - Judul `Stok Menipis` dengan tautan `Lihat Semua` rata kanan.
  - Mini-tabel 3 SKU paling kritis dengan sisa stok waktu nyata (`Beras 5 kg: 0 pcs`, `Rokok Filter 12: 0 pcs`, `Wafer Cokelat: 4 pcs`).
- **Bagian Snapshot Piutang Aktif**
  - Judul `Catatan Utang` dengan tautan `Lihat Semua` rata kanan.
  - Mini-tabel 2–3 pengutang aktif dan sisa utang (`Bu Hirah: Rp39.000`, `Pak Eko: Rp20.000`).

### 6.2 Ringkasan Keuangan & Cincin Target / Keuangan (`Screen 16`, `SRS-F15`, `BR-15`)

- **Pengukur Pencapaian Target**
  - Baris 3 Cincin Progres Melingkar yang digambar lewat Canvas:
    - **Cincin Target Harian**: menampilkan `35%` di tengah, dengan sub-label `Rp175.000` dan `Harian`.
    - **Cincin Target Mingguan**: menampilkan `60%`, dengan sub-label `Rp900.000` dan `Mingguan`.
    - **Cincin Target Bulanan**: menampilkan `85%`, dengan sub-label `Rp3.400.000` dan `Bulanan`.
  - Gaya visual: dibatasi 100%, jalur latar abu-abu, isian warna utama/aksen solid, atau `—` jika target belum diatur.
- **Ubin Kinerja Kategori**
  - Grid 2 × 2 kartu berwarna besar: `Sembako`, `Jajanan`, `Rokok`, `Kebutuhan Harian`.
  - Mengetuk kategori menampilkan omzet, margin kotor, dan volume periode tersebut.

### 6.3 Laporan Periode Rinci / Laporan (`Screen 17`, `SRS-F15`, `BR-14`)

- **Kontrol Pemilihan Periode**
  - Chip filter: `[ Hari ]`, `[ Minggu ]`, `[ Bulan ]`, `[ Rentang Kustom ]` dengan pemilih tanggal mulai/akhir.
- **Grid Ringkasan KPI (6 Kartu)**
  1. `Pendapatan`: `Rp128.000` (penjualan kotor dari Tunai, QRIS, dan Utang).
  2. `Laba Kotor`: `Rp14.000` (Pendapatan − Total Harga Pokok Penjualan).
  3. `Pengeluaran`: `Rp10.000` (biaya operasional dari catatan kas manual).
  4. `Laba Bersih`: `Rp4.000` (Laba Kotor + Pemasukan Lain − Pengeluaran).
  5. `Saldo Kas`: `Rp49.000` (uang tunai & dana bank yang dimiliki).
  6. `Utang Belum Lunas`: `Rp59.000` (piutang pelanggan yang belum tertagih, kumulatif).
- **Kartu Rekonsiliasi Saldo Kas**
  - Rincian perhitungan:
    - `Penjualan diterima tunai`: `+ Rp49.000`
    - `Penjualan diterima via QRIS`: `+ Rp30.000`
    - `Pembayaran utang diterima`: `+ Rp10.000`
    - `Biaya operasional`: `- Rp10.000`
    - `Pembelian stok (restock)`: `- Rp30.000`
    - Baris total: `Posisi Kas Bersih = Rp49.000`
- **Tabel Kinerja Penjualan & Margin Produk**
  - Kolom yang dapat diurutkan: `Nama Produk`, `Jumlah Terjual`, `Pendapatan (Subtotal)`, `Laba`.
- **Bar Ekspor Mengambang**
  - Dua tombol menonjol di bawah: `[ Bagikan PDF ]` (membuka dialog Cetak Android) dan `[ Ekspor CSV ]` (membuat berkas spreadsheet RFC 4180).

### 6.4 Layar Riwayat Pembelian (`Screen 15`, `SRS-F10`)

- **Chip Filter Periode & Metode**
  - Filter periode: `[ Hari Ini ]`, `[ Minggu Ini ]`, `[ Bulan Ini ]`, `[ Rentang Tanggal ]` (kasir dikunci hanya ke `Hari Ini`).
  - Filter metode pembayaran: `[ Semua ]`, `[ Tunai ]`, `[ QRIS ]`, `[ Utang ]`.
- **Kartu Daftar Transaksi**
  - Header Kartu: banner hijau/netral berisi waktu (`18/10/2026 10:42`), nomor struk (`TRX-20261018-003`), tag metode pembayaran (`Tunai`, `QRIS`, atau `Utang - Bu Hirah`), dan total (`Rp49.000`).
  - Isi Kartu: daftar item monospace (`Indomie Goreng x4`, `Minyak Goreng 1 L x1`, `Gula Pasir 1 kg x1`).
  - Footer Kartu: nama operator dan tombol `Lihat Struk` untuk membuka struk digital yang dapat dicetak.

---

## 7. Administrasi, Konfigurasi & Pemulihan Bencana

### 7.1 Layar Manajemen Pengguna / Pengguna (`Screen 19`, `SRS-F02`)

- Judul header `PENGGUNA` dan tombol menonjol `+ Tambah Staf`.
- **Tabel Akun Pengguna**
  - Kolom: `Nama`, `Nama Pengguna`, `Peran` (`admin` badge hijau, `kasir` badge biru), `Status` (saklar `Aktif` atau `Nonaktif`).
- **Kontainer Formulir Tambah / Ubah Pengguna**
  - Input: `Nama Lengkap`, `Nama Pengguna`, `PIN Awal (4–6 digit)`, `Peran` (dropdown).
  - Tombol aksi: `Simpan Pengguna`, `Reset PIN`, dan `Batal`.
  - Peringatan Proteksi: teks merah di bawah: `Admin aktif terakhir tidak dapat dinonaktifkan.`

### 7.2 Layar Pengaturan (`Screen 20`, `SRS-F17`)

- Tata letak formulir dibagi menjadi kartu fungsional:
  - **Kartu Identitas Toko**
    - `Nama toko`: kolom teks (`Toko Kharisma`).
    - `Alamat`: kolom teks multibaris.
    - `Catatan kaki struk`: kolom teks (`Terima kasih`).
  - **Kartu Parameter Operasional**
    - `Stok minimum default`: input bilangan bulat (default `5`).
    - `Kunci otomatis setelah (menit)`: dropdown (1, 2, 5, 10, 15, 30, 60 menit).
  - **Kartu Konfigurasi Akun QRIS**
    - `Bank / aplikasi m-banking QRIS`: kolom teks (`BRI`, `BCA`, dll.).
    - `Nama pemilik rekening QRIS`: kolom teks (`Sumarti`).
    - `Gambar QRIS`: pemilih gambar dengan thumbnail bitmap aktif dan tombol `[Unggah / Ganti Gambar]`.
  - **Kartu Target Pendapatan**
    - `Target harian (Rp)`: input bilangan bulat (`500.000`).
    - `Target mingguan (Rp)`: input bilangan bulat (`1.500.000`).
    - `Target bulanan (Rp)`: input bilangan bulat (`4.000.000`).
- **Dock Aksi Bawah**
  - Tombol utama: `Simpan` (hijau).
  - Tombol utilitas pengguna: `Ubah PIN` (membuka modal yang meminta PIN saat ini) dan `Kunci Sekarang` (merah, langsung keluar).

### 7.3 Layar Pencadangan & Pemulihan Bencana (`Screen 21`, `SRS-F18`)

- **Kartu Status Cadangan**
  - Label status: `Cadangan terakhir: hari ini 20:00 (Berhasil)`.
  - Arsip target: `Berkas: kharismart_2026-10-18_2000.zip`.
  - Indikator lokasi: `Folder: Flashdisk USB / KharismartBackup`.
  - Status penjadwalan: `Cadangan otomatis harian: AKTIF (menyimpan 30 terakhir)`.
- **Aksi Direktori & Cadangan**
  - Tombol 1: `Cadangkan Sekarang` (hijau solid, membuat `.zip` berisi database SQLite, foto, dan manifes versi).
  - Tombol 2: `Pilih Folder Cadangan` (biru solid, membuka pemilih pohon Android Storage Access Framework). Folder dapat berupa flashdisk USB maupun folder penyimpanan awan seperti Google Drive.
  - **Catatan keamanan** (tepat di bawah pemilih folder):
    `Catatan: Pastikan akun Google Drive atau media penyimpanan Anda aman. File cadangan memuat data transaksi dan catatan utang toko.`
- **Kartu Pemulihan / Restore**
  - Kotak Peringatan Menonjol (container amber muda, garis tepi merah):
    `Pemulihan akan mengganti SEMUA data saat ini. Berkas yang dipilih diperiksa terlebih dahulu; jika rusak atau berasal dari aplikasi lain, tidak ada yang berubah. Salinan pengaman data saat ini dibuat lebih dulu.`
  - Tombol aksi: `Pilih Berkas dan Pulihkan` (amber/cokelat solid).
  - Memulihkan memunculkan modal konfirmasi: `Pemulihan akan mengganti SEMUA data saat ini dengan data dalam {berkas} (tanggal {tanggal}). Lanjutkan?` dengan tombol `[Ya, Pulihkan]` dan `[Batal]`.
- Catatan risiko yang diterima (SRS bagian 2.4 dan 3.6): berkas SQLite dan zip tidak dienkripsi karena tablet konter hanya dioperasikan anggota keluarga.

---

## 8. Ringkasan Pesan Sistem & Modal Notifikasi

| Kondisi Pemicu | Pesan yang Ditampilkan | Jenis Komponen UI |
| --- | --- | --- |
| **Jumlah Stepper Tidak Valid** | `"Jumlah harus berupa bilangan bulat lebih dari 0."` | Teks error inline / Toast |
| **Melebihi Stok Tersedia** | `"Stok {produk} hanya {n}. Kurangi jumlah atau minta admin memperbarui stok."` | SnackBar / Banner peringatan |
| **Barcode Belum Terdaftar (Kasir)** | `"Barcode {kode} belum terdaftar. Minta admin untuk mendaftarkan produk ini."` | Dialog Modal Tengah |
| **Barcode Belum Terdaftar (Admin)** | `"Barcode {kode} belum terdaftar. Daftarkan produk ini?"` | Dialog dengan pintasan `Daftarkan` |
| **Nama Pengutang Kosong** | `"Nama pelanggan wajib diisi untuk penjualan utang."` | Error inline pada kolom teks |
| **Konfirmasi QRIS Belum Dicentang** | `"Konfirmasi bahwa dana sudah diterima sebelum menyelesaikan transaksi."` | Animasi getar + peringatan merah |
| **Gagal Menulis Transaksi** | `"Transaksi gagal disimpan. Tidak ada data yang tersimpan, silakan coba lagi."` | AlertDialog prioritas tinggi |
| **Checkout Berhasil** | `"Transaksi berhasil disimpan. No. Struk {no}."` | SnackBar bawah |
| **Menonaktifkan Admin Terakhir** | `"Admin aktif terakhir tidak dapat dinonaktifkan."` | SnackBar |
| **Kategori Masih Punya Produk Aktif** | `"Kategori ini masih memiliki produk aktif."` | SnackBar error |
| **Stok Opname Tanpa Selisih** | `"Stok hasil hitung sama dengan stok tercatat. Tidak ada yang perlu disesuaikan."` | Teks bantuan inline |
| **Penjaga Hapus/Ubah Utang Kasir (peran kasir)** | `"Utang ini berasal dari penjualan kasir dan tidak dapat diubah atau dihapus. Hubungi admin untuk rekonsiliasi."` | Dialog Modal Error |
| **Penjaga Hapus Utang Kasir (peran admin)** | `"Utang ini berasal dari penjualan kasir dan tidak dapat dihapus. Admin hanya dapat mengubah nama pelanggan dan catatan."` | Dialog Modal Error |
| **Peringatan Tanpa Cadangan** | `"Belum ada pencadangan selama {n} hari. Cadangkan sekarang?"` | Kartu Banner Dasbor Admin |
| **Berkas Cadangan Rusak** | `"Berkas ini bukan cadangan Kharismart yang valid atau rusak. Data saat ini tidak diubah."` | Dialog Error Permanen |
| **Penguncian PIN Aktif** | `"Terlalu banyak percobaan salah. Coba lagi dalam {n} detik."` | Banner merah mengambang |
| **Harga Jual di Bawah Modal** | `"Peringatan: harga jual lebih rendah dari harga modal."` | Teks merah inline |
| **Saran Keamanan Cadangan** | `"Catatan: Pastikan akun Google Drive atau media penyimpanan Anda aman. File cadangan memuat data transaksi dan catatan utang toko."` | Teks bantuan di bawah pemilih folder |
