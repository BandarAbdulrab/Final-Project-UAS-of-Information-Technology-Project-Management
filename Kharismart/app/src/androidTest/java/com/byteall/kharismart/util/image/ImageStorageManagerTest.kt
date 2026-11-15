package com.byteall.kharismart.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class ImageStorageManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ImageStorageManager.deleteAllPhotos(context)
    }

    @After
    fun tearDown() {
        ImageStorageManager.deleteAllPhotos(context)
    }

    @Test
    fun testSaveBitmap_createsFileUnder1MBAndReturnsValidFilename() {
        // Create a 600x600 test bitmap
        val bitmap = Bitmap.createBitmap(600, 600, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)

        val filename = ImageStorageManager.saveBitmap(context, bitmap)
        assertNotNull("Filename should not be null", filename)
        assertTrue("Filename should end with .jpg", filename!!.endsWith(".jpg"))

        // Verify the file exists on private disk
        val file = ImageStorageManager.getImageFile(context, filename)
        assertNotNull("Image file should exist in storage", file)
        assertTrue("File must actually exist on disk", file!!.exists())
        assertTrue("File must be regular file", file.isFile)
        assertTrue("File size must be strictly <= 1 MB (1048576 bytes)", file.length() <= 1024 * 1024)
        assertTrue("File size must be > 0 bytes", file.length() > 0)

        bitmap.recycle()
    }

    @Test
    fun testSaveBitmap_downscalesLargeImagePreservingAspectRatio() {
        // Create a 2400x1200 bitmap (2:1 aspect ratio, exceeds MAX_DIMENSION 1200)
        val largeBitmap = Bitmap.createBitmap(2400, 1200, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(Color.GREEN)

        val filename = ImageStorageManager.saveBitmap(context, largeBitmap)
        assertNotNull("Filename should not be null", filename)

        val file = ImageStorageManager.getImageFile(context, filename!!)
        assertNotNull(file)

        // Decode saved file bounds to check downscaled dimensions
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file!!.absolutePath, options)

        assertEquals("Width must be scaled down to 1200 px", 1200, options.outWidth)
        assertEquals("Height must be scaled proportionally to 600 px", 600, options.outHeight)
        assertTrue("File size must be strictly <= 1 MB", file.length() <= 1024 * 1024)

        largeBitmap.recycle()
    }

    @Test
    fun testSaveBitmap_smallImageNotUpscaled() {
        // Create a 320x240 bitmap (below MAX_DIMENSION)
        val smallBitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        smallBitmap.eraseColor(Color.RED)

        val filename = ImageStorageManager.saveBitmap(context, smallBitmap)
        assertNotNull(filename)

        val file = ImageStorageManager.getImageFile(context, filename!!)
        assertNotNull(file)

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file!!.absolutePath, options)

        assertEquals("Small image width should remain 320 px", 320, options.outWidth)
        assertEquals("Small image height should remain 240 px", 240, options.outHeight)

        smallBitmap.recycle()
    }

    @Test
    fun testDeleteImageFile_deletesExistingFileAndReturnsTrue() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val filename = ImageStorageManager.saveBitmap(context, bitmap)
        assertNotNull(filename)

        // First deletion should succeed
        val deleted = ImageStorageManager.deleteImageFile(context, filename!!)
        assertTrue("deleteImageFile should return true on existing file", deleted)

        // File should no longer exist
        val file = ImageStorageManager.getImageFile(context, filename)
        assertNull("getImageFile should return null after file is deleted", file)

        // Second deletion should return false
        val secondDelete = ImageStorageManager.deleteImageFile(context, filename)
        assertFalse("deleteImageFile should return false when file no longer exists", secondDelete)

        bitmap.recycle()
    }

    @Test
    fun testDeleteImageFile_nonExistentFileReturnsFalse() {
        val result = ImageStorageManager.deleteImageFile(context, "non_existent_file.jpg")
        assertFalse("deleteImageFile should return false for nonexistent file", result)
    }

    @Test
    fun testGetImageFile_returnsNullForNonExistentFile() {
        val file = ImageStorageManager.getImageFile(context, "does_not_exist.jpg")
        assertNull("getImageFile should return null for nonexistent file", file)
    }

    @Test
    fun testGetImageFile_returnsNullForBlankFilename() {
        assertNull(ImageStorageManager.getImageFile(context, ""))
        assertNull(ImageStorageManager.getImageFile(context, "   "))
    }

    @Test
    fun testPathTraversalSecurity_cannotEscapePhotosDir() {
        // Attempt directory traversal attacks
        val escapedFile = ImageStorageManager.getImageFile(context, "../../databases/kharismart.db")
        assertNull("Path traversal via getImageFile must return null", escapedFile)

        val deleteResult = ImageStorageManager.deleteImageFile(context, "../../databases/kharismart.db")
        assertFalse("Path traversal via deleteImageFile must return false", deleteResult)
    }

    @Test
    fun testDeleteAllPhotos_cleansUpPhotosDirectory() {
        val b1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val b2 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        ImageStorageManager.saveBitmap(context, b1)
        ImageStorageManager.saveBitmap(context, b2)

        val photosDir = ImageStorageManager.getPhotosDirectory(context)
        assertEquals("Should contain 2 photos", 2, photosDir.listFiles()?.size)

        val cleaned = ImageStorageManager.deleteAllPhotos(context)
        assertTrue(cleaned)
        assertEquals("Photos directory should be empty", 0, photosDir.listFiles()?.size)

        b1.recycle()
        b2.recycle()
    }
}
