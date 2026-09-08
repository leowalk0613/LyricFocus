package com.leowalk.ExternalLyricTest

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import org.json.JSONObject
import java.io.FileInputStream

/**
 * LyricFocus 外部歌词接收端（协议 v1）。
 * 见 docs/external-lyric-protocol.md
 */
class LyricReceiverProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_PUT_LYRIC -> {
                val json = extras?.getString("n") ?: return null
                handleJson(METHOD_PUT_LYRIC, json)
                null
            }
            METHOD_PUT_LYRIC_FD -> {
                @Suppress("DEPRECATION")
                val pfd = extras?.getParcelable<ParcelFileDescriptor>("fd") ?: return null
                pfd.use { fd ->
                    val text = FileInputStream(fd.fileDescriptor).bufferedReader().use { it.readText() }
                    handleJson(METHOD_PUT_LYRIC_FD, text)
                }
                null
            }
            METHOD_SETTINGS -> Bundle().apply {
                putString(
                    "n",
                    JSONObject().put("lyric_advance_ms", DEFAULT_ADVANCE_MS).toString()
                )
            }
            else -> {
                Log.w(TAG, "unknown method=$method")
                null
            }
        }
    }

    private fun handleJson(method: String, raw: String) {
        try {
            val o = JSONObject(raw)
            val ctx = o.optJSONObject("ctx")
            val lines = ctx?.optJSONArray("lines")
            val snapshot = LyricHub.Snapshot(
                method = method,
                title = o.optString("title"),
                artist = o.optString("artist"),
                line = o.optString("l"),
                second = o.optString("s"),
                timeMs = o.optLong("t"),
                playing = o.optBoolean("playing", true),
                pkg = o.optString("pkg"),
                ctxIdx = if (ctx != null) ctx.optInt("idx", -1) else null,
                ctxLineCount = lines?.length(),
                rawPreview = raw.take(240)
            )
            LyricHub.publish(snapshot)
            Log.i(
                TAG,
                "$method title=${snapshot.title} l=${snapshot.line} " +
                    "ctxLines=${snapshot.ctxLineCount} idx=${snapshot.ctxIdx}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "parse failed method=$method", e)
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        private const val TAG = "ExternalLyricTest"
        const val METHOD_PUT_LYRIC = "putlyric"
        const val METHOD_PUT_LYRIC_FD = "putlyricfd"
        const val METHOD_SETTINGS = "settings"
        const val DEFAULT_ADVANCE_MS = 200
    }
}
