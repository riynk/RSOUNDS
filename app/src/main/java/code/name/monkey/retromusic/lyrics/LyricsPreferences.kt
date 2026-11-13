package code.name.monkey.retromusic.lyrics

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import code.name.monkey.retromusic.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/** Persistent lyric presentation, per-track synchronization and translation cache. */
object LyricsPreferences {
    private const val FONT_SCALE = "lyrics_font_scale"
    private const val LINE_SPACING = "lyrics_line_spacing"
    private const val TRANSLATION_ENABLED = "lyrics_translation_enabled"
    private const val TRANSLATION_LANGUAGE = "lyrics_translation_language"
    private const val BACKGROUND_SOURCE = "lyrics_background_source"
    private const val OFFSET_PREFIX = "lyrics_offset_"
    private const val SCALE_PREFIX = "lyrics_time_scale_"
    private const val CACHE_PREFIX = "lyrics_translation_cache_"

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    fun fontScale(context: Context) = (prefs(context).getInt(FONT_SCALE, 100) / 100f).coerceIn(.75f, 1.5f)
    fun lineSpacing(context: Context) = (prefs(context).getInt(LINE_SPACING, 100) / 100f).coerceIn(.8f, 1.8f)
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

    private fun scaleKey(song: Song) = SCALE_PREFIX + songKey(song).removePrefix(OFFSET_PREFIX)
    fun timeScale(context: Context, song: Song) = prefs(context).getFloat(scaleKey(song), 1f).coerceIn(0.8f, 1.2f)
    fun setTimeScale(context: Context, song: Song, scale: Float) {
        prefs(context).edit { putFloat(scaleKey(song), scale.coerceIn(0.8f, 1.2f)) }
    }

    private fun cacheKey(text: String, source: String, target: String): String {
        val input = "$source|$target|${text.trim()}".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(input)
        val fingerprint = digest.take(16).joinToString("") { "%02x".format(it) }
        return CACHE_PREFIX + fingerprint
    }

    fun cachedTranslation(context: Context, text: String, target: String, source: String = "auto"): String? =
        prefs(context).getString(cacheKey(text, source, target), null)

    fun saveTranslation(context: Context, text: String, target: String, translated: String, source: String = "auto") {
        prefs(context).edit { putString(cacheKey(text, source, target), translated) }
    }

    /** Translates long lyrics in bounded chunks so public endpoints do not reject oversized requests. */
    suspend fun translate(context: Context, text: String, source: String = "auto", force: Boolean = false): String? =
        withContext(Dispatchers.IO) {
            val target = translationLanguage(context)
            if ((!translationEnabled(context) && !force) || text.isBlank()) return@withContext null
            cachedTranslation(context, text, target, source)?.let { return@withContext it }

            val chunks = splitForTranslation(text, 1800)
            val translatedChunks = ArrayList<String>(chunks.size)
            for (chunk in chunks) {
                val translated = cachedTranslation(context, chunk, target, source)
                    ?: translateChunk(chunk, source, target)
                    ?: return@withContext null
                saveTranslation(context, chunk, target, translated, source)
                translatedChunks += translated
            }
            translatedChunks.joinToString("\n").also {
                if (it.isNotBlank()) saveTranslation(context, text, target, it, source)
            }
        }

    private fun splitForTranslation(text: String, maxLength: Int): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        for (line in text.lines()) {
            if (line.length > maxLength) {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current.setLength(0)
                }
                var remaining = line
                while (remaining.length > maxLength) {
                    val splitAt = remaining.lastIndexOf(' ', maxLength).takeIf { it > maxLength / 2 } ?: maxLength
                    result += remaining.substring(0, splitAt).trim()
                    remaining = remaining.substring(splitAt).trimStart()
                }
                if (remaining.isNotEmpty()) result += remaining
            } else if (current.isNotEmpty() && current.length + line.length + 1 > maxLength) {
                result += current.toString()
                current.setLength(0)
                current.append(line)
            } else {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun translateChunk(text: String, source: String, target: String): String? {
        var googleConnection: HttpURLConnection? = null
        try {
            val encodedText = URLEncoder.encode(text, Charsets.UTF_8.name())
            val googleUrl = URL(
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$source&tl=$target&dt=t&q=$encodedText"
            )
            googleConnection = (googleUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            if (googleConnection.responseCode in 200..299) {
                val response = JSONArray(googleConnection.inputStream.bufferedReader().use { it.readText() })
                val segments = response.optJSONArray(0)
                val translated = buildString {
                    if (segments != null) {
                        for (index in 0 until segments.length()) {
                            append(segments.optJSONArray(index)?.optString(0).orEmpty())
                        }
                    }
                }.takeIf { it.isNotBlank() }
                if (translated != null) return translated
            } else {
                Log.w("LyricsTranslation", "Google translation failed (${googleConnection.responseCode})")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("LyricsTranslation", "Google translation request failed", error)
        } finally {
            googleConnection?.disconnect()
        }

        val body = JSONObject().put("q", text).put("source", source)
            .put("target", target).put("format", "text").toString()
        for (endpoint in listOf(
            "https://libretranslate.de/translate",
            "https://translate.argosopentech.com/translate",
            "https://trans.zillyhuhn.com/translate"
        )) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("User-Agent", "Rsounds")
                }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                if (connection.responseCode !in 200..299) {
                    Log.w("LyricsTranslation", "Translation endpoint failed: $endpoint (${connection.responseCode})")
                    continue
                }
                val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .optString("translatedText").takeIf { it.isNotBlank() }
                if (result != null) return result
                Log.w("LyricsTranslation", "Empty translation response from $endpoint")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("LyricsTranslation", "Translation request failed: $endpoint", error)
            } finally {
                connection?.disconnect()
            }
        }
        return null
    }
}