package me.zeddit

import com.google.gson.Gson
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

fun String.esc() : String {
    return MarkdownSanitizer.escape(this)
}

fun List<String>.args() : Array<String> {
    return Array(this.size -1) { this[it + 1] }
}

fun AudioTrack.play(audioGuild: AudioGuild, chn: VoiceChannel, audioManager: AudioManager) {
    if (!audioManager.isConnected) {
        audioManager.openAudioConnection(chn)
    }
    audioGuild.queue(this)
}

fun Long.fmtMs() : String {
    return String.format("%02d:%02d:%02d", TimeUnit.MILLISECONDS.toHours(this),
        TimeUnit.MILLISECONDS.toMinutes(this) % TimeUnit.HOURS.toMinutes(1),
        TimeUnit.MILLISECONDS.toSeconds(this) % TimeUnit.MINUTES.toSeconds(1))
}

fun GuildChannel.hasSelf() : Boolean = this.members.map { it.user.id }.contains(jda.selfUser.id)

fun ScheduledExecutorService.schedule(delay: Long, unit: TimeUnit, block: () -> Unit) {
    this.schedule(block, delay, unit)
}

val gson = Gson()

inline fun <reified T> Any.fromJson() = gson.fromJson(this.toString(), T::class.java)
inline fun <reified T> ByteArray.fromJson(charset: Charset = StandardCharsets.UTF_8) = gson.fromJson(String(this, charset), T::class.java)
inline fun <reified T> String.toJson() : T = gson.fromJson(this, T::class.java)

fun <T> Future<T>.join() = this.get()

fun eprintln(a: Any) {System.err.println(a.toString())}

fun <T> List<Future<T>>.awaitAll(): List<T> {
    val complete = ArrayList<T>()
    var filtered = this.filterNot { it.isCancelled }
    while (filtered.isNotEmpty()) {
        filtered[0].get()
        filtered = filtered.filter { it.isDone }.onEach { complete.add(it.get()) }.toMutableList().apply { this.removeIf { it.isDone } }
        filtered = filtered.filterNot { it.isCancelled }
    }
    return complete // shouldnt block if they are all complete
}
