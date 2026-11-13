package code.name.monkey.retromusic.fragments.player.rsound2

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import code.name.monkey.retromusic.R
import code.name.monkey.retromusic.fragments.player.blur.BlurPlayerFragment
import code.name.monkey.retromusic.helper.MusicPlayerRemote

/**
 * Rsound 2 keeps the Default player and artwork implementation intact.
 * The metadata header is an overlay in portrait and occupies only the
 * controls column in landscape, so the album artwork keeps Default sizing.
 */
class Rsound2PlayerFragment : BlurPlayerFragment(R.layout.fragment_rsound2) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (code.name.monkey.retromusic.util.PreferenceUtil.isFullScreenMode) {
            view.findViewById<View>(R.id.status_bar)?.isVisible = true
        }
        updateHeader(view)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        view?.let(::updateHeader)
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        view?.let(::updateHeader)
    }

    private fun updateHeader(root: View) {
        val song = MusicPlayerRemote.currentSong
        root.findViewById<android.widget.TextView>(R.id.songTitle)?.text = song.title
        root.findViewById<android.widget.TextView>(R.id.songArtist)?.text = song.artistName
        root.findViewById<android.widget.TextView>(R.id.songAlbum)?.text = song.albumName
    }
}
