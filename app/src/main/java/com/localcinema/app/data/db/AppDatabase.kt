package com.localcinema.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AlbumEntity::class,
        VideoEntity::class,
        PlaybackStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun videoDao(): VideoDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        const val NAME = "local_cinema.db"
    }
}
