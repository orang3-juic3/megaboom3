package command

import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent

interface Command {
    fun parseCommand(cmd: String, e: GuildMessageReceivedEvent) : CommandResult?
    fun onCommand(cmd: CommandResult, e: GuildMessageReceivedEvent)
}