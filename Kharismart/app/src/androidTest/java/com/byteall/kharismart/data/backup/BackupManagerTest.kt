package com.byteall.kharismart.data.backup

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.byteall.kharismart.data.local.KharismartDatabase
import com.byteall.kharismart.data.local.entity.ProductEntity
import com.byteall.kharismart.util.image.ImageStorageManager
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var backupFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        KharismartDatabase.closeInstance()
        val dbFile = context.getDatabasePath(KharismartDatabase.DATABASE_NAME)
        File("${dbFile.absolutePath}-wal").delete()
        File("${dbFile.absolutePath}-shm").delete()
        dbFile.delete()
        ImageStorageManager.deleteAllPhotos(context)
        backupFile = File(context.cacheDir, "test_backup_archive.zip")
        if (backupFile.exists()) backupFile.delete()
    }

    @After
    fun tearDown() {
        KharismartDatabase.closeInstance()
        if (backupFile.exists()) backupFile.delete()
        ImageStorageManager.deleteAllPhotos(context)
    }

    @Test
    fun testSuggestedBackupFilenameMatchesConvention() {
        val suggested = BackupManager.suggestedBackupFilename()
        assertTrue(suggested.startsWith("kharismart_"))
        assertTrue(suggested.endsWith(".zip"))
    }

    @Test
    fun testFullBackupAndRestoreCycleRestoresDeletedProductAndPhoto() = runBlocking {
        val db = KharismartDatabase.getInstance(context)
        db.openHelper.writableDatabase

        // 1. Insert a test product and a dummy photo file
        val photoDir = ImageStorageManager.getPhotosDirectory(context)
        val samplePhoto = File(photoDir, "sample_restore_test.jpg")
        samplePhoto.writeBytes(ByteArray(128) { 0x42 })

        val productId = db.productDao().insert(
            ProductEntity(
                barcode = "8999999000111",
                name = "Kopi Kapal Api Special",
                categoryId = 1L,
                price = 16500L,
                costPrice = 14000L,
                stock = 25,
                photo = samplePhoto.name,
                status = 1
            )
        )
        assertNotNull(db.productDao().getById(productId))

        // 2. Create backup zip
        val backupUri = Uri.fromFile(backupFile)
        val createResult = BackupManager.createBackup(context, db, backupUri)
        assertTrue("Backup creation should succeed", createResult.isSuccess)
        assertTrue(backupFile.exists() && backupFile.length() > 0L)

        // Verify zip contains manifest.json, kharismart.db, and product_photos/sample_restore_test.jpg
        ZipFile(backupFile).use { zip ->
            assertNotNull(zip.getEntry("manifest.json"))
            assertNotNull(zip.getEntry("kharismart.db"))
            assertNotNull(zip.getEntry("product_photos/sample_restore_test.jpg"))
        }

        // 3. Mutate live data (deactivate product and delete photo)
        db.productDao().updateStatus(productId, 0)
        samplePhoto.delete()
        assertFalse(samplePhoto.exists())

        // 4. Restore from the backup zip
        val restoreResult = BackupManager.restoreBackup(context, backupUri)
        assertTrue("Restore should succeed: ${restoreResult.exceptionOrNull()?.message}", restoreResult.isSuccess)

        // 5. Re-open database and verify product active status and photo file are restored
        val reopenedDb = KharismartDatabase.getInstance(context)
        val restoredProduct = reopenedDb.productDao().getById(productId)
        assertNotNull(restoredProduct)
        assertEquals(1, restoredProduct!!.status)
        assertEquals("Kopi Kapal Api Special", restoredProduct.name)
        assertTrue("Photo file should be restored", samplePhoto.exists())
    }

    @Test
    fun testRestoreRejectsFutureDbVersionWithoutTouchingLiveData() = runBlocking {
        val db = KharismartDatabase.getInstance(context)
        val prodId = db.productDao().insert(
            ProductEntity(
                name = "Produk Aman",
                categoryId = 1L,
                price = 5000L,
                costPrice = 4000L,
                stock = 10,
                status = 1
            )
        )

        // Create a zip with db_version = 99
        val invalidZip = File(context.cacheDir, "invalid_future_version.zip")
        ZipOutputStream(FileOutputStream(invalidZip)).use { zos ->
            val manifest = JSONObject().apply {
                put("app_version", "9.9.9")
                put("db_version", 99)
                put("created_at", "2026-11-16 10:00:00")
                put("photo_count", 0)
            }.toString()
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifest.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("kharismart.db"))
            zos.write("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            zos.closeEntry()
        }

        val result = BackupManager.restoreBackup(context, Uri.fromFile(invalidZip))
        assertTrue("Restore must fail for future db_version", result.isFailure)
        invalidZip.delete()

        // Live database must still contain "Produk Aman"
        val stillThere = db.productDao().getById(prodId)
        assertNotNull(stillThere)
        assertEquals("Produk Aman", stillThere!!.name)
    }
}