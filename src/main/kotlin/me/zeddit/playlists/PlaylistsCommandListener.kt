package me.zeddit.playlists

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import me.zeddit.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import java.awt.Color
import java.time.Instant
import java.util.concurrent.Executors

class PlaylistsCommandListener  {

    private val sp = Regex("\\s+")

    private val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(this)
    }

    @SubscribeEvent
    fun onGuildMessageReceived(e: GuildMessageReceivedEvent) {
        if (e.isWebhookMessage) return
        if (e.author.isBot) return
        val id = e.guild.id
        val builder = EmbedBuilder().setTitle("Result").setColor(Color.RED).setTimestamp(Instant.now())
        val audioGuild : AudioGuild = guilds.firstOrNull { it.id == id } ?: AudioGuild(playerManager, e.guild).apply { guilds.add(this) }
        if (!e.message.contentRaw.matches(Regex("!pl(aylist)?\\s+.+", RegexOption.IGNORE_CASE))) {
            return
        }
        val cmd = e.message.contentRaw.split(sp).toMutableList().apply { removeFirst() }.joinToString(" ")
        val result = parseCommand(cmd, e) ?: run {
            return
        }
        val argsEmbed = EmbedBuilder(builder).addField("Not enough/incorrect args supplied!".toResultField()).build()
        val pFmtHelper = PlaylistFmtHelper(e, argsEmbed, e.author)
        when(result.commandName) {
            // Arg 0 = url 1(o) = id toggle rest is the name/id
            CommandName.ADD -> {
                if (result.args.size < 2) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val arg1 =result.args[1]
                if (arg1.equals("id", true) && result.args.size != 3) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                }
                if (!result.args[0].matches(Regex("https?://.+"))) {
                    e.channel.sendMessageEmbeds(builder.addField("Invalid url supplied!".toResultField()).build()).queue()
                }
                if (arg1.equals("id", true)) {
                    getPlaylistFmt(result.args[2], pFmtHelper)
                } else {
                    getPlaylistFmt(result.args.joinToString(2),pFmtHelper, false)
                }?.let {
                    addInternal(e, builder, it, result.args)
                }
            }
            CommandName.REMOVE -> TODO()
            CommandName.QUEUE -> {
                // arg0 (opt) id toggle, arg1 etc name or id of playlist
                if (result.args.isEmpty()) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val idToggle =result.args[0].equals("id", ignoreCase = true)
                if (idToggle && result.args.size != 2) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                if (idToggle) {
                    getPlaylistFmt(result.args[1], pFmtHelper)
                } else {
                    getPlaylistFmt(result.args.joinToString(1),pFmtHelper, false)
                }?.let {
                    val audioManager = e.guild.audioManager
                    val chn = e.member!!.voiceState!!.channel
                    if (!audioManager.isConnected) {
                        val embed = voiceChnPrecons(chn, e.guild.retrieveMember(jda!!.selfUser).complete())
                        if (embed != null) {
                            e.channel.sendMessageEmbeds(embed).queue()
                            return
                        }
                    }
                    val tracks = it.getTracks()
                    if (tracks.isEmpty()) {
                        e.channel.sendMessageEmbeds(builder.addField("The playlist is empty!".toResultField()).build()).queue()
                        return
                    }
                    if (!audioManager.isConnected) {
                        audioManager.openAudioConnection(chn)
                    }
                    for (i in tracks) audioGuild.queue(i)
                    e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN)
                        .addField("Added ${tracks.size} tracks to queue from playlist ${it.info.name}!".toResultField(true)).build()).queue()
                    audioGuild.nextTrack(true)
                }
            }
            CommandName.CREATE -> {
                val playlists = storageHandler.retrieveAllPlaylists(e.author.id)
                if (playlists.size >= 5) {
                    e.channel.sendMessageEmbeds(builder.addField(("You have reached the playlist limit; " +
                            "please delete a playlist in order to create a new one!").toResultField()).build()).queue()
                    return
                }
                var name = "New Playlist"
                if (result.args.isNotEmpty()) {
                    name = result.args.joinToString("")
                }
                val newPl = Playlist(ArrayList(), "${e.author.id}-${nextId(playlists)}", Playlist.Info(name, ""), playerManager)
                Executors.newSingleThreadExecutor().submit {
                    cache.cache(newPl)
                    storageHandler.sync(newPl)
                    println("User ${e.author.asTag} (id = ${e.author.id}) created a new playlist with id ${newPl.id}!")
                    e.channel
                        .sendMessageEmbeds(builder
                            .addField("Created playlist with name ${newPl.info.name} and id ${newPl.id}!".toResultField(true)).setColor(
                                Color.GREEN).build()).queue()
                }
            }
            CommandName.DELETE -> TODO()
            CommandName.LIST -> TODO()
            CommandName.DESCRIPTION -> TODO()
            CommandName.NAME -> TODO()
            CommandName.CLONE -> TODO()
        }
    }

    private data class PlaylistFmtHelper(val e: GuildMessageReceivedEvent, val embed: MessageEmbed, val user: User)

    private fun getPlaylistFmt(name: String, p: PlaylistFmtHelper, id: Boolean = true) : Playlist? {
        val playlist = if (id) {
            safeGetPlaylistId(name)
        } else {
            safeGetPlaylist(name,p.user)
        }
        if (playlist == null) {
            p.e.channel.sendMessageEmbeds(p.embed).queue()
        }
        return playlist
    }

    private fun nextId(playlists:List<Playlist>) : Long {
        var largest = 0L
        for (i in playlists) {
            val next =i.id.split("-")[1].toLong()
            if (next >= largest) largest = next + 1
        }
        return largest
    }

    private fun addInternal(e: GuildMessageReceivedEvent, builder: EmbedBuilder, playlist: Playlist, args: Array<String>) {
        try {
            val added = playlist.add(args[0])
            if (added.size > 1) {
                e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN)
                    .addField("Detected playlist, adding ${added.size} tracks to this playlist!".toResultField(true)).build()).queue()
            } else {
                e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN)
                    .addField("Adding `${added[0].info.title.esc()} to this playlist!`".toResultField(true)).build()).queue()
            }
        } catch (ex: InvalidTrackException) {
            e.channel.sendMessageEmbeds(builder
                .addField("Could not load track for url(s) ${ex.urls.joinToString(", ")} for playlist with id ${ex.id}!"
                    .toResultField()).build()).queue()
        }
    }

    private fun String.toNoPlaylistEmbed(id: Boolean = false) : MessageEmbed {
        val builder = EmbedBuilder().setTitle("Result").setColor(Color.RED).setTimestamp(Instant.now())
        return builder.addField("Couldn't find playlist with ${if (id) "id" else "name"} $this!".toResultField()).build()
    }

    private fun safeGetPlaylist(name: String, user: User) : Playlist? {
        val nameSame : (Playlist) -> Boolean= {
            it.info.name.equals(name, true)
        }
        cache.getPlaylist {
            nameSame.invoke(it)
        }?.let {
            return it
        }
        val playlists = storageHandler.retrieveAllPlaylists(user.id)
        return playlists.firstOrNull { nameSame.invoke(it) }.apply { this?.let{ cache.cache(this)} }
    }

    private fun safeGetPlaylistId(id: String) : Playlist? {
        cache.getPlaylist(id)?.let {
            return it
        }
        return storageHandler.retrievePlaylist(id).apply { this?.let { cache.cache(this) } }
    }

    private enum class CommandName {
        ADD,
        REMOVE,
        QUEUE,
        CREATE,
        DELETE,
        LIST,
        DESCRIPTION,
        NAME,
        CLONE,
    }

    private class CommandResult(val commandName: CommandName, val args: Array<String>)

    private fun parseCommand(cmd: String, e: GuildMessageReceivedEvent) : CommandResult? {
        //lowercase cmdName
        val (cmdName, args) = cmd.split(sp).let {list ->
            Pair(list[0].toLowerCase(), Array(list.size - 1) {list[it + 1]})
        }
        val commandName = when (cmdName) {
            "add" -> CommandName.ADD
            "a" -> CommandName.ADD
            "remove" -> CommandName.REMOVE
            "r" -> CommandName.REMOVE
            "queue" -> CommandName.QUEUE
            "q" -> CommandName.QUEUE
            "create" -> CommandName.CREATE
            "c" -> CommandName.CREATE
            "delete" -> CommandName.DELETE
            "d" -> CommandName.DELETE
            "list" -> CommandName.LIST
            "display" -> CommandName.LIST
            "l" -> CommandName.LIST
            "description" -> CommandName.DESCRIPTION
            "desc" -> CommandName.DESCRIPTION
            "name" -> CommandName.NAME
            "n" -> CommandName.NAME
            "copy" -> CommandName.CLONE
            "clone" -> CommandName.CLONE
            else -> return null
        }
        return CommandResult(commandName, args)
    }
}