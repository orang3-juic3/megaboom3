package me.zeddit

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.hooks.AnnotatedEventManager

var jda : JDA? = null

fun main(args: Array<String>) {
    val token = args[0]
    val listener = CommandListener()
    jda = JDABuilder.createDefault(token)
        .setEventManager(AnnotatedEventManager())
        .addEventListeners(listener)
        .build().awaitReady()
}