package com.leowalk.LyricFocus.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.net.Uri

object AlbumArtLoader {

    fun load(context: Context, metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null

        metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)?.let { return it }
        metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)?.let { return it }

        return loadFromUri(context, metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI))
            ?: loadFromUri(context, metadata.getString(MediaMetadata.METADATA_KEY_ART_URI))
            ?: loadFromUri(context, metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI))
    }

    fun artKey(metadata: MediaMetadata?): String {
        if (metadata == null) return ""
        val uri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        if (!uri.isNullOrBlank()) return uri
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID).orEmpty()
        return listOf(title, artist, album, mediaId).joinToString("|")
    }

    private fun loadFromUri(context: Context, uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        } catch (_: Throwable) {
            null
        }
    }
}
