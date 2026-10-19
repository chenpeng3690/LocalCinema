package com.localcinema.app.di

import android.content.Context
import androidx.room.Room
import com.localcinema.app.data.db.AlbumDao
import com.localcinema.app.data.db.AppDatabase
import com.localcinema.app.data.db.PlaybackStateDao
import com.localcinema.app.data.db.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideAlbumDao(db: AppDatabase): AlbumDao = db.albumDao()

    @Provides
    fun provideVideoDao(db: AppDatabase): VideoDao = db.videoDao()

    @Provides
    fun providePlaybackStateDao(db: AppDatabase): PlaybackStateDao = db.playbackStateDao()
}
