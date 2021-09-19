package me.zeddit.playlists

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager
import java.io.File
import java.sql.Connection
import java.sql.DriverManager


// This class will block
class PlaylistStorageHandler(private val playerManager: AudioPlayerManager) : AutoCloseable {

    private val file: File = File("playlists.db")
    private val dbPath = "jdbc:sqlite:${file.absolutePath}"
    private lateinit var connection: Connection
    // this shouldn't be called more than once!
    fun init() {
        var firstTime = false
        if (!file.exists()) {
            firstTime = true
            firstTimeSetup()
        }
        if (!firstTime) {
            println("Set up playlists db for the first time!")
        } else {
            connection = DriverManager.getConnection(dbPath)
        }
    }

    private fun firstTimeSetup() {
        file.createNewFile()
        connection = DriverManager.getConnection(dbPath)
        val stmt = connection.createStatement()
        stmt.executeUpdate("CREATE TABLE PlaylistItems(id varchar(255), url varchar(255), i INT);")
        stmt.executeUpdate("CREATE TABLE PlaylistMeta(id varchar(255), uid varchar(30), pid INT, name varchar(255), description varchar(255));")

    }


    @Synchronized
    fun sync(playlist: Playlist) {
        syncMeta(playlist)
        syncTracks(playlist)
    }

    private fun syncMeta(playlist: Playlist) {
        var stmt = connection.prepareStatement("SELECT * FROM PlaylistMeta WHERE id = ?;")
        stmt.setString(1, playlist.id)
        val res = stmt.executeQuery()
        val isEmpty = res.next()
        if (isEmpty) {
            stmt = connection.prepareStatement("INSERT INTO PlaylistMeta VALUES (?, ?, ?, ?, ?);")
        } else {
            stmt = connection.prepareStatement("UPDATE PlaylistMeta SET id=?, uid=?, pid=?, name=?, description=? WHERE id = ?;")
            stmt.setString(6, playlist.id)
        }
        stmt.setString(1, playlist.id)
        stmt.setString(2, playlist.getUserID())
        stmt.setString(3, playlist.getPlaylistNumber())
        stmt.setString(4, playlist.info.name)
        stmt.setString(5, playlist.info.description)
    }

    private fun syncTracks(playlist: Playlist) {
        var stmt = connection.prepareStatement("DELETE FROM PlaylistItems WHERE id = ?;")
        stmt.setString(1, playlist.id)
        stmt.executeUpdate()
        playlist.audioTracks.map { it.info.uri }.forEachIndexed {i, it->
            stmt = connection.prepareStatement("INSERT INTO PlaylistItems VALUES (?,?,?);")
            stmt.setString(1, playlist.id)
            stmt.setString(2, it)
            stmt.setInt(3, i)
        }
    }

    fun retrieveAllPlaylists(uid: String) : List<Playlist> {
        var stmt = connection.prepareStatement("SELECT * FROM PlaylistMeta WHERE uid = ?;")
        stmt.setString(1, uid)
        val res = stmt.executeQuery()
        val playlists = ArrayList<Playlist>()
        while (res.next()) {
            val info = Playlist.Info(res.getString(4), res.getString(5))
            val id = res.getString(1)
            val playlistNo = res.getInt(3)
            stmt = connection.prepareStatement("SELECT * FROM PlaylistItems WHERE id = ? ORDER BY i;")
            stmt.setString(1, id)
            val miniRes = stmt.executeQuery()
            val tracks : MutableList<String> = ArrayList()
            while (miniRes.next()) {
                tracks.add(miniRes.getString(2))
            }
            playlists.add(Playlist(tracks, id, info, playerManager))
        }
        return playlists
    }

    fun retrievePlaylist(id: String) : Playlist {
        var stmt = connection.prepareStatement("SELECT * FROM PlaylistMeta WHERE id = ?")
        stmt.setString(1, id)
        val res = stmt.executeQuery()
        while (res.next()) {

        }
    }

    override fun close() {
        if (this::connection.isInitialized) {
            connection.close()
        }
    }
}