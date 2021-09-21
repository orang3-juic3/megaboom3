package me.zeddit.playlists

import me.zeddit.firstNullable

// A LRU cache for playlists
class PlaylistCache(private val maxSize : Int, private val storageHandler: PlaylistStorageHandler)  {

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

    fun getPlaylist(id: String): Playlist? {
        return deque.firstNullable  {
            it.id == id
        }.apply { this?.let {
            deque.remove(this)
            deque.addLast(this)
        }}
    }

    fun allItems() : Array<Playlist> {
        val iterator = deque.iterator()
        return Array(deque.size) {iterator.next()}
    }


}