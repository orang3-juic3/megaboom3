package me.zeddit

import me.zeddit.playlists.PlaylistCache
import me.zeddit.playlists.PlaylistStorageHandler
import me.zeddit.playlists.PlaylistsCommandListener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import java.util.*
import kotlin.collections.HashSet
import kotlin.system.exitProcess

var jda : JDA? = null
val storageHandler = PlaylistStorageHandler()
val cache : PlaylistCache = PlaylistCache(20)
val guilds: MutableSet<AudioGuild> = HashSet()
fun main(args: Array<String>) {
    val token = args[0]
    val listener = CommandListener()
    storageHandler.init()
    jda = JDABuilder.createDefault(token)
        .setEventManager(AnnotatedEventManager())
        .addEventListeners(listener, PlaylistsCommandListener())
        .build().awaitReady()
    sysInThread.start()
}
private val sysInThread = Thread {
    val sc = Scanner(System.`in`)
    var next : String
    while (true) {
        next = sc.next()
        if (next.equals("e", true)) {
            println("Shutting down..")
            cache.allItems().forEach {
                storageHandler.sync(it)
                println("Saved cached playlist with id ${it.id}!")
            }
            storageHandler.close()
            exitProcess(0)
        }
    }
}