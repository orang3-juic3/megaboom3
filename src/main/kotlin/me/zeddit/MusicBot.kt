package me.zeddit

import me.zeddit.playlists.PlaylistCache
import me.zeddit.playlists.PlaylistStorageHandler
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager
import java.util.*

var jda : JDA? = null
val cache : PlaylistCache = PlaylistCache(20)
val storageHandler = PlaylistStorageHandler()

fun main(args: Array<String>) {
    val token = args[0]
    val listener = CommandListener()
    jda = JDABuilder.createDefault(token)
        .setEventManager(AnnotatedEventManager())
        .addEventListeners(listener)
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
            storageHandler.close()
            cache.allItems().forEach {
                storageHandler.sync(it)
                println("Saved cached playlist with id ${it.id}!")
            }
        }
    }
}