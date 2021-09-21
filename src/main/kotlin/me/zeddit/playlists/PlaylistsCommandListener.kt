package me.zeddit.playlists

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers
import me.zeddit.AudioGuild
import me.zeddit.GenericCommandListener
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent

class PlaylistsCommandListener(guilds : MutableSet<AudioGuild>) : GenericCommandListener(guilds) {

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
        val audioGuild : AudioGuild = guilds.firstOrNull { it.id == id } ?: AudioGuild(playerManager, e.guild).apply { guilds.add(this) }
        if (!e.message.contentRaw.matches(Regex("!pl(aylist)?\\s+.+", RegexOption.IGNORE_CASE))) {
            return
        }
        val cmd = e.message.contentRaw.split(sp).let { splits ->
            Array(splits.size - 1) {splits[it+1]}.joinToString { "" }
        }
        val result =parseCommand(cmd, e) ?: run {
            return
        }
        when(result.commandName) {
            CommandName.ADD -> {

            }
            CommandName.REMOVE -> TODO()
            CommandName.QUEUE -> TODO()
            CommandName.CREATE -> TODO()
            CommandName.DELETE -> TODO()
            CommandName.LIST -> TODO()
            CommandName.DESCRIPTION -> TODO()
            CommandName.NAME -> TODO()
        }
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
            else -> return null
        }
        return CommandResult(commandName, args)
    }
}