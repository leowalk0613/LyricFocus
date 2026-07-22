package com.leowalk.LyricFocus.lyric

import java.io.File
import java.text.Normalizer

object LocalLrcMatcher {

    private val INVALID_FILE_CHARS = Regex("""[\\/:*?"<>|]""")
    private val TOKEN_SPLITTER = Regex("""[\s_\-–—]+""")
    private val STEM_SEPARATOR = Regex("""\s[-–—]\s""")

    fun findBestLrcFile(directory: File, title: String, artist: String): File? {
        if (!directory.isDirectory) return null
        val files = directory.listFiles { file ->
            file.isFile && file.extension.equals("lrc", ignoreCase = true)
        } ?: return null
        if (files.isEmpty()) return null

        val candidates = buildCandidateNames(title, artist)
        for (candidate in candidates) {
            val exact = files.find { it.nameWithoutExtension.equals(candidate, ignoreCase = true) }
            if (exact != null) return exact
        }

        var bestFile: File? = null
        var bestScore = Int.MIN_VALUE
        for (file in files) {
            val score = scoreFileName(file.nameWithoutExtension, title, artist)
            if (score > bestScore) {
                bestScore = score
                bestFile = file
            }
        }
        return bestFile.takeIf { bestScore > 0 }
    }

    fun scoreFileName(fileStem: String, title: String, artist: String): Int {
        if (fileStem.isBlank()) return 0
        val normalizedStem = normalize(fileStem)
        val normalizedTitle = normalizeLrcTitle(LyricSearchHelper.normalizeTitleForSearch(title, artist))
        val normalizedRawTitle = normalizeLrcTitle(title)
        val artistParts = LyricSearchHelper.splitArtists(artist).map(::normalize).filter { it.isNotBlank() }
        val normalizedArtist = normalize(artist)
        val normalizedArtistSpaced = normalizeArtistSpaced(artist)

        var score = 0
        if (normalizedTitle.isNotBlank()) {
            score = maxOf(score, scoreContains(normalizedStem, normalizedTitle))
            if (normalizedRawTitle != normalizedTitle) {
                score = maxOf(score, scoreContains(normalizedStem, normalizedRawTitle))
            }
        }
        if (normalizedArtist.isNotBlank()) {
            score = maxOf(score, scoreContains(normalizedStem, normalizedArtist) - 2)
        }
        if (normalizedArtistSpaced.isNotBlank()) {
            score = maxOf(score, scoreContains(normalizedStem, normalizedArtistSpaced) - 1)
        }
        for (part in artistParts) {
            score = maxOf(score, scoreContains(normalizedStem, part) - 1)
        }

        splitStem(fileStem)?.let { (left, right) ->
            score = maxOf(
                score,
                scorePartPair(left, right, normalizedTitle, normalizedRawTitle, artistParts, normalizedArtist, normalizedArtistSpaced)
            )
            score = maxOf(
                score,
                scorePartPair(right, left, normalizedTitle, normalizedRawTitle, artistParts, normalizedArtist, normalizedArtistSpaced)
            )
        }

        val titleHit = titlesMatch(normalizedStem, normalizedTitle, normalizedRawTitle)
        val artistHit = artistMatches(normalizedStem, artistParts, normalizedArtist, normalizedArtistSpaced)

        if (titleHit && artistHit) {
            score = maxOf(score, 24)
        } else if (titleHit) {
            score = maxOf(score, 12)
        }

        val stemTokens = tokenize(normalizedStem)
        val titleTokens = tokenize(normalizedTitle) + tokenize(normalizedRawTitle)
        if (titleTokens.isNotEmpty() && titleTokens.all { token -> stemTokens.contains(token) }) {
            score = maxOf(score, 10)
        }
        return score
    }

    fun sanitizeFileStem(value: String): String {
        return INVALID_FILE_CHARS.replace(value.trim(), "_")
    }

