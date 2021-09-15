package me.zeddit

import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.entities.GuildChannel
import net.dv8tion.jda.api.entities.VoiceChannel
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.MarkdownSanitizer
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
        TimeUnit.MILLISECONDS.toSeconds(this) % TimeUnit.MINUTES.toSeconds(1));
}

fun GuildChannel.hasSelf() : Boolean = this.members.map { it.user.id }.contains(jda.selfUser.id)

fun ScheduledExecutorService.schedule(delay: Long, unit: TimeUnit, block: () -> Unit) {
    this.schedule(block, delay, unit)
}
