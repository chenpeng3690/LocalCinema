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
    version = 3,
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

        /** v2 → v3：记录用户手动挂载的字幕，重扫与重启后仍能自动加载 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE videos ADD COLUMN manualSubtitleUri TEXT")
            }
        }

        val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
    }
}
