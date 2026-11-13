/*
 * Copyright (c) 2020 Hemanth Savarla.
 *
 * Licensed under the GNU General Public License v3
 *
 * This is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 */
package code.name.monkey.retromusic.fragments.lyrics

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.app.Activity
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.util.TypedValue
import android.view.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.navigation.fragment.findNavController
import androidx.transition.Fade
import code.name.monkey.appthemehelper.common.ATHToolbarActivity
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.appthemehelper.util.VersionUtils
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.activities.tageditor.TagWriter
import code.name.monkey.retromusic.databinding.FragmentLyricsBinding
import code.name.monkey.retromusic.extensions.accentColor
import code.name.monkey.retromusic.extensions.materialDialog
import code.name.monkey.retromusic.extensions.openUrl
import code.name.monkey.retromusic.extensions.uri
import code.name.monkey.retromusic.fragments.base.AbsMainActivityFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.helper.MusicProgressViewUpdateHelper
import code.name.monkey.retromusic.lyrics.LrcView
import code.name.monkey.retromusic.lyrics.LyricsPreferences
import code.name.monkey.retromusic.lyrics.LyricsTranslationDialog
import code.name.monkey.retromusic.glide.BlurTransformation
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.simpleSongCoverOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.artistImageOptions
import code.name.monkey.retromusic.glide.crossfadeListener
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Artist
import com.bumptech.glide.Glide
import code.name.monkey.retromusic.model.AudioTagInfo
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.FileUtils
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.UriUtil
import com.afollestad.materialdialogs.input.input
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.collections.set

