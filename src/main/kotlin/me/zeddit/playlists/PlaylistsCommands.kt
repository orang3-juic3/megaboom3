package me.zeddit.playlists

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import command.Command
import command.CommandResult
import command.GenericCommandName
import me.zeddit.*
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import java.awt.Color
import java.lang.StringBuilder
import java.time.Instant
import java.util.concurrent.Executors

class PlaylistsCommands : Command {

    private val sp = Regex("\\s+")

    private val playerManager: AudioPlayerManager = DefaultAudioPlayerManager().apply {
        AudioSourceManagers.registerLocalSource(this)
        AudioSourceManagers.registerRemoteSources(this)
    }

    private enum class CommandName : GenericCommandName {
        ADD,
        REMOVE,
        QUEUE,
        CREATE,
        DELETE,
        SET,
        LIST,
        CLONE,
    }

    override fun parseCommand(cmd: String, e: GuildMessageReceivedEvent) : CommandResult? {
        if (!e.message.contentRaw.matches(Regex("!pl(aylist)?\\s+.+", RegexOption.IGNORE_CASE))) {
            return null
        }
        val pCmd = e.message.contentRaw.split(sp).toMutableList().apply { removeFirst() }.joinToString(" ")
        //lowercase cmdName
        val (cmdName, args) = pCmd.split(sp).let {list ->
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
            "set" -> CommandName.SET
            "s" -> CommandName.SET
            "copy" -> CommandName.CLONE
            "clone" -> CommandName.CLONE
            else -> return null
        }
        return CommandResult(commandName, args)
    }

