package code.name.monkey.retromusic.fragments.player.rsound1

import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.preference.PreferenceManager
import code.name.monkey.appthemehelper.util.ToolbarContentTintHelper
import code.name.monkey.retromusic.NEW_BLUR_AMOUNT
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.databinding.FragmentRsound1Binding
import code.name.monkey.retromusic.extensions.drawAboveSystemBars
import code.name.monkey.retromusic.extensions.whichFragment
import code.name.monkey.retromusic.fragments.base.AbsPlayerFragment
import code.name.monkey.retromusic.fragments.player.PlayerAlbumCoverFragment
import code.name.monkey.retromusic.glide.BlurTransformation
import code.name.monkey.retromusic.glide.RetroGlideExtension
import code.name.monkey.retromusic.glide.RetroGlideExtension.artistImageOptions
import code.name.monkey.retromusic.glide.RetroGlideExtension.simpleSongCoverOptions
import code.name.monkey.retromusic.glide.crossfadeListener
import code.name.monkey.retromusic.helper.MusicPlayerRemote
import code.name.monkey.retromusic.model.Album
import code.name.monkey.retromusic.model.Artist
import code.name.monkey.retromusic.model.Song
import code.name.monkey.retromusic.util.CoverLyricsType
import code.name.monkey.retromusic.util.LyricsPreferences
import code.name.monkey.retromusic.util.PreferenceUtil
import code.name.monkey.retromusic.util.color.MediaNotificationProcessor
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestBuilder

class Rsound1PlayerFragment : AbsPlayerFragment(R.layout.fragment_rsound1),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private var _binding: FragmentRsound1Binding? = null
    private val binding get() = _binding!!
    private lateinit var playbackControlsFragment: Rsound1PlaybackControlsFragment
    private var lastRequest: RequestBuilder<Drawable>? = null
    private var lastColor: Int = 0

    override fun playerToolbar(): Toolbar = binding.playerToolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentRsound1Binding.bind(view)
        setUpSubFragments()
        setUpPlayerToolbar()
        binding.playerToolbar.drawAboveSystemBars()
        bindSongActions()
        updateSongMetadata()
    }

    private fun setUpSubFragments() {
        playbackControlsFragment = whichFragment(R.id.playbackControlsFragment)
        val coverFragment: PlayerAlbumCoverFragment = whichFragment(R.id.playerAlbumCoverFragment)
        coverFragment.setCallbacks(this)
    }

    private fun bindSongActions() {
        val controlsView = playbackControlsFragment.view ?: return
        controlsView.findViewById<View>(R.id.actionFavorite)?.setOnClickListener {
            dispatchPlayerAction(R.id.action_toggle_favorite)
        }
        controlsView.findViewById<View>(R.id.actionQueue)?.setOnClickListener {
            dispatchPlayerAction(R.id.now_playing)
        }
        controlsView.findViewById<View>(R.id.actionPlaylist)?.setOnClickListener {
            dispatchPlayerAction(R.id.action_add_to_playlist)
        }
        controlsView.findViewById<View>(R.id.actionDetails)?.setOnClickListener {
            dispatchPlayerAction(R.id.action_details)
        }
    }

    private fun dispatchPlayerAction(itemId: Int) {
        binding.playerToolbar.menu.findItem(itemId)?.let(::onMenuItemClick)
    }

    private fun updateSongMetadata() {
        val song = MusicPlayerRemote.currentSong
        binding.songTitle.text = song.title
        binding.songArtist.text = song.artistName
        binding.songAlbum.text = song.albumName
    }

    private fun setUpPlayerToolbar() {
        binding.playerToolbar.apply {
            inflateMenu(R.menu.menu_player)
            setNavigationOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            setOnMenuItemClickListener(this@Rsound1PlayerFragment)
            menu.findItem(R.id.action_sleep_timer)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.action_toggle_lyrics)?.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            menu.findItem(R.id.action_toggle_favorite)?.isVisible = false
            menu.findItem(R.id.now_playing)?.isVisible = false
            menu.findItem(R.id.action_add_to_playlist)?.isVisible = false
            menu.findItem(R.id.action_details)?.isVisible = false
            ToolbarContentTintHelper.colorizeToolbar(this, Color.WHITE, requireActivity())
        }
        updateTranslationActionVisibility()
    }

    private fun updateTranslationActionVisibility() {
        binding.playerToolbar.menu.findItem(R.id.action_translate_lyrics)?.isVisible =
            LyricsPreferences.translationIconEnabled(requireContext())
    }

    override fun onFavoriteToggled() {
        toggleFavorite(MusicPlayerRemote.currentSong)
    }

    override fun toggleFavorite(song: Song) {
        super.toggleFavorite(song)
        if (song.id == MusicPlayerRemote.currentSong.id) updateIsFavorite()
    }

    override fun onColorChanged(color: MediaNotificationProcessor) {
        if (::playbackControlsFragment.isInitialized) playbackControlsFragment.setColor(color)
        lastColor = color.backgroundColor
        libraryViewModel.updateColor(color.backgroundColor)
        if (_binding != null) {
            ToolbarContentTintHelper.colorizeToolbar(binding.playerToolbar, Color.WHITE, activity)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        updateSongMetadata()
        updateIsFavorite()
        updateBlur()
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        updateSongMetadata()
        updateBlur()
    }

    override fun onShow() = Unit

    override fun onHide() = Unit

    override fun toolbarIconColor(): Int = Color.WHITE

    override val paletteColor: Int
        get() = lastColor

    private fun updateBlur() {
        if (_binding == null) return
        val song = MusicPlayerRemote.currentSong
        val blur = BlurTransformation.Builder(requireContext())
            .blurRadius(PreferenceUtil.blurAmount.toFloat())
            .build()
        val metadataRequest = Glide.with(this).load(RetroGlideExtension.getSongModel(song))
            .simpleSongCoverOptions(song).transform(blur)
        val source = if (PreferenceUtil.lyricsType == CoverLyricsType.OVER_COVER) {
            LyricsPreferences.backgroundSource(requireContext())
        } else {
            "metadata"
        }
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

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
        updateTranslationActionVisibility()
        updateBlur()
    }

    override fun onPause() {
        super.onPause()
        lastRequest = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        _binding = null
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "lyrics_translation_enabled") updateTranslationActionVisibility()
        if (key == NEW_BLUR_AMOUNT || key == "lyrics_background_source" || key == "lyrics_type") {
            updateBlur()
        }
    }
}
