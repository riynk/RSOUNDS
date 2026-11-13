package code.name.monkey.retromusic.lyrics

import android.text.InputType
import android.graphics.Typeface
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import code.name.monkey.retromusic.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Translation actions are deliberately kept inside the translation icon, not the main settings page. */
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

        val visibilityAction = if (LyricsPreferences.translationVisible(context)) {
            R.string.lyrics_translation_action_hide
        } else {
            R.string.lyrics_translation_action_show
        }
        val options = arrayOf(
            context.getString(R.string.lyrics_translation_action_translate),
            context.getString(visibilityAction),
            context.getString(R.string.pref_lyrics_translation_language),
            context.getString(R.string.lyrics_translation_action_delete)
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_translate_lyrics)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> translateNow(fragment, lines)
                    1 -> LyricsPreferences.setTranslationVisible(
                        context, !LyricsPreferences.translationVisible(context)
                    )
                    2 -> editLanguage(fragment)
                    3 -> confirmDelete(fragment)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun translateNow(fragment: Fragment, lines: List<String>) {
        val context = fragment.requireContext()
        val sourceText = lines.joinToString("\n").trim()
        val progress = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.action_translate_lyrics)
            .setMessage(R.string.lyrics_translation_loading)
            .setCancelable(false)
            .show()

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                val translatedText = LyricsPreferences.translate(context, sourceText, force = true).orEmpty()
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

                val target = LyricsPreferences.translationLanguage(context)
                val translatedLines = translatedText.lines()
                lines.forEachIndexed { index, source ->
                    val translated = translatedLines.getOrNull(index).orEmpty()
                    if (source.isNotBlank() && translated.isNotBlank()) {
                        LyricsPreferences.saveTranslation(context, source, target, translated)
                    }
                }
                LyricsPreferences.setTranslationVisible(context, true)

                val content = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(20, 12, 20, 12)
                }
                content.addView(TextView(context).apply {
                    text = context.getString(R.string.lyrics_translated_text)
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    setPadding(0, 4, 0, 8)
                })
                content.addView(TextView(context).apply {
                    text = translatedText
                    textSize = 16f
                    setLineSpacing(4f, 1.08f)
                })
                val scroll = ScrollView(context).apply {
                    isFillViewport = true
                    addView(content)
                }
                MaterialAlertDialogBuilder(context)
                    .setTitle(context.getString(R.string.action_translate_lyrics) + " · " + target.uppercase())
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

    private fun editLanguage(fragment: Fragment) {
        val context = fragment.requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            setText(LyricsPreferences.translationLanguage(context))
            setSelection(text.length)
        }
        val container = LinearLayout(context).apply {
            setPadding(24, 8, 24, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.pref_lyrics_translation_language)
            .setMessage(R.string.lyrics_translation_language_prompt)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val language = input.text.toString().trim()
                if (language.isEmpty() || language.matches(Regex("[A-Za-z]{2,3}([-_][A-Za-z]{2,8})?"))) {
                    LyricsPreferences.setTranslationLanguage(context, language)
                }
            }
            .show()
    }

    private fun confirmDelete(fragment: Fragment) {
        val context = fragment.requireContext()
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.lyrics_translation_action_delete)
            .setMessage(R.string.lyrics_translation_delete_confirm)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.lyrics_translation_action_delete) { _, _ ->
                LyricsPreferences.clearTranslationCache(context)
                LyricsPreferences.setTranslationVisible(context, false)
            }
            .show()
    }
}