    override fun onCommand(cmd: CommandResult, e: GuildMessageReceivedEvent) {
        val audioGuild : AudioGuild = guilds.firstOrNull { it.id == e.guild.id } ?: AudioGuild(playerManager, e.guild).apply { guilds.add(this) }
        val builder = EmbedBuilder().setTitle("Result").setColor(Color.RED).setTimestamp(Instant.now())
        val argsEmbed = EmbedBuilder(builder).addField("Not enough/incorrect args supplied!".toResultField()).build()
        val doesNotOwn = EmbedBuilder(builder).addField("This is not your playlist!".toResultField()).build()
        val pFmtHelper = PlaylistFmtHelper(e, e.author)
        when(cmd.commandName) {
            // Arg 0 = url 1(o) = id toggle rest is the name/id
            CommandName.ADD -> {
                if (cmd.args.size < 2) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val arg1 =cmd.args[1]
                if (arg1.equals("id", true) && cmd.args.size != 3) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                }
                if (!cmd.args[0].matches(Regex("https?://.+"))) {
                    e.channel.sendMessageEmbeds(builder.addField("Invalid url supplied!".toResultField()).build()).queue()
                    return
                }
                if (arg1.equals("id", true)) {
                    getPlaylistFmt(cmd.args[2], pFmtHelper)
                } else {
                    getPlaylistFmt(cmd.args.joinToString(1),pFmtHelper, false)
                }?.let {
                    if (it.getUserID() != e.author.id) {
                        e.channel.sendMessageEmbeds(doesNotOwn).queue()
                    } else {
                        addInternal(e, builder, it, cmd.args)
                    }
                }
            }
            CommandName.REMOVE -> {
                if (cmd.args.isEmpty()) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val idToggle =cmd.args[0].equals("id", ignoreCase = true)
                if ((idToggle && cmd.args.size != 3) || cmd.args.size < 2
                    || !cmd.args[cmd.args.size - 1].matches(Regex("\\d{1,9}"))) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val index = cmd.args[cmd.args.size - 1].toInt()
                if (idToggle) {
                    getPlaylistFmt(cmd.args[1], pFmtHelper)
                } else {
                    getPlaylistFmt(Array(cmd.args.size - 1) {cmd.args[it]}.joinToString(),pFmtHelper, false)
                }?.let {
                    try {
                        it.remove(index - 1)
                        e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN).addField("Removed track $index!".toResultField(true)).build()).queue()
                    } catch (ex: IllegalStateException) {
                        e.channel.sendMessageEmbeds(builder.addField("Index $index out of range!".toResultField()).build()).queue()
                    }
                }
            }
            CommandName.QUEUE -> {
                parseOnlyPlaylist(e, cmd.args, argsEmbed)?.let {
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
                if (cmd.args.isNotEmpty()) {
                    name = cmd.args.joinToString("")
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
            CommandName.SET -> {
                // arg 0 = id opt then arg 1 until a opt is the name/id then the rest is value for the set op
                if (cmd.args.isEmpty()) {
                    e.channel.sendMessageEmbeds(argsEmbed).queue()
                    return
                }
                val opt : SetOpt
                val value: String
                val playlist: Playlist
                if (cmd.args[0].equals("id", true)) {
                    if (cmd.args.size < 4) {
                        e.channel.sendMessageEmbeds(argsEmbed).queue()
                        return
                    }
                    val id = cmd.args[1]
                    playlist = getPlaylistFmt(id, pFmtHelper) ?: return
                    opt = parseOpt(cmd.args[2]) ?: run {e.channel.sendMessageEmbeds(argsEmbed).queue(); return}
                    value = cmd.args.joinToString(3)
                } else {
                    if (cmd.args.size < 3) {
                        e.channel.sendMessageEmbeds(argsEmbed).queue()
                        return
                    }
                    var optI = -1
                    for (i in cmd.args.indices) {
                        if (parseOpt(cmd.args[i]) != null) {
                            optI = i
                            break
                        }
                    }
                    if (optI == -1) {
                        e.channel.sendMessageEmbeds(argsEmbed).queue()
                        return
                    }
                    playlist  = getPlaylistFmt(Array(optI) {cmd.args[it]}.joinToString(), pFmtHelper, false) ?: return
                    opt = parseOpt(cmd.args[optI])!!
                    value = cmd.args.joinToString(optI + 1)
                }
                if (opt == SetOpt.NAME) {
                    playlist.info.name = value
                } else {
                    playlist.info.description = value
                }
                e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN)
                    .addField("Changed this playlist's ${opt.name.toLowerCase()} to `${value.esc()}`!".toResultField(true)).build()).queue()
            }
            CommandName.DELETE -> {
                val pl = parseOnlyPlaylist(e, cmd.args, argsEmbed) ?: return
                if (pl.getUserID() != e.author.id) {
                    e.channel.sendMessageEmbeds(doesNotOwn).queue()
                    return
                }
                e.channel.sendMessageEmbeds(builder.setColor(Color.GREEN).addField("Deleting playlist ${pl.id}..".toResultField(true)).build()).queue()
                Executors.newSingleThreadExecutor().submit {
                    cache.remove(pl)
                    storageHandler.remove(pl)
                    println("User ${e.author.asTag} (id = ${e.author.id}) deleted playlist with id ${pl.id}!")
                }
            }
            CommandName.LIST -> {
                parseOnlyPlaylist(e, cmd.args, argsEmbed)?.let {
                    val tracks = it.getTracks()
                    val res = EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTimestamp(Instant.now())
                        .setTitle("Contents")
                    val sBuilder = StringBuilder()
                    for (i in tracks.indices) {
                        val t = tracks[i]
                        sBuilder.append("${i + 1}. `${t.info.title.esc()}` | `${t.info.author.esc()}` | `${t.duration.fmtMs()}`\n")
                    }
                    e.channel.sendMessageEmbeds(res.addField("Contents of ${it.info.name}:", sBuilder.toString(), false).build()).queue()
                }
            }
            CommandName.CLONE -> {
                parseOnlyPlaylist(e, cmd.args, argsEmbed)?.let {
                    val playlists = storageHandler.retrieveAllPlaylists(e.author.id)
                    if (playlists.size >= 5) {
                        e.channel.sendMessageEmbeds(builder.addField(("You have reached the playlist limit; " +
                                "please delete a playlist in order to create a new one!").toResultField()).build()).queue()
                        return
                    }
                    val newPl = it.clone("${e.author.id}-${nextId(playlists)}")
                    Executors.newSingleThreadExecutor().submit {
                        cache.cache(newPl)
                        storageHandler.sync(newPl)
                        println("User ${e.author.asTag} (id = ${e.author.id}) cloned an existing playlist (id ${it.id}) with id ${newPl.id}!")
                        e.channel
                            .sendMessageEmbeds(builder
                                .addField(("Cloned playlist with id ${it.id} to create" +
                                        " a new playlist with name ${newPl.info.name} and id ${newPl.id}!").toResultField(true)).setColor(
                                    Color.GREEN).build()).queue()
                    }
                }
            }
        }
    }


    private enum class SetOpt{
        DESCRIPTION,
        NAME
    }

    private fun parseOpt(opt : String) : SetOpt? {
        return when (opt.toLowerCase()) {
            "d" -> SetOpt.DESCRIPTION
            "desc" -> SetOpt.DESCRIPTION
            "description" -> SetOpt.DESCRIPTION
            "name" -> SetOpt.NAME
            "n" -> SetOpt.NAME
            else -> return null
        }
    }

    private fun parseOnlyPlaylist(e: GuildMessageReceivedEvent, args: Array<String>, argsEmbed: MessageEmbed) : Playlist? {
        val pFmtHelper = PlaylistFmtHelper(e, e.author)
        // arg0 (opt) id toggle, arg1 etc name or id of playlist
        if (args.isEmpty()) {
            e.channel.sendMessageEmbeds(argsEmbed).queue()
            return null
        }
        val idToggle =args[0].equals("id", ignoreCase = true)
        if (idToggle && args.size != 2) {
            e.channel.sendMessageEmbeds(argsEmbed).queue()
            return null
        }
        return if (idToggle) {
            getPlaylistFmt(args[1], pFmtHelper)
        } else {
            getPlaylistFmt(args.joinToString(0),pFmtHelper, false)
        }
    }

    private data class PlaylistFmtHelper(val e: GuildMessageReceivedEvent, val user: User)

    private fun getPlaylistFmt(name: String, p: PlaylistFmtHelper, id: Boolean = true) : Playlist? {
        val playlist = if (id) {
            safeGetPlaylistId(name)
        } else {
            safeGetPlaylist(name,p.user)
        }
        if (playlist == null) {
            p.e.channel.sendMessageEmbeds(name.toNoPlaylistEmbed(id)).queue()
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
                    .addField("Adding `${added[0].info.title.esc()}` to this playlist!".toResultField(true)).build()).queue()
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
}