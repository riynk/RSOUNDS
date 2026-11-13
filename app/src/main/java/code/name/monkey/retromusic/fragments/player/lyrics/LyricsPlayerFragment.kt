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
package code.name.monkey.retromusic.fragments.player.lyrics

import android.content.SharedPreferences
import android.graphics.Color
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.NEW_BLUR_AMOUNT
import code.name.monkey.retromusic.SHOW_LYRICS
import code.name.monkey.retromusic.lyrics.LyricsPreferences
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentLyricsPlayerBinding
import code.name.monkey.retromusic.extensions.getSongInfo
import code.name.monkey.retromusic.extensions.getTintedDrawable
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.base.goToAlbum
import code.name.monkey.retromusic.fragments.base.goToArtist
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment
import code.name.monkey.retromusic.glide.BlurTransformation
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.simpleSongCoverOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.artistImageOptions
import code.name.monkey.retromusic.glide.crossfadeListener
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.util.LyricUtil
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.CoverLyricsType
import code.name.monkey.retromusic.util.PreferenceUtil.blurAmount
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class LyricsPlayerFragment : AbsPlayerFragment(R.layout.fragment_lyrics_player),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var lastRequest: RequestBuilder<Drawable>? = null

    override fun playerToolbar(): Toolbar {
        return binding.playerToolbar
    }

    private lateinit var playbackControlsFragment: code.name.monkey.retromusic.fragments.player.blur.BlurPlaybackControlsFragment

    private var lastColor: Int = 0

    private var _binding: FragmentLyricsPlayerBinding? = null
    private val binding get() = _binding!!


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentLyricsPlayerBinding.bind(view)
        setUpSubFragments()
        setUpPlayerToolbar()
        setUpBottomActions()
        updateSongHeader()
        binding.title.setOnClickListener { goToAlbum(requireActivity()) }
        binding.artist.setOnClickListener { goToArtist(requireActivity()) }
        // Keep the toolbar anchored at the fullscreen position in both fullscreen states.
        // Reserve only the physical display-cutout inset so the header never moves or covers a notch.
        binding.statusBarContainer.findViewById<View>(R.id.status_bar)?.isVisible = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.statusBarContainer) { container, insets ->
            // Keep the same status-bar reservation even when the system bar is hidden.
            // This prevents every constrained player element from jumping in fullscreen.
            val stableTop = insets.getInsetsIgnoringVisibility(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val cutout = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.displayCutout())
            container.updatePadding(
                left = cutout.left,
                top = stableTop.top,
                right = cutout.right
            )

            // Keep the window background edge-to-edge. Apply landscape cutout
            // protection only to the content panels, never to the root/background.
            binding.root.findViewById<View>(R.id.artworkSquareContainer)?.updatePadding(
                left = cutout.left,
                right = 0
            )
            binding.root.findViewById<View>(R.id.rightPanel)?.updatePadding(
                left = 0,
                right = cutout.right
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.statusBarContainer)
    }

    private fun setUpSubFragments() {
        playbackControlsFragment = whichFragment(R.id.playbackControlsFragment)
        val playerAlbumCoverFragment: PlayerAlbumCoverFragment =
            whichFragment(R.id.playerAlbumCoverFragment)
        playerAlbumCoverFragment.setCallbacks(this)
    }

    private fun setUpBottomActions() {
        binding.sleepTimerButton.setOnClickListener {
            performSleepTimerAction()
        }
        binding.lyricsButton.setOnClickListener {
            toggleLyricsAction()
            updateLyricsButtonState()
        }
        updateLyricsButtonState()
        binding.favoriteButton.setOnClickListener {
            toggleFavorite(MusicPlayerRemote.currentSong)
        }
        updateFavoriteButtonState()
        binding.queueButton.setOnClickListener {
            openPlayingQueueAction()
        }
    }

    private fun updateLyricsButtonState() {
        binding.lyricsButton.apply {
            setImageResource(
                if (PreferenceUtil.showLyrics) R.drawable.ic_lyrics else R.drawable.ic_lyrics_outline
            )
            imageTintList = ColorStateList.valueOf(toolbarIconColor())
        }
    }

    private fun updateFavoriteButtonState(animate: Boolean = false) {
        val songId = MusicPlayerRemote.currentSong.id
        lifecycleScope.launch {
            val isFavorite = withContext(Dispatchers.IO) {
                libraryViewModel.isSongFavorite(songId)
            }
            if (_binding != null && MusicPlayerRemote.currentSong.id == songId) {
                binding.favoriteButton.apply {
                    val icon = when {
                        animate && isFavorite -> R.drawable.avd_favorite
                        animate && !isFavorite -> R.drawable.avd_unfavorite
                        isFavorite -> R.drawable.ic_favorite
                        else -> R.drawable.ic_favorite_border
                    }
                    setImageDrawable(requireContext().getTintedDrawable(icon, toolbarIconColor()))
                    (drawable as? AnimatedVectorDrawable)?.start()
                }
            }
        }
    }

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.apply {
            inflateMenu(R.menu.menu_lyrics_player)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            ToolbarContentTintHelper.colorizeToolbar(this, Color.WHITE, activity)
        }.setOnMenuItemClickListener(this)
        updateTranslationActionVisibility()
    }

    private fun updateTranslationActionVisibility() {
        binding.playerToolbar.menu.findItem(R.id.action_translate_lyrics)?.isVisible =
            LyricsPreferences.translationIconEnabled(requireContext())
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
    }

    override fun onFavoriteStateChanged() {
        super.onFavoriteStateChanged()
        updateFavoriteButtonState(animate = true)
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        playbackControlsFragment.setColor(color)
        lastColor = color.backgroundColor
        libraryViewModel.updateColor(color.backgroundColor)
        ToolbarContentTintHelper.colorizeToolbar(binding.playerToolbar, Color.WHITE, activity)
    }

    override fun toggleFavorite(song: Song) {
        super.toggleFavorite(song)
        if (song.id == MusicPlayerRemote.currentSong.id) {
            updateIsFavorite()
        }
    }

    override fun onShow() {
    }

    override fun onHide() {
    }

    override fun toolbarIconColor(): Int {
        return Color.WHITE
    }

    override val paletteColor: Int
        get() = lastColor

    private fun updateBlur() {
        // https://github.com/bumptech/glide/issues/527#issuecomment-148840717
        val song = MusicPlayerRemote.currentSong
        val blur = BlurTransformation.Builder(requireContext()).blurRadius(blurAmount.toFloat()).build()
        val metadataRequest = Glide.with(this).load(RetroGlideExtension.getSongModel(song))
            .simpleSongCoverOptions(song).transform(blur)
        val source = LyricsPreferences.backgroundSource(requireContext())
        val artist = Artist(song.artistId, listOf(Album(song.albumId, listOf(song))))
        val request = if (source == "artist" || source == "auto") {
            Glide.with(this).load(RetroGlideExtension.getArtistModel(artist))
                .artistImageOptions(artist).transform(blur).error(metadataRequest)
        } else {
            metadataRequest
        }
        request.thumbnail(lastRequest)
            .error(Glide.with(this).load(ColorDrawable(Color.DKGRAY)).fitCenter())
            .also {
                lastRequest = it.clone()
                it.crossfadeListener().into(binding.colorBackground)
            }
    }

    private fun updateLyricsFormat() {
        val song = MusicPlayerRemote.currentSong
        val audioFile = java.io.File(song.data)
        val ttmlFile = audioFile.parentFile?.resolve("${audioFile.nameWithoutExtension}.ttml")
        val hasTtml = ttmlFile?.isFile == true
        val hasLrc = LyricUtil.getSyncedLyricsFile(song)?.isFile == true ||
            !LyricUtil.getEmbeddedSyncedLyrics(song.data).isNullOrBlank()

        binding.lyricsFormat.apply {
            text = when {
                hasTtml -> "TTML"
                hasLrc -> "LRC"
                else -> ""
            }
            visibility = if (hasTtml || hasLrc) View.VISIBLE else View.GONE
        }
    }

    private fun updateSongHeader() {
        val song = MusicPlayerRemote.currentSong
        binding.title.text = song.title
        binding.artist.text = song.artistName
        binding.album.text = song.albumName
        updateLyricsFormat()
        binding.songInfoBottom.apply {
            text = getSongInfo(song)
            visibility = if (PreferenceUtil.isSongInfo) View.VISIBLE else View.GONE
        }
    }

    override fun onServiceConnected() {
        updateIsFavorite()
        updateFavoriteButtonState()
        updateLyricsButtonState()
        updateBlur()
        updateSongHeader()
    }

    override fun onPlayingMetaChanged() {
        updateIsFavorite()
        updateFavoriteButtonState()
        updateBlur()
        updateSongHeader()
    }

    override fun onPause() {
        super.onPause()
        lastRequest = null
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        updateTranslationActionVisibility()
        updateBlur()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        _binding = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "lyrics_translation_enabled") updateTranslationActionVisibility()
        if (key == SHOW_LYRICS) updateLyricsButtonState()
        if (key == "extra_song_info") updateSongHeader()
        if (key == NEW_BLUR_AMOUNT || key == "lyrics_background_source" || key == "lyrics_type") {
            updateBlur()
        }
    }
}
