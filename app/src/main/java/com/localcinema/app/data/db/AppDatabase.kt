package com.localcinema.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumEntity::class,
        VideoEntity::class,
        PlaybackStateEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun videoDao(): VideoDao
    abstract fun playbackStateDao(): PlaybackStateDao

    companion object {
        const val NAME = "local_cinema.db"

        /** v1 → v2：视频表新增外挂字幕字段，不影响已有记录与进度 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN subtitleUri TEXT")
            }
        }
    }
}
