package com.leowalk.LyricFocus.lyric

interface LyricProvider {
    val id: String
    val name: String
    suspend fun searchLyric(title: String, artist: String = "", album: String = ""): LyricInfo?
}
