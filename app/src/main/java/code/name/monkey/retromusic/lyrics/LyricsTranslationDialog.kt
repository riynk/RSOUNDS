package code.name.monkey.retromusic.lyrics

import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import code.name.monkey.retromusic.R
import kotlinx.coroutines.launch

/** Presents an on-demand translation without changing the user's automatic-translation setting. */
object LyricsTranslationDialog {
    fun show(fragment: Fragment, sourceLines: List<String>) {
        val context = fragment.requireContext()
        val lines = sourceLines.map { it.trim() }
        if (lines.none { it.isNotBlank() }) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.action_translate_lyrics)
                .setMessage(R.string.lyrics_translation_no_lyrics)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val progress = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_translate_lyrics)
            .setMessage(R.string.lyrics_translation_loading)
            .setCancelable(false)
            .show()

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Translate the complete lyric in one request to avoid mirror rate limits.
                val sourceText = lines.joinToString("\n").trim()
                val translatedText = LyricsPreferences.translate(
                    context, sourceText, force = true
                ).orEmpty()

                if (!fragment.isAdded) {
                    progress.dismiss()
                    return@launch
                }
                progress.dismiss()
                if (translatedText.isBlank()) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.action_translate_lyrics)
                        .setMessage(R.string.lyrics_translation_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    return@launch
                }

                val text = TextView(context).apply {
                    setPadding(24, 16, 24, 16)
                    textSize = 16f
                    this.text = sourceText + "\n\n──────────\n\n" + translatedText
                }
                val scroll = ScrollView(context).apply {
                    addView(text)
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.action_translate_lyrics) + " · " +
                        LyricsPreferences.translationLanguage(context).uppercase())
                    .setView(scroll)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            } catch (cancelled: CancellationException) {
                progress.dismiss()
                throw cancelled
            } catch (_: Exception) {
                progress.dismiss()
                if (fragment.isAdded) {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(R.string.action_translate_lyrics)
                        .setMessage(R.string.lyrics_translation_failed)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }
}
