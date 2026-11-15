package com.byteall.kharismart.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

object ImageStorageManager {

    private const val TAG = "ImageStorageManager"
    const val PHOTO_DIR_NAME = "product_photos"
    const val MAX_DIMENSION = 1200
    const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1 MB = 1,048,576 bytes
    private const val INITIAL_QUALITY = 80
    private const val MIN_QUALITY = 20
    private const val QUALITY_STEP = 10

    fun getPhotosDirectory(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun saveImageFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            // Read stream once into memory so content:// picker URIs never fail on re-open
            val rawBytes = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                input.readBytes()
            } ?: run {
                Log.e(TAG, "Cannot open input stream for Uri: $sourceUri")
                return null
            }

            if (rawBytes.isEmpty()) {
                Log.e(TAG, "Empty byte stream for Uri: $sourceUri")
                return null
            }

            // 1. Determine EXIF orientation from memory bytes
            val orientation = getExifOrientationFromBytes(rawBytes)

            // 2. Decode image bounds from memory bytes
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, boundsOptions)

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: $origWidth x $origHeight")
                return null
            }

            // 3. Calculate sampleSize and decode bitmap
            var sampleSize = 1
            val maxRawDim = maxOf(origWidth, origHeight)
            while ((maxRawDim / (sampleSize * 2)) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOptions)
                ?: run {
                    Log.e(TAG, "Failed to decode bitmap from bytes for Uri: $sourceUri")
                    return null
                }

            // 4. Apply EXIF rotation if necessary
            val orientedBitmap = applyExifRotation(decodedBitmap, orientation)

            // 5. Downscale to max dimension and compress <= 1 MB
            val savedFilename = saveBitmap(context, orientedBitmap)

            if (orientedBitmap != decodedBitmap && !decodedBitmap.isRecycled) {
                decodedBitmap.recycle()
            }
            if (!orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }

            savedFilename
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image from Uri: $sourceUri", e)
            null
        }
    }

    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            val scaledBitmap = scaleDownIfNeeded(bitmap, MAX_DIMENSION)
            val compressedBytes = compressUnderLimit(scaledBitmap, MAX_FILE_SIZE_BYTES, INITIAL_QUALITY)

            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            if (compressedBytes == null) {
                Log.e(TAG, "Could not compress image under limit of $MAX_FILE_SIZE_BYTES bytes")
                return null
            }

            val photosDir = getPhotosDirectory(context)
            val filename = "${UUID.randomUUID()}.jpg"
            val targetFile = File(photosDir, filename)

            FileOutputStream(targetFile).use { fos ->
                fos.write(compressedBytes)
                fos.flush()
            }

            filename
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap", e)
            null
        }
    }

    fun getImageFile(context: Context, filename: String): File? {
        if (filename.isBlank()) return null
        val photosDir = getPhotosDirectory(context)
        val cleanName = File(filename).name
        val file = File(photosDir, cleanName)

        return try {
            if (file.canonicalPath.startsWith(photosDir.canonicalPath) && file.exists() && file.isFile) {
                file
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun deleteImageFile(context: Context, filename: String): Boolean {
        if (filename.isBlank()) return false
        val photosDir = getPhotosDirectory(context)
        val cleanName = File(filename).name
        val file = File(photosDir, cleanName)

        return try {
            if (file.canonicalPath.startsWith(photosDir.canonicalPath) && file.exists() && file.isFile) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun deleteAllPhotos(context: Context): Boolean {
        val photosDir = getPhotosDirectory(context)
        return try {
            photosDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maxCurrentDim = maxOf(width, height)

        if (maxCurrentDim <= maxDim) {
            return bitmap
        }

        val scale = maxDim.toFloat() / maxCurrentDim.toFloat()
        val newWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val newHeight = (height * scale).roundToInt().coerceAtLeast(1)

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressUnderLimit(bitmap: Bitmap, maxBytes: Int, initialQuality: Int): ByteArray? {
        var currentBitmap = bitmap
        var shouldRecycleCurrent = false

        try {
            var quality = initialQuality
            while (quality >= MIN_QUALITY) {
                val stream = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= maxBytes) {
                    return bytes
                }
                quality -= QUALITY_STEP
            }

            var scaleFactor = 0.75f
            while (scaleFactor >= 0.25f) {
                val nextWidth = (currentBitmap.width * scaleFactor).roundToInt().coerceAtLeast(1)
                val nextHeight = (currentBitmap.height * scaleFactor).roundToInt().coerceAtLeast(1)
                val downscaled = Bitmap.createScaledBitmap(currentBitmap, nextWidth, nextHeight, true)

                if (shouldRecycleCurrent && currentBitmap != bitmap && !currentBitmap.isRecycled) {
                    currentBitmap.recycle()
                }
                currentBitmap = downscaled
                shouldRecycleCurrent = true

                val stream = ByteArrayOutputStream()
                currentBitmap.compress(Bitmap.CompressFormat.JPEG, MIN_QUALITY, stream)
                val bytes = stream.toByteArray()
                if (bytes.size <= maxBytes) {
                    return bytes
                }
                scaleFactor -= 0.25f
            }

            return null
        } finally {
            if (shouldRecycleCurrent && currentBitmap != bitmap && !currentBitmap.isRecycled) {
                currentBitmap.recycle()
            }
        }
    }

    private fun getExifOrientationFromBytes(bytes: ByteArray): Int {
        return try {
            ByteArrayInputStream(bytes).use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    private fun applyExifRotation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error applying EXIF rotation", e)
            bitmap
        }
    }
}