package com.lyricnotify.focus.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import com.lyricnotify.focus.R

object NotificationIconHelper {

    private const val MODULE_PACKAGE = "com.lyricnotify.focus"

    const val MAX_LINE2_CHARS = 36
    const val MAX_LINE3_CHARS = 36

    fun truncateLine(text: String, maxChars: Int): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "\u00A0"
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars - 1) + "…"
    }

    fun createBlankIcon(context: Context): Icon {
        loadIconFromModule(context, "ic_notification_blank")?.let { return it }
        return createTransparentBitmapIcon()
    }

    fun createMusicIcon(context: Context): Icon {
        loadIconFromModule(context, "ic_music_note")?.let { return it }
        return createBlankIcon(context)
    }

    private fun loadIconFromModule(context: Context, drawableName: String): Icon? {
        return try {
            val moduleContext = context.createPackageContext(
                MODULE_PACKAGE,
                Context.CONTEXT_IGNORE_SECURITY
            )
            val resId = moduleContext.resources.getIdentifier(
                drawableName,
                "drawable",
                MODULE_PACKAGE
            )
            if (resId != 0) Icon.createWithResource(moduleContext, resId) else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun createTransparentBitmapIcon(): Icon {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.TRANSPARENT)
        return Icon.createWithBitmap(bitmap)
    }
}