    fun buildCandidateNames(title: String, artist: String): List<String> {
        val cleanTitle = sanitizeFileStem(LyricSearchHelper.normalizeTitleForSearch(title, artist))
        val cleanArtist = sanitizeFileStem(artist)
        val rawTitle = sanitizeFileStem(title)
        return buildList {
            if (cleanTitle.isNotBlank() && cleanArtist.isNotBlank()) {
                add("$cleanTitle - $cleanArtist")
                add("$cleanArtist - $cleanTitle")
            }
            if (rawTitle.isNotBlank() && rawTitle != cleanTitle && cleanArtist.isNotBlank()) {
                add("$rawTitle - $cleanArtist")
                add("$cleanArtist - $rawTitle")
            }
            if (cleanTitle.isNotBlank()) add(cleanTitle)
            if (rawTitle.isNotBlank() && rawTitle != cleanTitle) add(rawTitle)
        }.distinct()
    }

    private fun splitStem(stem: String): Pair<String, String>? {
        val parts = STEM_SEPARATOR.split(stem.trim(), limit = 2)
        if (parts.size != 2) return null
        val left = parts[0].trim()
        val right = parts[1].trim()
        if (left.isBlank() || right.isBlank()) return null
        return left to right
    }

    private fun scorePartPair(
        first: String,
        second: String,
        normalizedTitle: String,
        normalizedRawTitle: String,
        artistParts: List<String>,
        normalizedArtist: String,
        normalizedArtistSpaced: String
    ): Int {
        if (!titlesMatch(normalize(second), normalizedTitle, normalizedRawTitle)) {
            return 0
        }
        if (!artistMatches(normalize(first), artistParts, normalizedArtist, normalizedArtistSpaced)) {
            return 0
        }
        return 24
    }

    private fun titlesMatch(
        candidate: String,
        normalizedTitle: String,
        normalizedRawTitle: String
    ): Boolean {
        if (normalizedTitle.isBlank() && normalizedRawTitle.isBlank()) return false
        val normCandidate = normalizeLrcTitle(candidate)
        if (normalizedTitle.isNotBlank()) {
            if (normCandidate == normalizedTitle ||
                normCandidate.contains(normalizedTitle) ||
                normalizedTitle.contains(normCandidate)
            ) {
                return true
            }
        }
        if (normalizedRawTitle.isNotBlank() && normalizedRawTitle != normalizedTitle) {
            if (normCandidate == normalizedRawTitle ||
                normCandidate.contains(normalizedRawTitle) ||
                normalizedRawTitle.contains(normCandidate)
            ) {
                return true
            }
        }
        return false
    }

    private fun artistMatches(
        candidate: String,
        artistParts: List<String>,
        normalizedArtist: String,
        normalizedArtistSpaced: String
    ): Boolean {
        if (artistParts.any { part ->
                part.length >= 2 && (candidate.contains(part) || part.contains(candidate))
            }
        ) {
            return true
        }
        if (normalizedArtist.length >= 2 &&
            (candidate.contains(normalizedArtist) || normalizedArtist.contains(candidate))
        ) {
            return true
        }
        if (normalizedArtistSpaced.length >= 3 && candidate.contains(normalizedArtistSpaced)) {
            return true
        }
        return false
    }

    private fun scoreContains(haystack: String, needle: String): Int {
        if (needle.isBlank()) return 0
        return when {
            haystack == needle -> 20
            haystack.contains(needle) && needle.length >= 2 -> 14
            needle.contains(haystack) && haystack.length >= 2 -> 12
            else -> 0
        }
    }

    private fun normalizeLrcTitle(value: String): String {
        var t = normalize(value)
        while (t.length > 1 && t.endsWith('.') && t.dropLast(1).endsWith('.')) {
            t = t.dropLast(1)
        }
        return t
    }

    private fun normalizeArtistSpaced(artist: String): String {
        return normalize(
            artist.replace('/', ' ')
                .replace("\\s+".toRegex(), " ")
                .trim()
        )
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase()
            .replace("　", " ")
            .trim()
    }

    private fun tokenize(value: String): Set<String> {
        return TOKEN_SPLITTER.split(value)
            .map { it.trim() }
            .filter { it.length >= 2 }
            .toSet()
    }
}
