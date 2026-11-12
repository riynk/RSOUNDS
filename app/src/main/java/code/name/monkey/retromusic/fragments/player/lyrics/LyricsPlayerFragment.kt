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
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.NEW_BLUR_AMOUNT
import code.name.monkey.retromusic.SHOW_LYRICS
import code.name.monkey.retromusic.lyrics.LyricsPreferences
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentLyricsPlayerBinding
import code.name.monkey.retromusic.extensions.drawAboveSystemBars
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
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
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.CoverLyricsType
import code.name.monkey.retromusic.util.PreferenceUtil.blurAmount
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder


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
        binding.playerToolbar.drawAboveSystemBars()
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
        binding.queueButton.setOnClickListener {
            openPlayingQueueAction()
        }
    }

    private fun updateLyricsButtonState() {
        binding.lyricsButton.setImageResource(
            if (PreferenceUtil.showLyrics) R.drawable.ic_lyrics else R.drawable.ic_lyrics_outline
        )
    }

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.apply {
            inflateMenu(R.menu.menu_lyrics_player)
            setNavigationOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
            ToolbarContentTintHelper.colorizeToolbar(this, Color.WHITE, activity)
        }.setOnMenuItemClickListener(this)
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
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
        val source = if (PreferenceUtil.lyricsType == CoverLyricsType.OVER_COVER) LyricsPreferences.backgroundSource(requireContext()) else "metadata"
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

    private fun updateSongHeader() {
        val song = MusicPlayerRemote.currentSong
        binding.title.text = song.title
        binding.artist.text = song.artistName
        binding.album.text = song.albumName
    }

    override fun onServiceConnected() {
        updateIsFavorite()
        updateBlur()
        updateSongHeader()
    }

    override fun onPlayingMetaChanged() {
        updateIsFavorite()
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
        updateBlur()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        _binding = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == SHOW_LYRICS) updateLyricsButtonState()
        if (key == NEW_BLUR_AMOUNT || key == "lyrics_background_source" || key == "lyrics_type") {
            updateBlur()
        }
    }
}
