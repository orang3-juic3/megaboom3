package me.zeddit

import com.google.gson.Gson
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.awt.Color
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Instant
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

// null result == success
fun voiceChnPrecons(chn: VoiceChannel?, self: Member) : MessageEmbed? {
    val builder = EmbedBuilder().setColor(Color.RED).setTitle("Error").setTimestamp(Instant.now())
    val wrongMsg = "Something went wrong!"
    return when {
        chn == null ->  builder.addField(wrongMsg, "You are not in a voice channel!", false).build()
        chn.userLimit  >= chn.members.size+1 -> builder.addField(wrongMsg, "The voice channel you are in is full!", false).build()
        !self.hasPermission(chn, Permission.VOICE_CONNECT) -> builder.addField(wrongMsg, "The bot doesn't have enough permissions to join your voice channel!", false).build()
        else -> null
    }
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

inline fun <T> Iterable<T>.firstNullable(predicate: (T) -> Boolean) : T? {
    for (element in this) if (predicate(element)) return element
    return null
}

fun String.toResultField(success: Boolean = false) : MessageEmbed.Field = MessageEmbed.Field(if (success) "Success" else "Failure", this, false)

fun Array<String>.joinToString(offset: Int = 0, separator: String = " ") : String = Array(this.size - offset) {this[it + offset]}.joinToString(" ")

