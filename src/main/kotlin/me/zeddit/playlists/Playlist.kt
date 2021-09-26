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
import java.util.concurrent.ThreadFactory

// doesnt automatically setup so that invalid links can be removed!
// ID format is the user id of the creator a dash and the playlist number eg 10-1
class Playlist(private val tracks: MutableList<String>, val id: String, val info: Info, private val playerManager: AudioPlayerManager) {

    data class Info(var name: String, var description: String)

    private val threadFactory = ThreadFactory {
        val thread = Thread(it)
        thread.name = "Playlist Track Loader Thread"
        thread
    }

    private var started: Boolean = false
    private var audioTracks: MutableList<AudioTrack> = ArrayList()
    get() {
        if (!started) {
            field = resetInternal()
            started = true
        }
        return field
    }
    set(value) {
        if (!started) {
            started =true
        }
        field = value
    }


    private fun String.constructTrackCallable(index: Int) : Callable<Result<Pair<List<AudioTrack>, Int>>> {
        val i = this
        return Callable {
            val loadingTr: MutableList<AudioTrack?> = ArrayList()
            playerManager.loadItemOrdered(Any(), i, object : AudioLoadResultHandler {
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
            val nonNull = loadingTr.filterNotNull()
            if (loadingTr.size - nonNull.size > 0) {
                return@Callable Result.failure(InvalidTrackException(i, this@Playlist.id))
            } else {
                return@Callable Result.success(Pair(nonNull, index))
            }
        }
    }

    @Synchronized
    private fun resetInternal() : MutableList<AudioTrack> {
        val service = Executors.newFixedThreadPool(5, threadFactory)
        val callables = ArrayList<Callable<Result<Pair<List<AudioTrack>, Int>>>>()
        for (i in tracks.indices) {
            callables.add(tracks[i].constructTrackCallable(i))
        }
        return service.invokeAll(callables).awaitAll()
            .asSequence()
            .map { it.getOrThrow() }
            .sortedBy { it.second }
            .map { it.first }
            .flatten()
            .toMutableList()
    }
    @Synchronized
    fun reset() {
        audioTracks = resetInternal()
    }

    @Synchronized
    //The number of urls removed
    fun remove(url : String) : Int {
        val str = tracks.removeIf { it == url }
        val track = audioTracks.removeIf {it.info.uri == url}
        return if (str || track) 1 else 0
    }

    @Synchronized
    fun remove(index: Int) {
        if (index !in tracks.indices) {
            throw IllegalStateException()
        }
        tracks.removeAt(index)
        reset()
    }


    // This method makes sure this class is immutable by returning a new list each time. Always will be sorted
    @Synchronized
    fun getTracks() : List<AudioTrack> {
        val service = Executors.newFixedThreadPool(5,threadFactory)
        val callables = ArrayList<Callable<Pair<AudioTrack, Int>>>()
        for (i in audioTracks.indices) {
            callables.add(Callable {
                Pair(audioTracks[i].makeClone(), i)
            })
        }
        return service.invokeAll(callables).awaitAll().sortedBy { it.second }.map { it.first }
    }

    fun getTrackUrls() : List<String> = ArrayList(tracks)

    fun clone(id: String) : Playlist = Playlist(ArrayList(tracks), id, this.info, playerManager)


    @Synchronized
    fun remove(urls: List<String>) : Int {
        var count  =0
        urls.forEach {
            count += remove(it)
        }
        return count
    }

    @Synchronized
    fun add(urls: List<String>) : List<AudioTrack> {
        return Executors.newFixedThreadPool(5, threadFactory).invokeAll(MutableList(urls.size) {
            urls[it].constructTrackCallable(it)
        }).awaitAll().flatMap { it.getOrThrow().first }.onEach {
            addTrack(it)
        }
    }

    private fun addTrack(i: AudioTrack) {
        tracks.add(i.info.uri)
        audioTracks.add(i)
    }

    @Synchronized
    fun add(url: String) : List<AudioTrack> {
        val list = ArrayList<AudioTrack>()
        for (i in url.constructTrackCallable(0).call().getOrThrow().first) {
            addTrack(i)
            list.add(i)
        }
        return list
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