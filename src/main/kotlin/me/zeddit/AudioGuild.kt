package me.zeddit

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason
import net.dv8tion.jda.api.entities.Guild
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

class AudioGuild(manager: AudioPlayerManager, guild: Guild) : AudioEventAdapter() {

    // null stops the player.
    private val player: AudioPlayer = manager.createPlayer().apply {
        addListener(this@AudioGuild)
        guild.audioManager.sendingHandler = AudioPlayerSendHandler(this)
    }
    private val queue: BlockingQueue<AudioTrack> = ArrayBlockingQueue(100)
    val id: String = guild.id
    var looping = false
    private var current: AudioTrack? = null
    var expiry: Long = -1


    override fun onTrackEnd(player: AudioPlayer, track: AudioTrack, endReason: AudioTrackEndReason) {
        if (endReason.mayStartNext) {
            nextTrack()
        }
    }

    fun queue(track: AudioTrack) {
        if (!player.startTrack(track, true)) {
            queue.offer(track)
        } else {
            current = track.makeClone()
        }
    }

    fun viewQueue() : List<AudioTrack> = queue.toList()

    fun clearQueue() {
        queue.clear()
        current = null
    }

    fun pop() : AudioTrack? {
        if (queue.isEmpty()) return null
        return queue.last().apply { queue.remove(this) }
    }

    //true if successful
    fun seek(ms: Long) : Boolean {
        if (player.playingTrack == null) return false
        if (!player.playingTrack.isSeekable) return false
        try {
            player.playingTrack.position = ms
        } catch (e: Exception) {
            return false
        }
        return true
    }

    fun togglePause() {
        player.isPaused = !player.isPaused
    }
    fun isPaused() : Boolean {
        return player.isPaused
    }

    fun nextTrack() {
        if (looping) {
            if (current == null) {
                player.startTrack(null, false)
            } else {
                player.startTrack(current, false)
                current = current!!.makeClone()
            }
        } else {
            player.startTrack(queue.poll()?.apply { current = this.makeClone() }, false)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other is AudioGuild) {
            if (other.id == id) return true
        }
        return false
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

}