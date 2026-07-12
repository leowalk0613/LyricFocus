package com.leowalk.LyricFocus.lyric

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.leowalk.LyricFocus.FocusPreferences
import java.io.File

object LocalLrcStore {

    data class LrcFileRef(
        val name: String,
        val readText: () -> String?
    )

    fun getLocationLabel(context: Context): String {
        FocusPreferences.getLocalLrcTreeUri(context)?.let { uriString ->
            val name = DocumentFile.fromTreeUri(context, Uri.parse(uriString))?.name
            return if (name.isNullOrBlank()) "已选择文件夹" else "已选择：$name"
        }
        return "应用目录：lyrics"
    }

    fun getBootstrapDirectory(context: Context): File {
        val directory = FocusPreferences.getDefaultLocalLrcDirectoryFile(context)
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }

    fun listLrcFiles(context: Context): List<LrcFileRef> {
        FocusPreferences.getLocalLrcTreeUri(context)?.let { uriString ->
            return listFromDocumentTree(context, Uri.parse(uriString))
        }
        val directory = getBootstrapDirectory(context)
        return directory.listFiles { file ->
            file.isFile && file.extension.equals("lrc", ignoreCase = true)
        }?.map { file ->
            LrcFileRef(file.name) {
                readFileText(file)
            }
        }.orEmpty()
    }

    fun findBestMatch(context: Context, title: String, artist: String): LrcFileRef? {
        val refs = listLrcFiles(context)
        if (refs.isEmpty()) return null
        var best: LrcFileRef? = null
        var bestScore = Int.MIN_VALUE
        for (ref in refs) {
            val score = LocalLrcMatcher.scoreFileName(
                fileStem = ref.name.substringBeforeLast('.', ref.name),
                title = title,
                artist = artist
            )
            if (score > bestScore) {
                bestScore = score
                best = ref
            }
        }
        return best.takeIf { bestScore > 0 }
    }

    fun copyBundledLyricsIfNeeded(context: Context) {
        val assetManager = context.assets
        val bundled = runCatching { assetManager.list("lyrics")?.toList().orEmpty() }
            .getOrDefault(emptyList())
        if (bundled.isEmpty()) return

        FocusPreferences.getLocalLrcTreeUri(context)?.let { uriString ->
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(uriString)) ?: return
            for (name in bundled) {
                if (!name.endsWith(".lrc", ignoreCase = true)) continue
                if (tree.findFile(name) != null) continue
                runCatching {
                    tree.createFile("application/octet-stream", name)?.let { doc ->
                        assetManager.open("lyrics/$name").use { input ->
                            context.contentResolver.openOutputStream(doc.uri)?.use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            return
        }

        val directory = getBootstrapDirectory(context)
        for (name in bundled) {
            if (!name.endsWith(".lrc", ignoreCase = true)) continue
            val target = File(directory, name)
            if (target.exists()) continue
            runCatching {
                assetManager.open("lyrics/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }

    fun hasAnyLrcFile(context: Context): Boolean = listLrcFiles(context).isNotEmpty()

    private fun listFromDocumentTree(context: Context, treeUri: Uri): List<LrcFileRef> {
        val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<LrcFileRef>()
        collectDocumentLrcFiles(context, tree, results)
        return results
    }

    private fun collectDocumentLrcFiles(
        context: Context,
        node: DocumentFile,
        out: MutableList<LrcFileRef>
    ) {
        for (child in node.listFiles()) {
            if (child.isDirectory) {
                collectDocumentLrcFiles(context, child, out)
                continue
            }
            val name = child.name ?: continue
            if (!name.endsWith(".lrc", ignoreCase = true)) continue
            out.add(
                LrcFileRef(name) {
                    readDocumentText(context, child.uri)
                }
            )
        }
    }

    private fun readDocumentText(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_16).readText()
                }
            }.getOrNull()
    }

    private fun readFileText(file: File): String? {
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            ?: runCatching { file.readText(Charsets.UTF_16) }.getOrNull()
    }
}
