package com.byteall.kharismart.util.image

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.byteall.kharismart.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Reusable Compose component for picking, displaying, and managing product photos.
 *
 * Features:
 * - Uses Coil's [AsyncImage] to load photos from private app storage.
 * - Displays a fallback placeholder icon when no photo is attached.
 * - Integrates Android's [ActivityResultContracts.PickVisualMedia] photo picker contract.
 * - Saves selected images via [ImageStorageManager] ensuring compression to <= 1 MB and max 1200 px.
 * - Provides a "Change Photo" / "Select Photo" button.
 * - Provides a "Delete Photo" button when a photo is currently set.
 *
 * @param photoFilename Filename of the current product photo (e.g., "<uuid>.jpg"), or null if unset.
 * @param onPhotoChanged Callback invoked with the new saved filename, or null when removed.
 * @param modifier Composable modifier.
 * @param enabled Whether picker controls are interactive.
 */
@Composable
fun ProductImagePicker(
    photoFilename: String?,
    onPhotoChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    // Resolve the file from private app storage
    val imageFile = remember(photoFilename) {
        if (!photoFilename.isNullOrBlank()) {
            ImageStorageManager.getImageFile(context, photoFilename)
        } else {
            null
        }
    }

    // Photo picker launcher contract
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                val newFilename = withContext(Dispatchers.IO) {
                    val saved = ImageStorageManager.saveImageFromUri(context, uri)
                    if (saved != null && !photoFilename.isNullOrBlank()) {
                        ImageStorageManager.deleteImageFile(context, photoFilename)
                    }
                    saved
                }
                isLoading = false
                if (newFilename != null) {
                    onPhotoChanged(newFilename)
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Preview Box / Thumbnail
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        RoundedCornerShape(12.dp)
                    )
                    .testTag("product_image_preview_box")
                    .then(
                        if (enabled && !isLoading) {
                            Modifier.clickable {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (imageFile != null && imageFile.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageFile)
                            .crossfade(true)
                            .build(),
                        contentDescription = stringResource(R.string.product_photo_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("product_async_image")
                    )
                } else {
                    // Fallback placeholder icon
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(8.dp)
                            .testTag("product_image_placeholder")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = stringResource(R.string.product_photo_placeholder),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.product_photo_no_image),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Loading overlay while processing/compressing
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("product_image_loading_indicator")
                        )
                    }
                }
            }

            // Action Buttons and Guidelines Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Change / Pick Photo Button
                Button(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = enabled && !isLoading,
                    modifier = Modifier.testTag("btn_pick_photo")
                ) {
                    Icon(
                        imageVector = if (photoFilename != null) Icons.Default.Edit else Icons.Default.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (photoFilename != null) {
                            stringResource(R.string.product_photo_change)
                        } else {
                            stringResource(R.string.product_photo_select)
                        }
                    )
                }

                // Delete Photo Button (only shown if a photo is attached)
                if (photoFilename != null) {
                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    ImageStorageManager.deleteImageFile(context, photoFilename)
                                }
                                isLoading = false
                                onPhotoChanged(null)
                            }
                        },
                        enabled = enabled && !isLoading,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.testTag("btn_delete_photo")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.product_photo_delete))
                    }
                }

                // Format & Size Hint
                Text(
                    text = stringResource(R.string.product_photo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
