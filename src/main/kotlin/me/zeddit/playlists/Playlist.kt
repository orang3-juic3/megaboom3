package me.zeddit.playlists

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import me.zeddit.awaitAll
import me.zeddit.eprintln
import java.util.concurrent.Callable
import java.util.concurrent.Executors

// doesnt automatically setup so that invalid links can be removed!
// ID format is the user id of the creator a dash and the playlist number eg 10-1
class Playlist(private val tracks: MutableList<String>, val id: String, val info: Info, private val playerManager: AudioPlayerManager) {

    data class Info(val name: String, val description: String)

    lateinit var audioTracks: MutableList<AudioTrack>
    private set

    fun reset() {
        val service = Executors.newFixedThreadPool(5)
        val callables = ArrayList<Callable<List<AudioTrack?>>>()
        for (i in tracks) {
            callables.add(Callable {
                val loadingTr: MutableList<AudioTrack?> = ArrayList()
                playerManager.loadItemOrdered(service, i, object : AudioLoadResultHandler {
                    override fun trackLoaded(loaded: AudioTrack) {
                        loadingTr.add(loaded)
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        if (i.matches(Regex("https?://"))) {
                            loadingTr.add(playlist.tracks[0])
                        } else {
                            playlist.tracks.forEach { loadingTr.add(it) }
                        }
                    }

                    override fun noMatches() {
                        loadingTr.add(null)
                    }

                    override fun loadFailed(exception: FriendlyException) {
                        loadingTr.add(null)
                        exception.printStackTrace()
                        eprintln("Load failed for track with url $i")
                    }
                }).get()
                loadingTr
            })
        }
        audioTracks = service.invokeAll(callables).awaitAll().flatMap { it.filterNotNull() }.toMutableList()
    }
    //The number of urls removed
    fun remove(url : String) : Int {
        val str = tracks.removeIf { it == url }
        val track = audioTracks.removeIf {it.info.uri == url}
        return if (str || track) 1 else 0
    }

    fun remove(urls: List<String>) : Int {
        var count  =0
        urls.forEach {
            count += remove(it)
        }
        return count
    }

    fun getUserID() : String {
        return id.split("-")[0]
    }
    fun getPlaylistNumber() : String {
        return id.split("-")[1]
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        (other as? Playlist)?.let {
            return it.id == this.id
        }
        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}