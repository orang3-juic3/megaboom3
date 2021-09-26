package me.zeddit.playlists

import me.zeddit.firstNullable
import me.zeddit.storageHandler

// A LRU cache for playlists
class PlaylistCache(private val maxSize : Int)  {

    private val deque = ArrayDeque<Playlist>()


    @Synchronized
    fun cache(playlist: Playlist) {
        if (deque.contains(playlist)) {
            deque.remove(playlist)
        }
        if (deque.size >= maxSize) {
            storageHandler.sync(deque.first())
            deque.removeFirst()
        }
        deque.addLast(playlist)
    }

    fun getPlaylist(id: String): Playlist? = getPlaylist { it.id == id }

    fun getPlaylist(predicate: (Playlist) -> Boolean) : Playlist? {
        return deque.firstNullable  {
            predicate.invoke(it)
        }.apply { this?.let {
            deque.remove(this)
            deque.addLast(this)
        }}
    }

    fun allItems() : Array<Playlist> {
        val iterator = deque.iterator()
        return Array(deque.size) {iterator.next()}
    }

    fun remove(playlist: Playlist) {
        deque.remove(playlist)
    }

}