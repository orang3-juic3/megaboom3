package me.zeddit.playlists

class InvalidTrackException(val urls: List<String>, val id: String) : Exception() {
    constructor(url: String, id: String) : this(listOf(url), id)
}