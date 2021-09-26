package command

import me.zeddit.filterMap
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent
import net.dv8tion.jda.api.hooks.SubscribeEvent
import java.awt.Color
import java.time.Instant

class GeneralCommandListener {

    private val commands = ArrayList<Command>()

    @SubscribeEvent
    fun onGuildMessageReceived(e: GuildMessageReceivedEvent) {
        if (e.isWebhookMessage) return
        if (e.author.isBot) return
        if (!e.message.contentRaw.startsWith("!")) return
        val results = commands.map { Pair(it, it.parseCommand(e.message.contentRaw, e)) }
            .filterMap({it.second != null}, {it -> Pair(it.first, it.second!!)})
        if (results.isEmpty()) {
            e.channel.sendMessageEmbeds(
                EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Error")
                    .addField("Something went wrong!", "Unknown command!", false)
                    .setTimestamp(Instant.now()).build()).queue()
            return
        }
        results.forEach {
            it.first.onCommand(it.second, e)
        }
    }

    fun addCommands(vararg commands: Command) {
        commands.forEach { this.commands.add(it) }
    }
}