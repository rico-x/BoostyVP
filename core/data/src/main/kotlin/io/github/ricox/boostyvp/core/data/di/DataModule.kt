package io.github.ricox.boostyvp.core.data.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.ricox.boostyvp.core.data.account.AccountIdCache
import io.github.ricox.boostyvp.core.data.account.BoostyCurrentAccount
import io.github.ricox.boostyvp.core.data.account.PreferencesAccountIdCache
import io.github.ricox.boostyvp.core.data.library.LibraryDatabase
import io.github.ricox.boostyvp.core.data.library.PersonalLibraryDao
import io.github.ricox.boostyvp.core.data.repository.RoomPersonalLibraryRepository
import io.github.ricox.boostyvp.core.domain.repository.PersonalLibraryRepository
import io.github.ricox.boostyvp.core.data.library.PlaybackProgressDao
import io.github.ricox.boostyvp.core.data.library.WatchHistoryDao
import io.github.ricox.boostyvp.core.data.repository.RoomWatchHistoryRepository
import io.github.ricox.boostyvp.core.data.repository.DefaultBlogRepository
import io.github.ricox.boostyvp.core.data.repository.DefaultFeedRepository
import io.github.ricox.boostyvp.core.data.repository.DefaultPostCommentsRepository
import io.github.ricox.boostyvp.core.data.repository.LegacyPlaybackPositions
import io.github.ricox.boostyvp.core.data.repository.RoomPlaybackProgressRepository
import io.github.ricox.boostyvp.core.data.repository.SharedPreferencesLegacyPositions
import io.github.ricox.boostyvp.core.domain.auth.CurrentAccount
import io.github.ricox.boostyvp.core.domain.repository.PostCommentsRepository
import io.github.ricox.boostyvp.core.domain.repository.BlogRepository
import io.github.ricox.boostyvp.core.domain.repository.FeedRepository
import io.github.ricox.boostyvp.core.domain.repository.PlaybackProgressRepository
import io.github.ricox.boostyvp.core.domain.repository.WatchHistoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    abstract fun bindPersonalLibrary(impl: RoomPersonalLibraryRepository): PersonalLibraryRepository
    @Binds
    abstract fun bindPostComments(impl: DefaultPostCommentsRepository): PostCommentsRepository

    @Binds
    abstract fun bindBlogRepository(impl: DefaultBlogRepository): BlogRepository

    @Binds
    abstract fun bindFeedRepository(impl: DefaultFeedRepository): FeedRepository

    @Binds
    abstract fun bindPlaybackProgress(impl: RoomPlaybackProgressRepository): PlaybackProgressRepository

    @Binds
    abstract fun bindWatchHistory(impl: RoomWatchHistoryRepository): WatchHistoryRepository

    @Binds
    abstract fun bindCurrentAccount(impl: BoostyCurrentAccount): CurrentAccount

    @Binds
    abstract fun bindAccountIdCache(impl: PreferencesAccountIdCache): AccountIdCache

    @Binds
    abstract fun bindLegacyPositions(impl: SharedPreferencesLegacyPositions): LegacyPlaybackPositions
}

@Module
@InstallIn(SingletonComponent::class)
object LibraryModule {
    @Provides
    @Singleton
    fun provideLibraryDatabase(@ApplicationContext context: Context): LibraryDatabase =
        Room.databaseBuilder(context, LibraryDatabase::class.java, LibraryDatabase.NAME)
            .addMigrations(LibraryDatabase.MIGRATION_1_2, LibraryDatabase.MIGRATION_2_3, LibraryDatabase.MIGRATION_3_4).build()

    @Provides
    fun providePersonalLibraryDao(database: LibraryDatabase): PersonalLibraryDao = database.personalLibrary()

    @Provides
    fun providePlaybackProgressDao(database: LibraryDatabase): PlaybackProgressDao = database.playbackProgress()

    @Provides
    fun provideWatchHistoryDao(database: LibraryDatabase): WatchHistoryDao = database.watchHistory()
}
