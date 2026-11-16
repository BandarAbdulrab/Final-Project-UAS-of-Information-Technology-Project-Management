package com.byteall.kharismart.data.backup

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.OpenableColumns
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.util.DomainException
import com.byteall.kharismart.util.image.ImageStorageManager
import com.byteall.kharismart.util.time.WibClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object BackupManager {

    const val CURRENT_DB_VERSION = 1
    const val APP_VERSION = "1.0.0"
    const val MANIFEST_ENTRY = "manifest.json"
    const val DB_ENTRY = KharismartDatabase.DATABASE_NAME
    const val PHOTOS_DIR_PREFIX = "${ImageStorageManager.PHOTO_DIR_NAME}/"
    const val SAFETY_ROOT_DIR = "restore_safety"
    private const val MAX_SAFETY_FOLDERS = 3
    private val SQLITE_MAGIC_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private val FILE_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm")
    private val SAFETY_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS")

    fun suggestedBackupFilename(): String {
        val now = WibClock.parse(WibClock.nowString())
        return "kharismart_${now.format(FILE_TIMESTAMP_FORMAT)}.zip"
    }

    suspend fun createBackup(
        context: Context,
        database: KharismartDatabase,
        destinationUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // a. Flush WAL into the main database file using query()
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { cursor -> cursor.moveToFirst() }

            val dbFile = context.getDatabasePath(KharismartDatabase.DATABASE_NAME)
            if (!dbFile.exists() || !dbFile.isFile) {
                throw DomainException("File database utama tidak ditemukan.")
            }

            val photosDir = ImageStorageManager.getPhotosDirectory(context)
            val photoFiles = photosDir.listFiles()?.filter { it.isFile } ?: emptyList()

            val manifestJson = JSONObject().apply {
                put("app_version", APP_VERSION)
                put("db_version", CURRENT_DB_VERSION)
                put("created_at", WibClock.nowString())
                put("photo_count", photoFiles.size)
            }.toString(2)

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: throw DomainException("Gagal membuka lokasi tujuan file cadangan.")

            outputStream.use { rawOut ->
                ZipOutputStream(BufferedOutputStream(rawOut)).use { zos ->
                    // 1. Write manifest.json
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()

                    // 2. Write kharismart.db
                    zos.putNextEntry(ZipEntry(DB_ENTRY))
                    FileInputStream(dbFile).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()

                    // 3. Write every photo in filesDir/product_photos/
                    for (photo in photoFiles) {
                        val entryName = "$PHOTOS_DIR_PREFIX${photo.name}"
                        zos.putNextEntry(ZipEntry(entryName))
                        FileInputStream(photo).use { fis ->
                            fis.copyTo(zos)
                        }
                        zos.closeEntry()
                    }
                    zos.flush()
                }
            }

            resolveDisplayFileName(context, destinationUri) ?: suggestedBackupFilename()
        }
    }

    suspend fun restoreBackup(
        context: Context,
        backupZipUri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempZipFile = File(context.cacheDir, "restore_archive_${UUID.randomUUID()}.zip")
        val tempDbFile = File(context.cacheDir, "restore_db_${UUID.randomUUID()}.db")

        runCatching {
            try {
                // Copy incoming SAF stream to a local temp file for random-access ZipFile inspection
                val inputStream = context.contentResolver.openInputStream(backupZipUri)
                    ?: throw DomainException("Tidak dapat membaca file cadangan yang dipilih.")
                inputStream.use { input ->
                    FileOutputStream(tempZipFile).use { output ->
                        input.copyTo(output)
                    }
                }

                if (!tempZipFile.exists() || tempZipFile.length() == 0L) {
                    throw DomainException("File cadangan kosong atau rusak.")
                }

                val zipFile = try {
                    ZipFile(tempZipFile)
                } catch (e: Exception) {
                    throw DomainException("Format file bukan arsip ZIP yang valid.")
                }

                zipFile.use { zip ->
                    // Step 1: Validate zip entries and manifest db_version <= current
                    val manifestEntry = zip.getEntry(MANIFEST_ENTRY)
                        ?: throw DomainException("Arsip cadangan tidak valid: manifest.json tidak ditemukan.")
                    val dbEntry = zip.getEntry(DB_ENTRY)
                        ?: throw DomainException("Arsip cadangan tidak valid: kharismart.db tidak ditemukan.")

                    val manifestText = zip.getInputStream(manifestEntry).use {
                        it.readBytes().toString(Charsets.UTF_8)
                    }
                    val manifestObj = try {
                        JSONObject(manifestText)
                    } catch (e: Exception) {
                        throw DomainException("Format manifest.json di dalam cadangan rusak.")
                    }

                    val backupDbVersion = manifestObj.optInt("db_version", -1)
                    if (backupDbVersion <= 0) {
                        throw DomainException("Versi database pada manifest tidak valid.")
                    }
                    if (backupDbVersion > CURRENT_DB_VERSION) {
                        throw DomainException(
                            "Versi database cadangan ($backupDbVersion) lebih baru dari versi aplikasi ini ($CURRENT_DB_VERSION)."
                        )
                    }

                    // Validate all photo entries against zip-slip before touching anything
                    val photosDirCanonical = ImageStorageManager.getPhotosDirectory(context).canonicalPath
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.contains("..")) {
                            throw DomainException("Terdeteksi path tidak aman di dalam arsip ZIP.")
                        }
                        if (entry.name.startsWith(PHOTOS_DIR_PREFIX) && !entry.isDirectory) {
                            val relativeName = entry.name.removePrefix(PHOTOS_DIR_PREFIX)
                            val candidate = File(ImageStorageManager.getPhotosDirectory(context), relativeName)
                            if (!candidate.canonicalPath.startsWith(photosDirCanonical + File.separator)) {
                                throw DomainException("Terdeteksi path foto di luar folder penyimpanan.")
                            }
                        }
                    }

                    // Step 2: Extract database to temp file and verify SQLite header + integrity_check + user_version
                    zip.getInputStream(dbEntry).use { dbIn ->
                        FileOutputStream(tempDbFile).use { dbOut ->
                            dbIn.copyTo(dbOut)
                        }
                    }

                    validateSqliteTempFile(tempDbFile)

                    // Step 3: Create safety copy in filesDir/restore_safety/<timestamp>/
                    val safetyDir = createSafetyCopy(context)

                    // Steps 4 to 6: Close Room, replace DB + WAL/SHM, replace product_photos (with rollback on failure)
                    try {
                        // Step 4: Close Room
                        KharismartDatabase.closeInstance()

                        // Step 5: Delete main db, -wal, -shm, and -journal, then copy restored DB into place
                        val liveDbFile = context.getDatabasePath(KharismartDatabase.DATABASE_NAME)
                        liveDbFile.parentFile?.mkdirs()
                        deleteDatabaseFiles(liveDbFile)
                        tempDbFile.copyTo(liveDbFile, overwrite = true)

                        // Step 6: Replace product_photos from zip (zip-slip safe)
                        val photosDir = ImageStorageManager.getPhotosDirectory(context)
                        photosDir.listFiles()?.forEach { it.delete() }

                        val extractEntries = zip.entries()
                        while (extractEntries.hasMoreElements()) {
                            val entry = extractEntries.nextElement()
                            if (entry.name.startsWith(PHOTOS_DIR_PREFIX) && !entry.isDirectory) {
                                val fileName = entry.name.removePrefix(PHOTOS_DIR_PREFIX)
                                if (fileName.isNotBlank()) {
                                    val destPhoto = File(photosDir, fileName)
                                    if (!destPhoto.canonicalPath.startsWith(photosDirCanonical + File.separator)) {
                                        throw DomainException("Path foto tidak valid: ${entry.name}")
                                    }
                                    zip.getInputStream(entry).use { zIn ->
                                        FileOutputStream(destPhoto).use { fOut ->
                                            zIn.copyTo(fOut)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (restoreErr: Throwable) {
                        // Step 7: Rollback from safety copy if anything in steps 4..6 failed
                        rollbackFromSafetyCopy(context, safetyDir)
                        throw DomainException(
                            "Gagal menerapkan pemulihan (${restoreErr.message}). Data sebelumnya telah dikembalikan."
                        )
                    }
                }
            } finally {
                if (tempDbFile.exists()) tempDbFile.delete()
                if (tempZipFile.exists()) tempZipFile.delete()
            }
        }
    }

    fun restartApp(context: Context) {
        val packageManager = context.packageManager
        val launchIntent = packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
        Runtime.getRuntime().exit(0)
    }

    private fun validateSqliteTempFile(tempDbFile: File) {
        if (!tempDbFile.exists() || tempDbFile.length() < 16L) {
            throw DomainException("File database di dalam cadangan terlalu kecil atau rusak.")
        }

        val header = ByteArray(16)
        BufferedInputStream(FileInputStream(tempDbFile)).use { fis ->
            val read = fis.read(header)
            if (read != 16 || !header.contentEquals(SQLITE_MAGIC_HEADER)) {
                throw DomainException("Header file bukan SQLite 3 yang valid.")
            }
        }

        val sqliteDb = try {
            SQLiteDatabase.openDatabase(
                tempDbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        } catch (e: Exception) {
            throw DomainException("Database cadangan tidak dapat dibuka: ${e.message}")
        }

        sqliteDb.use { db ->
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else ""
            }
            if (!integrity.equals("ok", ignoreCase = true)) {
                throw DomainException("Pemeriksaan integritas SQLite gagal: $integrity")
            }

            val userVersion = db.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (userVersion > CURRENT_DB_VERSION) {
                throw DomainException(
                    "PRAGMA user_version ($userVersion) melebihi versi saat ini ($CURRENT_DB_VERSION)."
                )
            }
        }
    }

    private fun createSafetyCopy(context: Context): File {
        val safetyRoot = File(context.filesDir, SAFETY_ROOT_DIR)
        if (!safetyRoot.exists()) safetyRoot.mkdirs()

        val stamp = WibClock.parse(WibClock.nowString()).format(SAFETY_TIMESTAMP_FORMAT)
        val safetyDir = File(safetyRoot, stamp)
        safetyDir.mkdirs()

        val liveDb = context.getDatabasePath(KharismartDatabase.DATABASE_NAME)
        val liveWal = File("${liveDb.absolutePath}-wal")
        val liveShm = File("${liveDb.absolutePath}-shm")

        if (liveDb.exists()) liveDb.copyTo(File(safetyDir, liveDb.name), overwrite = true)
        if (liveWal.exists()) liveWal.copyTo(File(safetyDir, liveWal.name), overwrite = true)
        if (liveShm.exists()) liveShm.copyTo(File(safetyDir, liveShm.name), overwrite = true)

        val safetyPhotosDir = File(safetyDir, ImageStorageManager.PHOTO_DIR_NAME)
        safetyPhotosDir.mkdirs()
        val livePhotosDir = ImageStorageManager.getPhotosDirectory(context)
        livePhotosDir.listFiles()?.filter { it.isFile }?.forEach { photo ->
            photo.copyTo(File(safetyPhotosDir, photo.name), overwrite = true)
        }

        pruneOldSafetyCopies(safetyRoot)
        return safetyDir
    }

    private fun pruneOldSafetyCopies(safetyRoot: File) {
        val dirs = safetyRoot.listFiles()?.filter { it.isDirectory }
            ?.sortedByDescending { it.name }
            ?: return
        if (dirs.size > MAX_SAFETY_FOLDERS) {
            dirs.drop(MAX_SAFETY_FOLDERS).forEach { oldDir ->
                oldDir.deleteRecursively()
            }
        }
    }

    private fun rollbackFromSafetyCopy(context: Context, safetyDir: File) {
        runCatching {
            KharismartDatabase.closeInstance()
            val liveDb = context.getDatabasePath(KharismartDatabase.DATABASE_NAME)
            deleteDatabaseFiles(liveDb)

            val safetyDb = File(safetyDir, liveDb.name)
            val safetyWal = File(safetyDir, "${liveDb.name}-wal")
            val safetyShm = File(safetyDir, "${liveDb.name}-shm")

            if (safetyDb.exists()) safetyDb.copyTo(liveDb, overwrite = true)
            if (safetyWal.exists()) safetyWal.copyTo(File("${liveDb.absolutePath}-wal"), overwrite = true)
            if (safetyShm.exists()) safetyShm.copyTo(File("${liveDb.absolutePath}-shm"), overwrite = true)

            val livePhotosDir = ImageStorageManager.getPhotosDirectory(context)
            livePhotosDir.listFiles()?.forEach { it.delete() }
            val safetyPhotosDir = File(safetyDir, ImageStorageManager.PHOTO_DIR_NAME)
            safetyPhotosDir.listFiles()?.filter { it.isFile }?.forEach { photo ->
                photo.copyTo(File(livePhotosDir, photo.name), overwrite = true)
            }
        }
    }

    private fun deleteDatabaseFiles(dbFile: File) {
        val wal = File("${dbFile.absolutePath}-wal")
        val shm = File("${dbFile.absolutePath}-shm")
        val journal = File("${dbFile.absolutePath}-journal")
        if (wal.exists()) wal.delete()
        if (shm.exists()) shm.delete()
        if (journal.exists()) journal.delete()
        if (dbFile.exists()) dbFile.delete()
    }

    private fun resolveDisplayFileName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment
    }
}