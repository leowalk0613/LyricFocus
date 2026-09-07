# Xposed / LSPosed
-keep class com.leowalk.LyricFocus.xposed.** { *; }
-dontwarn com.leowalk.LyricFocus.xposed.**

# Keep LyricFocus app classes (used by Xposed module process)
-keep class com.leowalk.LyricFocus.FocusPreferences { *; }
-keep class com.leowalk.LyricFocus.FocusStyleSnapshot { *; }

# Keep data classes sent across processes (JSON serialization)
-keep class com.leowalk.LyricFocus.lyric.LyricLine { *; }
-keep class com.leowalk.LyricFocus.lyric.LyricInfo { *; }
-keep class com.leowalk.LyricFocus.lyric.WordTime { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# SuperLyric
-keep class github.hochenx.** { *; }
-dontwarn github.hochenx.**

# Lyricon
-keep class com.proify.lyricon.** { *; }
-dontwarn com.proify.lyricon.**

# HyperFocusApi
-keep class com.github.ghhccghk.** { *; }
-dontwarn com.github.ghhccghk.**

# Palette (already covered by Android)
# Material
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# JSON
-keep class org.json.** { *; }

# Missing in framework
-dontwarn android.os.ServiceManager
