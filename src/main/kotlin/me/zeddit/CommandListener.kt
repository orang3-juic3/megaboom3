package me.zeddit

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.events.guild.GenericGuildEvent
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent
import net.dv8tion.jda.api.events.guild.UnavailableGuildLeaveEvent
import net.dv8tion.jda.api.events.guild.voice.*
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.dv8tion.jda.api.managers.AudioManager
import net.dv8tion.jda.api.utils.MarkdownSanitizer
import java.awt.Color
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class CommandListener(guilds : MutableSet<AudioGuild>) : GenericCommandListener(guilds) {

    private val playerManager : AudioPlayerManager = DefaultAudioPlayerManager().apply {
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(this)
    }

    @SubscribeEvent
    fun onGuildMessage(e: GuildMessageReceivedEvent) {
        if (e.isWebhookMessage) return
        if (e.author.isBot) return
        val id = e.guild.id
        val audioGuild : AudioGuild = guilds.firstOrNull { it.id == id } ?: AudioGuild(playerManager, e.guild).apply { guilds.add(this) }
        val res = parseCommand(e.message.contentRaw) ?: run {
            e.channel.sendMessageEmbeds(
                EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("Error")
                .addField("Something went wrong!", "Syntax is incorrect!", false)
                .setTimestamp(Instant.now()).build()).queue()
            return
        }
        val builder = EmbedBuilder().setColor(Color.GREEN).setTitle("Result").setTimestamp(Instant.now())
        when (res.name)  {
            CommandName.PLAY, CommandName.PLAY_SC_S, CommandName.PLAY_YT_S -> {
                val chn = e.member!!.voiceState!!.channel
                val chnEmbed = voiceChnPrecons(chn, e.guild.retrieveMember(jda!!.selfUser).complete())
                if (chnEmbed != null || chn == null) {
                    e.channel.sendMessageEmbeds(chnEmbed!!).queue()
                    return
                }
                val argsJoined = res.args.joinToString(" ") { it }
                val identifier = when (res.name) { // spaces issue
                    CommandName.PLAY -> res.args[0]
                    CommandName.PLAY_YT_S -> "ytsearch:$argsJoined"
                    CommandName.PLAY_SC_S -> "scsearch:$argsJoined"
                    else -> ""
                }
                playerManager.loadItemOrdered(audioGuild, identifier, object : AudioLoadResultHandler {
                    override fun trackLoaded(track: AudioTrack) {
                        e.channel
                            .sendMessageEmbeds(builder.addField("Success!", "Adding `${track.info.title.esc()}` to q!", false).build())
                            .queue()
                        track.play(audioGuild, chn,e.guild.audioManager)
                    }

                    override fun playlistLoaded(playlist: AudioPlaylist) {
                        if (res.name == CommandName.PLAY_YT_S || res.name == CommandName.PLAY_SC_S) {
                            val track = playlist.tracks[0]
                            e.channel
                                .sendMessageEmbeds(builder.addField("Success!", "Adding `${track.info.title.esc()}` to q!", false).build())
                                .queue()
                            track.play(audioGuild, chn,e.guild.audioManager)
                        } else {
                            e.channel
                                .sendMessageEmbeds(builder.addField("Success!", "Detected playlist, adding ${playlist.tracks.size} tracks to q!", false).build())
                                .queue()
                            playlist.tracks.forEach { it.play(audioGuild, chn, e.guild.audioManager) }
                        }
                    }

                    override fun noMatches() {
                        e.channel
                            .sendMessageEmbeds(builder.addField("Failure", "No matches for your query!", false)
                                .setColor(Color.RED).build())
                            .queue()
                    }

                    override fun loadFailed(ex: FriendlyException) {
                        ex.printStackTrace()
                        e.channel
                            .sendMessageEmbeds(builder.addField("Failure", "Couldn't load the track(s) due to an internal error!", false)
                                .setColor(Color.RED).build())
                            .queue()
                    }
                })
            }
            CommandName.SKIP -> {
                audioGuild.nextTrack()// Add a dj role style shitter
                e.channel.sendMessageEmbeds(builder.addField("Success", "Skipped!", false).build()).queue()
            }
            CommandName.Q -> {
                e.channel.sendMessageEmbeds(builder.addField("Queue contents", fmtQ(audioGuild.viewQueue()), false).build()).queue()
            }
            CommandName.DC -> {
                if (e.guild.audioManager.isConnected) {
                    e.guild.audioManager.closeAudioConnection()
                    e.channel.sendMessageEmbeds(builder.addField("Success", "Disconnected!", false).build()).queue()
                    // probably dont need to clear q because event handler fn handles?
                } else {
                    e.channel.sendMessageEmbeds(builder.setColor(Color.RED).addField("Failure", "Not currently connected to a voice channel!", false).build()).queue()
                }
            }
            CommandName.LOOP -> {
                audioGuild.looping = !audioGuild.looping
                if (audioGuild.looping) {
                    e.channel.sendMessageEmbeds(builder.addField("Success", "The player is now looping the current track!", false).build()).queue()
                } else {
                    e.channel.sendMessageEmbeds(builder.addField("Success", "The player is no longer looping the current track!", false).build()).queue()
                }
            }
            CommandName.PAUSE -> {
                audioGuild.togglePause()
                if (audioGuild.isPaused()) {
                    e.channel.sendMessageEmbeds(builder.addField("Success", "Paused the player!", false).build()).queue()
                } else {
                    e.channel.sendMessageEmbeds(builder.addField("Success", "Resumed the player!", false).build()).queue()
                }
            }
            CommandName.CLEAR -> {
                audioGuild.clearQueue()
                e.channel.sendMessageEmbeds(builder.addField("Success", "Cleared the queue!", false).build()).queue()
            }
            CommandName.SEEK -> {
                val ms : Long = seekToMs(res.args[0]) ?: run {
                    e.channel.sendMessageEmbeds(builder.setColor(Color.RED).addField("Failure", "Could not parse seek arg!", false).build()).queue()
                    return
                }
                if (!audioGuild.seek(ms)) {
                    e.channel.sendMessageEmbeds(builder.setColor(Color.RED).addField("Failure", "Could not seek to that position!", false).build()).queue()
                } else {
                    e.channel.sendMessageEmbeds(builder.addField("Success", "Now at ${ms.fmtMs()}!", false).build()).queue()
                }
            }
            CommandName.POP -> {
                val pop =audioGuild.pop()
                if (pop == null) {
                    e.channel.sendMessageEmbeds(builder.setColor(Color.RED).addField("Failure", "Queue is empty!", false).build()).queue()
                } else {
                    e.channel.sendMessageEmbeds(builder.addField("Success!", "Removed `${pop.info.title.esc()}` from q!", false).build()).queue()
                }
            }
            CommandName.HELP -> {
                e.channel.sendMessageEmbeds(builder
                    .addField("Available commands:", "!help- Lists available commands\n" +
                            "!p, !play (yt search term/url)- Attempts to play audio\n" +
                            "!psc, !playsc (sc search term/url)- Attempts to play from soundcloud (EXPERIMENTAL)\n" +
                            "!skip, !fs- Skips the current track.\n" +
                            "!pause- Toggles pause on the player.\n" +
                            "!loop- Toggles loop on the player.\n" +
                            "!queue- Displays all queued songs.\n" +
                            "!clear- Clears the queue.\n" +
                            "!pop- Removes the last added element to the queue.\n" +
                            "!seek (HH:MM:SS)- Seeks to the given timestamp.\n" +
                            "!dc- Disconnects the bot, this happens automatically if the bot is alone in " +
                            "vc for 2 minutes. When the bot disconnects, all pause and loop options are reset and the queue is cleared.", false).build()).queue()
            }
            CommandName.NOT -> return
        }
    }

    private fun fmtQ(q: List<AudioTrack>) : String {
        if (q.isEmpty()) {
            return "Empty!"
        }
        val builder = StringBuilder()
        for (i in q.indices) {
            val tr = q[i]
            builder.append("${i + 1}. `${tr.info.title.esc()}` | `${tr.info.author.esc()}` | `${tr.duration.fmtMs()}`\n")
        }
        return builder.toString().apply {
            if (this.length >= 1024) {
                return this.substring(0..1023)
            }
        }
    }

    // null result == success
    private fun voiceChnPrecons(chn: VoiceChannel?, self: Member) : MessageEmbed? {
        val builder = EmbedBuilder().setColor(Color.RED).setTitle("Error").setTimestamp(Instant.now())
        val wrongMsg = "Something went wrong!"
        return when {
            chn == null ->  builder.addField(wrongMsg, "You are not in a voice channel!", false).build()
            chn.userLimit  >= chn.members.size+1 -> builder.addField(wrongMsg, "The voice channel you are in is full!", false).build()
            !self.hasPermission(chn, Permission.VOICE_CONNECT) -> builder.addField(wrongMsg, "The bot doesn't have enough permissions to join your voice channel!", false).build()
            else -> null
        }
    }

    private enum class CommandName {
        PLAY,
        PLAY_YT_S,
        PLAY_SC_S,
        SKIP,
        DC,
        Q,
        PAUSE,
        LOOP,
        CLEAR,
        SEEK,
        POP,
        HELP,
        NOT;
    }

    private class CommandResult(val name: CommandName, val args: Array<String>)

    private fun parseCommand(string: String) : CommandResult? {
        if (string.isEmpty() || string[0] != '!') return CommandResult(CommandName.NOT, Array(0) {""})
        val splits = string.substring(1 until string.length).split(" ")
        when {
            splits[0].equals(ignoreCase = true, other = "play") || splits[0].equals(ignoreCase = true, other = "p")-> {
                return when {
                    splits.size== 2 && splits[1].matches(Regex("https?://"))-> {
                        CommandResult(CommandName.PLAY, Array(1) {splits[1]})
                    }
                    splits.size < 2 -> {
                        return null
                    }
                    else -> CommandResult(CommandName.PLAY_YT_S, splits.args())
                }
            }
            splits[0].equals(ignoreCase = true, other = "dc") -> {
                return CommandResult(CommandName.DC, Array(0) {""})
            }
            splits[0].equals(ignoreCase = true, other = "playsc") || splits[0].equals(ignoreCase = true, other = "psc") -> {
                return when {
                    splits.size== 2 -> {
                        CommandResult(CommandName.PLAY, Array(1) {splits[1]})
                    }
                    splits.size < 2 -> {
                        return null
                    }
                    else -> CommandResult(CommandName.PLAY_SC_S, splits.args())
                }
            }
            splits[0].equals(ignoreCase = true, other = "skip") || splits[0].equals(ignoreCase = true, other = "fs") -> {
                return CommandResult(CommandName.SKIP, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "queue") -> {
                return CommandResult(CommandName.Q, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "loop") -> {
                return CommandResult(CommandName.LOOP, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "pause") -> {
                return CommandResult(CommandName.PAUSE, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "clear") -> {
                return CommandResult(CommandName.CLEAR, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "seek") -> {
                if (splits.args().size > 1) {
                    return null
                }
                return CommandResult(CommandName.SEEK, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "pop") -> {
                return CommandResult(CommandName.POP, splits.args())
            }
            splits[0].equals(ignoreCase = true, other = "help") -> {
                return CommandResult(CommandName.HELP, splits.args())
            }
        }
        return null
    }

    private fun seekToMs(string: String) : Long? {
        if (!string.matches(Regex("[:\\d]+"))) return null
        val splits = string.split(":")
        if (splits.size > 3) {
            return null
        }
        var multiplier = 1
        var sum : Long = 0
        splits.asReversed().forEach {
            try {
                sum += it.toLong() * multiplier
            } catch (e: NumberFormatException) {
                return null
            }
            multiplier *= 60
        }
        return sum * 1000
    }

    @SubscribeEvent
    fun onGuildLeave(e: GuildLeaveEvent) =
        guilds.removeIf {
            it.id == e.guild.id
        }

    @SubscribeEvent
    fun onUnGuildLeave(e:UnavailableGuildLeaveEvent) =
        guilds.removeIf {
            it.id == e.guildId
        }

    private val dcScheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    @SubscribeEvent
    fun onVcDc(e: GuildVoiceLeaveEvent) {
        e.audioGuild {
            vcDc(e.member, it, e.channelLeft, e.guild)
        }
    }

    private fun vcDc(member: Member, audioGuild: AudioGuild, chn: VoiceChannel, guild: Guild) {
        val id = jda!!.selfUser.id
        if (member.id == id) {
            audioGuild.apply { clearQueue(); looping = false; if (isPaused()) togglePause(); nextTrack() }
        } else {
            if (chn.hasSelf()) {
                if (chn.members.size == 1) {
                    scheduleDc(audioGuild, guild)
                }
            }
        }
    }

    @SubscribeEvent
    fun onVcMove(e: GuildVoiceMoveEvent) {
        e.audioGuild {
            if (e.member.id == jda!!.selfUser.id) return
            if (e.channelJoined.hasSelf()) {
                it.expiry = -1
            } else if (e.channelLeft.hasSelf()) {
                vcDc(e.member,it, e.channelLeft, e.guild)
            }
        }
    }

    @SubscribeEvent
    fun onVcConnect(e: GuildVoiceJoinEvent) {
        e.audioGuild {
            if (e.channelJoined.hasSelf()) {
                it.expiry = -1
            }
        }
    }


    private inline fun GenericGuildEvent.audioGuild(block : (AudioGuild) -> Unit) {
        val audioGuild = guilds.firstOrNull {this.guild.id == it.id}
        audioGuild?.let { block.invoke(it) }
    }

    private fun scheduleDc(audioGuild: AudioGuild, guild: Guild) {
        val delay: Long = 2000 * 60
        val exp  = System.currentTimeMillis() + delay
        audioGuild.expiry = exp
        dcScheduler.schedule(delay, TimeUnit.MILLISECONDS) {
            if (audioGuild.expiry == exp) {
                val manager = guild.audioManager
                if (manager.isConnected) {
                    manager.closeAudioConnection()
                    audioGuild.expiry = -1
                }
            }
        }
    }
}