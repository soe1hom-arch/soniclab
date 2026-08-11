package com.soniclab.app.ui.common

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.soniclab.app.ui.theme.PurpleAccent
import com.soniclab.app.ui.theme.SurfaceVariantDark

/**
 * Loads the album art for [albumId] from MediaStore and falls back to a
 * gradient + music-note placeholder when unavailable (offline, defensive).
 */
@Composable
fun AlbumArt(
    albumId: Long,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap = remember(albumId) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(albumId) {
        bitmap.value = loadAlbumArt(context, albumId)
    }
    val art = bitmap.value
    if (art != null) {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(PurpleAccent.copy(alpha = 0.55f), SurfaceVariantDark))
            ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

private fun loadAlbumArt(context: Context, albumId: Long): Bitmap? {
    if (albumId <= 0) return null
    val uri: Uri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"),
        albumId
    )
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        }
    }.getOrNull()
}
