package code.name.monkey.retromusic.lyrics

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import code.name.monkey.retromusic.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Persistent lyric presentation, per-track synchronization and translation cache. */
object LyricsPreferences {
    private const val FONT_SCALE = "lyrics_font_scale"
    private const val LINE_SPACING = "lyrics_line_spacing"
    private const val TRANSLATION_ENABLED = "lyrics_translation_enabled"
    private const val TRANSLATION_LANGUAGE = "lyrics_translation_language"
    private const val BACKGROUND_SOURCE = "lyrics_background_source"
    private const val OFFSET_PREFIX = "lyrics_offset_"
    private const val CACHE_PREFIX = "lyrics_translation_cache_"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun fontScale(context: Context) = prefs(context).(getInt(FONT_SCALE, 100) / 100f).coerceIn(.75f, 1.5f)
    fun lineSpacing(context: Context) = prefs(context).(getInt(LINE_SPACING, 100) / 100f).coerceIn(.8f, 1.8f)
    fun translationEnabled(context: Context) = prefs(context).getBoolean(TRANSLATION_ENABLED, false)
    fun translationLanguage(context: Context) = prefs(context).getString(TRANSLATION_LANGUAGE, "")?.takeIf { it.isNotBlank() } ?: java.util.Locale.getDefault().language
    fun backgroundSource(context: Context) = prefs(context).getString(BACKGROUND_SOURCE, "auto") ?: "auto"

    private fun songKey(song: Song): String {
        val identity = if (song.data.isNotBlank()) song.data else "${song.id}:${song.albumId}"
        return OFFSET_PREFIX + identity.hashCode().toUInt().toString(16)
    }

    fun offset(context: Context, song: Song) = prefs(context).getLong(songKey(song), 0L)
    fun setOffset(context: Context, song: Song, offsetMs: Long) {
        prefs(context).edit { putLong(songKey(song), offsetMs.coerceIn(-30000L, 30000L)) }
    }

    private fun cacheKey(text: String, target: String) =
        CACHE_PREFIX + (text.trim() + "|" + target).hashCode().toUInt().toString(16)

    fun cachedTranslation(context: Context, text: String, target: String): String? =
        prefs(context).getString(cacheKey(text, target), null)

    fun saveTranslation(context: Context, text: String, target: String, translated: String) {
        prefs(context).edit { putString(cacheKey(text, target), translated) }
    }

    /** Uses LibreTranslate-compatible API; callers must invoke off the main thread. */
    suspend fun translate(context: Context, text: String, source: String = "auto"): String? =
        withContext(Dispatchers.IO) {
            val target = translationLanguage(context)
            if (!translationEnabled(context) || text.isBlank()) return@withContext null
            cachedTranslation(context, text, target)?.let { return@withContext it }
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("https://libretranslate.de/translate").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val body = JSONObject().put("q", text).put("source", source)
                    .put("target", target).put("format", "text").toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) return@withContext null
                val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optString("translatedText").takeIf { it.isNotBlank() }
                if (result != null) saveTranslation(context, text, target, result)
                result
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }
}
