package com.leowalk.LyricFocus.util

import com.leowalk.LyricFocus.FocusPreferences

object LyricFocusLogFilter {

    private val APP_PACKAGE = FocusPreferences.MODULE_PACKAGE

    private val LOG_TAGS = listOf(
        "LyricFocus_Xposed",
        "LyricService",
        "MusicMonitorService",
        "SystemUIHyperFocusHook",
        "SystemUIPluginHook",
        "AodFocusPluginHook",
        "HyperFocusLyricStyle",
        "LyricFocus_About"
    )

    val tagSummary: String
        get() = LOG_TAGS.joinToString(", ")

    fun matches(line: String): Boolean {
        if (line.contains(APP_PACKAGE)) return true
        return LOG_TAGS.any { tag -> matchesTag(line, tag) }
    }

    fun filter(content: String): String {
        return content.lineSequence()
            .filter(::matches)
            .joinToString("\n")
    }

    private fun matchesTag(line: String, tag: String): Boolean {
        if (line.contains(" $tag:")) return true
        if (line.contains("\t$tag:")) return true
        if (line.startsWith("$tag:")) return true
        if (line.contains("[$tag]")) return true
        return false
    }
}
