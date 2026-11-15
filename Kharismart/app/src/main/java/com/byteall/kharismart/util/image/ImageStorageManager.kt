package com.byteall.kharismart.util.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Manages photo storage pipeline for product photos and app media.
 *
 * Requirements:
 * - Photos stored in app private storage (`context.filesDir/product_photos`).
 * - Strictly compressed to JPEG format with file size <= 1 MB (1,048,576 bytes).
 * - Maximum dimension (width or height) downscaled to <= 1200 px while preserving aspect ratio.
 * - Handles camera EXIF orientation rotations automatically.
 * - Database stores only the filename string (e.g., "<UUID>.jpg").
 */
object ImageStorageManager {

    private const val TAG = "ImageStorageManager"
    const val PHOTO_DIR_NAME = "product_photos"
    const val MAX_DIMENSION = 1200
    const val MAX_FILE_SIZE_BYTES = 1024 * 1024 // 1 MB = 1,048,576 bytes
    private const val INITIAL_QUALITY = 80
    private const val MIN_QUALITY = 20
    private const val QUALITY_STEP = 10

    /**
     * Returns the private storage directory for product photos (`context.filesDir/product_photos`).
     * Creates the directory if it does not already exist.
     */
    fun getPhotosDirectory(context: Context): File {
        val dir = File(context.filesDir, PHOTO_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Opens an [InputStream] from [sourceUri], decodes the [Bitmap] efficiently, applies EXIF rotation,
     * scales it down if dimensions exceed [MAX_DIMENSION], compresses it to JPEG ensuring file size
     * is strictly <= [MAX_FILE_SIZE_BYTES] (1 MB), and writes it into `context.filesDir/product_photos/<UUID>.jpg`.
     *
     * @param context Application or UI context.
     * @param sourceUri Uri of the selected or captured image.
     * @return The saved filename (e.g. "<uuid>.jpg"), or null if processing fails.
     */
    fun saveImageFromUri(context: Context, sourceUri: Uri): String? {
        return try {
            // 1. Determine image orientation from EXIF before reading bounds
            val orientation = getExifOrientation(context, sourceUri)

            // 2. Decode image bounds to calculate inSampleSize to avoid OOM
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOptions)
            } ?: run {
                Log.e(TAG, "Cannot open input stream for Uri: $sourceUri")
                return null
            }

            val origWidth = boundsOptions.outWidth
            val origHeight = boundsOptions.outHeight
            if (origWidth <= 0 || origHeight <= 0) {
                Log.e(TAG, "Invalid image dimensions: $origWidth x $origHeight")
                return null
            }

            // 3. Decode bitmap with inSampleSize to conserve heap memory
            var sampleSize = 1
            val maxRawDim = maxOf(origWidth, origHeight)
            while ((maxRawDim / (sampleSize * 2)) >= MAX_DIMENSION) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decodedBitmap = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            } ?: run {
                Log.e(TAG, "Failed to decode bitmap from Uri: $sourceUri")
                return null
            }

            // 4. Apply EXIF rotation if necessary
            val orientedBitmap = applyExifRotation(decodedBitmap, orientation)

            // 5. Downscale to max dimension and compress <= 1 MB
            val savedFilename = saveBitmap(context, orientedBitmap)

            // Recycle intermediate bitmaps if new allocations were made
            if (orientedBitmap != decodedBitmap) {
                decodedBitmap.recycle()
            }
            if (orientedBitmap.isRecycled.not()) {
                orientedBitmap.recycle()
            }

            savedFilename
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image from Uri: $sourceUri", e)
            null
        }
    }

    /**
     * Scales down [bitmap] if either width or height exceeds [MAX_DIMENSION], compresses it to JPEG
     * (starting at 80% quality and adjusting downward) ensuring the file size is strictly <= 1 MB,
     * and saves it into `context.filesDir/product_photos/<UUID>.jpg`.
     *
     * @param context Application or UI context.
     * @param bitmap Source bitmap.
     * @return The saved filename (e.g. "<uuid>.jpg"), or null if saving fails.
     */
    fun saveBitmap(context: Context, bitmap: Bitmap): String? {
        return try {
            // 1. Scale down if necessary
            val scaledBitmap = scaleDownIfNeeded(bitmap, MAX_DIMENSION)

            // 2. Compress to JPEG ensuring size <= 1 MB
            val compressedBytes = compressUnderLimit(scaledBitmap, MAX_FILE_SIZE_BYTES, INITIAL_QUALITY)

            // Clean up scaled bitmap if a new one was allocated
            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            if (compressedBytes == null) {
                Log.e(TAG, "Could not compress image under limit of $MAX_FILE_SIZE_BYTES bytes")
                return null
            }

            // 3. Save to private directory
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

    /**
     * Returns the [File] object corresponding to [filename] from `context.filesDir/product_photos`,
     * or null if the file does not exist or is not a regular file.
     * Includes protection against path traversal attempts.
     */
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

    /**
     * Deletes the image file corresponding to [filename] from `context.filesDir/product_photos`.
     * Includes protection against path traversal attempts.
     *
     * @return true if the file existed and was successfully deleted, false otherwise.
     */
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

    /**
     * Deletes all files inside the `product_photos` directory.
     *
     * @return true if all files were deleted or the directory was empty.
     */
    fun deleteAllPhotos(context: Context): Boolean {
        val photosDir = getPhotosDirectory(context)
        return try {
            photosDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downscales the given [bitmap] if width or height exceeds [maxDim], preserving aspect ratio.
     * If the bitmap is within limits, returns the original bitmap.
     */
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

    /**
     * Compresses the [bitmap] to JPEG format, adjusting quality iteratively to ensure
     * byte size is strictly <= [maxBytes]. If quality reaches [MIN_QUALITY] and still exceeds
     * [maxBytes], downscales the bitmap further and re-attempts compression.
     */
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

            // If still too large at MIN_QUALITY, downscale dimensions progressively
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

    /**
     * Extracts EXIF orientation tag from [uri] using [ExifInterface].
     */
    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }

    /**
     * Applies rotation or flip according to the EXIF [orientation] tag.
     */
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
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Error applying EXIF rotation", e)
            bitmap
        }
    }
}