class LyricsFragment : AbsMainActivityFragment(R.layout.fragment_lyrics),
    MusicProgressViewUpdateHelper.Callback, SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentLyricsBinding? = null
    private val binding get() = _binding!!
    private lateinit var song: Song

    private lateinit var normalLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>
    private lateinit var editSyncedLyricsLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var cacheFile: File
    private var syncedLyrics: String = ""
    private lateinit var syncedFileUri: Uri

    private var lyricsType: LyricsType = LyricsType.NORMAL_LYRICS

    private val googleSearchLrcUrl: String
        get() {
            var baseUrl = "http://www.google.com/search?"
            var query = song.title + "+" + song.artistName
            query = "q=" + query.replace(" ", "+") + " lyrics"
            baseUrl += query
            return baseUrl
        }

    private lateinit var updateHelper: MusicProgressViewUpdateHelper
    private var translationJob: Job? = null
    private var translationSongPath: String? = null
    private var originalNormalLyrics: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Normal lyrics launcher
        normalLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    FileUtils.copyFileToUri(requireContext(), cacheFile, song.uri)
                }
            }
        editSyncedLyricsLauncher =
            registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
                if (it.resultCode == Activity.RESULT_OK) {
                    requireContext().contentResolver.openOutputStream(syncedFileUri)?.use { os ->
                        (os as FileOutputStream).channel.truncate(0)
                        os.write(syncedLyrics.toByteArray())
                        os.flush()
                    }
                }
            }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        enterTransition = Fade()
        exitTransition = Fade()
        _binding = FragmentLyricsBinding.bind(view)
        updateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        updateTitleSong()
        setupLyricsView()
        updateLyricsBackground()
        loadLyrics()

        setupWakelock()
        setupViews()
        setupToolbar()
    }

    private fun updateLyricsBackground() {
        val current = MusicPlayerRemote.currentSong
        val blur = BlurTransformation.Builder(requireContext())
            .blurRadius(PreferenceUtil.blurAmount.toFloat()).build()
        val metadata = Glide.with(this).load(RetroGlideExtension.getSongModel(current))
            .simpleSongCoverOptions(current).transform(blur)
        val source = LyricsPreferences.backgroundSource(requireContext())
        val artist = Artist(current.artistId, listOf(Album(current.albumId, listOf(current))))
        val request = if (source == "artist" || source == "auto") {
            Glide.with(this).load(RetroGlideExtension.getArtistModel(artist))
                .artistImageOptions(artist).transform(blur).error(metadata)
        } else metadata
        request.into(binding.lyricsBackground)
    }

    private fun resolveThemeColor(attribute: Int, fallback: Int): Int {
        val value = TypedValue()
        if (!requireContext().theme.resolveAttribute(attribute, value, true)) return fallback
        return if (value.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId)
        } else {
            value.data
        }
    }

    private fun setupLyricsView() {
        // Lyrics use a fixed neutral contrast: current line white, inactive lines visibly dimmed.
        val primaryTextColor = Color.WHITE
        val secondaryTextColor = Color.argb(112, 255, 255, 255)
        binding.lyricsView.apply {
            setLyricStyle(LyricsPreferences.fontScale(requireContext()), LyricsPreferences.lineSpacing(requireContext()))
            setTimeOffsetMs(LyricsPreferences.offset(requireContext(), song))
            setTimeScale(LyricsPreferences.timeScale(requireContext(), song))
            setNormalColor(secondaryTextColor)
            setCurrentColor(primaryTextColor)
            setTranslationStyle(secondaryTextColor, 0.86f)
            setTimeTextColor(accentColor())
            setTimelineColor(accentColor())
            setTimelineTextColor(accentColor())
            setDraggable(true, LrcView.OnPlayClickListener {
                MusicPlayerRemote.seekTo(it.toInt())
                return@OnPlayClickListener true
            })
        }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        binding.lyricsView.updateTime(progress.toLong())
        startLyricTranslations()
    }

    private fun startLyricTranslations() {
        if (!binding.lyricsView.hasLrc()) return
        if (!LyricsPreferences.translationVisible(requireContext())) {
            binding.lyricsView.clearTranslations()
            translationSongPath = song.data
            return
        }
        if (translationSongPath == song.data) return
        translationSongPath = song.data
        val lines = binding.lyricsView.lyricTexts
        val targetLanguage = LyricsPreferences.translationLanguage(requireContext())
        val translatedLines = LyricsPreferences.cachedTranslation(
            requireContext(), lines.joinToString("\n"), targetLanguage
        )?.lines().orEmpty()
        // The player and lyrics page may load equivalent lyrics from different sources.
        // Fall back to per-line cache entries so translations remain visible across both.
        binding.lyricsView.setTranslations(
            lines.mapIndexed { index, line ->
                translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() }
                    ?: LyricsPreferences.cachedTranslation(requireContext(), line, targetLanguage).orEmpty()
            }
        )
    }

    private fun setupViews() {
        binding.editButton.accentColor()
        binding.editButton.setOnClickListener {
            when (lyricsType) {
                LyricsType.SYNCED_LYRICS -> {
                    editSyncedLyrics()
                }
                LyricsType.NORMAL_LYRICS -> {
                    editNormalLyrics()
                }
            }
        }
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        translationJob?.cancel()
        translationSongPath = null
        updateTitleSong()
        updateLyricsBackground()
        binding.lyricsView.setTimeOffsetMs(LyricsPreferences.offset(requireContext(), song))
        binding.lyricsView.setTimeScale(LyricsPreferences.timeScale(requireContext(), song))
        loadLyrics()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateTitleSong()
        updateLyricsBackground()
        binding.lyricsView.setTimeOffsetMs(LyricsPreferences.offset(requireContext(), song))
        binding.lyricsView.setTimeScale(LyricsPreferences.timeScale(requireContext(), song))
        loadLyrics()
    }

    private fun updateTitleSong() {
        song = MusicPlayerRemote.currentSong
    }

    private fun setupToolbar() {
        mainActivity.setSupportActionBar(binding.toolbar)
        ToolbarContentTintHelper.colorBackButton(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupWakelock() {
        requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_lyrics, menu)
        menu.findItem(R.id.action_translate_lyrics)?.isVisible =
            LyricsPreferences.translationIconEnabled(requireContext())
        ToolbarContentTintHelper.handleOnCreateOptionsMenu(
            requireContext(),
            binding.toolbar,
            menu,
            ATHToolbarActivity.getToolbarBackgroundColor(binding.toolbar)
        )
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_search -> openUrl(googleSearchLrcUrl)
            R.id.action_sync -> adjustLyricsSync()
            R.id.action_sync_rate -> adjustLyricsRate()
            R.id.action_translate_lyrics -> {
                val lines = if (lyricsType == LyricsType.SYNCED_LYRICS) {
                    binding.lyricsView.lyricTexts
                } else {
                    originalNormalLyrics.lines()
                }
                LyricsTranslationDialog.show(this, lines)
            }
            else -> return false
        }
        return true
    }

    private fun adjustLyricsRate() {
        val currentScale = LyricsPreferences.timeScale(requireContext(), song)
        materialDialog().show {
            title(res = R.string.pref_lyrics_sync_rate)
            input(
                hintRes = R.string.pref_lyrics_sync_rate,
                prefill = currentScale.toString(),
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            ) { _, value ->
                val scale = value.toString().toFloatOrNull() ?: return@input
                LyricsPreferences.setTimeScale(requireContext(), song, scale)
                binding.lyricsView.setTimeScale(scale)
            }
            positiveButton(res = R.string.save)
            negativeButton(res = android.R.string.cancel)
        }
    }

    private fun adjustLyricsSync() {
        val currentOffset = LyricsPreferences.offset(requireContext(), song)
        materialDialog().show {
            title(res = R.string.pref_lyrics_sync_adjust)
            input(
                hintRes = R.string.pref_lyrics_sync_adjust,
                prefill = currentOffset.toString(),
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_SIGNED
            ) { _, value ->
                val offset = value.toString().toLongOrNull() ?: return@input
                LyricsPreferences.setOffset(requireContext(), song, offset)
                binding.lyricsView.setTimeOffsetMs(offset)
            }
            positiveButton(res = R.string.save)
            negativeButton(res = android.R.string.cancel)
        }
    }

    @SuppressLint("CheckResult")
    private fun editNormalLyrics(lyrics: String? = null) {
        val file = File(song.data)
        val content = lyrics ?: try {
            AudioFileIO.read(file).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }

        val song = song

        materialDialog().show {
            title(res = R.string.edit_normal_lyrics)
            input(
                hintRes = R.string.paste_lyrics_here,
                prefill = content,
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            ) { _, input ->
                val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
                fieldKeyValueMap[FieldKey.LYRICS] = input.toString()
                viewLifecycleOwner.lifecycleScope.launch {
                    if (VersionUtils.hasR()) {
                        cacheFile = TagWriter.writeTagsToFilesR(
                            requireContext(), AudioTagInfo(
                                listOf(song.data), fieldKeyValueMap, null
                            )
                        )[0]
                        val pendingIntent =
                            MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(song.uri)
                            )

                        normalLyricsLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent).build()
                        )
                    } else {
                        TagWriter.writeTagsToFiles(
                            requireContext(), AudioTagInfo(
                                listOf(song.data), fieldKeyValueMap, null
                            )
                        )
                    }
                }
            }
            positiveButton(res = R.string.save) {
                loadNormalLyrics()
            }
            negativeButton(res = android.R.string.cancel)
        }
    }


    @SuppressLint("CheckResult")
    private fun editSyncedLyrics(lyrics: String? = null) {
        val content = lyrics ?: LyricUtil.getStringFromLrc(LyricUtil.getSyncedLyricsFile(song))

        val song = song
        materialDialog().show {
            title(res = R.string.edit_synced_lyrics)
            input(
                hintRes = R.string.paste_timeframe_lyrics_here,
                prefill = content,
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
            ) { _, input ->
                if (VersionUtils.hasR()) {
                    syncedLyrics = input.toString()
                    val lrcFile = LyricUtil.getSyncedLyricsFile(song)
                    if (lrcFile?.exists() == true) {
                        syncedFileUri =
                            UriUtil.getUriFromPath(requireContext(), lrcFile.absolutePath)
                        val pendingIntent =
                            MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(syncedFileUri)
                            )
                        editSyncedLyricsLauncher.launch(
                            IntentSenderRequest.Builder(pendingIntent).build()
                        )
                    } else {
                        val fieldKeyValueMap = EnumMap<FieldKey, String>(FieldKey::class.java)
                        fieldKeyValueMap[FieldKey.LYRICS] = input.toString()
                        viewLifecycleOwner.lifecycleScope.launch {
                            cacheFile = TagWriter.writeTagsToFilesR(
                                requireContext(),
                                AudioTagInfo(listOf(song.data), fieldKeyValueMap, null)
                            )[0]
                            val pendingIntent = MediaStore.createWriteRequest(
                                requireContext().contentResolver,
                                listOf(song.uri)
                            )

                            normalLyricsLauncher.launch(
                                IntentSenderRequest.Builder(pendingIntent).build()
                            )
                        }
                    }
                } else {
                    LyricUtil.writeLrc(song, input.toString())
                }
            }
            positiveButton(res = R.string.save) {
                loadLRCLyrics()
            }
            negativeButton(res = android.R.string.cancel)
        }
    }

    private fun loadNormalLyrics() {
        val file = File(song.data)
        val lyrics = try {
            AudioFileIO.read(file).tagOrCreateDefault.getFirst(FieldKey.LYRICS)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        binding.normalLyrics.isVisible = !lyrics.isNullOrEmpty()
        binding.noLyricsFound.isVisible = lyrics.isNullOrEmpty()
        originalNormalLyrics = lyrics.orEmpty()
        binding.normalLyrics.text = lyrics
        val scale = LyricsPreferences.fontScale(requireContext())
        binding.normalLyrics.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * scale)
        binding.normalLyrics.setLineSpacing(0f, LyricsPreferences.lineSpacing(requireContext()))
        translationJob?.cancel()
        translationSongPath = null
        if (LyricsPreferences.translationVisible(requireContext()) && !lyrics.isNullOrBlank()) {
            val targetLanguage = LyricsPreferences.translationLanguage(requireContext())
            val sourceLines = lyrics.lines()
            val translatedLines = LyricsPreferences.cachedTranslation(
                requireContext(), lyrics, targetLanguage
            )?.lines().orEmpty()
            val lineTranslations = sourceLines.mapIndexed { index, line ->
                translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() }
                    ?: LyricsPreferences.cachedTranslation(requireContext(), line, targetLanguage).orEmpty()
            }
            if (lineTranslations.any { it.isNotBlank() }) {
                val rendered = SpannableStringBuilder()
                sourceLines.forEachIndexed { index, line ->
                    if (index > 0) rendered.append("\n\n")
                    rendered.append(line)
                    val translated = lineTranslations.getOrNull(index).orEmpty()
                    if (translated.isNotBlank()) {
                        rendered.append("\n")
                        val start = rendered.length
                        rendered.append(translated)
                        rendered.setSpan(RelativeSizeSpan(0.86f), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        rendered.setSpan(StyleSpan(Typeface.ITALIC), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        rendered.setSpan(ForegroundColorSpan(Color.argb(160, 255, 255, 255)), start, rendered.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                binding.normalLyrics.text = rendered
            }
        }
    }

    /**
     * @return success
     */
    private fun loadLRCLyrics(): Boolean {
        val lrcFile = LyricUtil.getSyncedLyricsFile(song)
        if (lrcFile != null) {
            binding.lyricsView.loadLrc(lrcFile)
        } else {
            val embeddedLyrics = LyricUtil.getEmbeddedSyncedLyrics(song.data)
            if (embeddedLyrics != null) {
                binding.lyricsView.loadLrc(embeddedLyrics)
            } else {
                binding.lyricsView.setLabel(getString(R.string.empty))
                return false
            }
        }
        return true
    }

    private fun loadLyrics() {
        lyricsType = if (!loadLRCLyrics()) {
            binding.lyricsView.isVisible = false
            loadNormalLyrics()
            LyricsType.NORMAL_LYRICS
        } else {
            binding.normalLyrics.isVisible = false
            binding.noLyricsFound.isVisible = false
            binding.lyricsView.isVisible = true
            LyricsType.SYNCED_LYRICS
        }
    }

    override fun onResume() {
        super.onResume()
        updateHelper.start()
        binding.toolbar.menu.findItem(R.id.action_translate_lyrics)?.isVisible =
            LyricsPreferences.translationIconEnabled(requireContext())
        PreferenceManager.getDefaultSharedPreferences(requireContext()).registerOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "lyrics_translation_enabled" -> {
                binding.toolbar.menu.findItem(R.id.action_translate_lyrics)?.isVisible =
                    LyricsPreferences.translationIconEnabled(requireContext())
            }
            "lyrics_translation_visible", "lyrics_translation_language" -> {
                translationJob?.cancel()
                translationSongPath = null
                binding.lyricsView.clearTranslations()
                if (lyricsType == LyricsType.NORMAL_LYRICS) loadNormalLyrics()
                else startLyricTranslations()
            }
            "lyrics_font_scale", "lyrics_line_spacing" -> {
                binding.lyricsView.setLyricStyle(LyricsPreferences.fontScale(requireContext()), LyricsPreferences.lineSpacing(requireContext()))
                binding.normalLyrics.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f * LyricsPreferences.fontScale(requireContext()))
                binding.normalLyrics.setLineSpacing(0f, LyricsPreferences.lineSpacing(requireContext()))
            }
            "lyrics_background_source" -> updateLyricsBackground()
        }
    }

    override fun onPause() {
        super.onPause()
        updateHelper.stop()
        PreferenceManager.getDefaultSharedPreferences(requireContext()).unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (MusicPlayerRemote.playingQueue.isNotEmpty())
            mainActivity.expandPanel()
        translationJob?.cancel()
        _binding = null
    }

    enum class LyricsType {
        NORMAL_LYRICS,
        SYNCED_LYRICS
    }
}
