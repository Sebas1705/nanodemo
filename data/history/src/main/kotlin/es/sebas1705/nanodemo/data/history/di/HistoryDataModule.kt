package es.sebas1705.nanodemo.data.history.di

import android.content.Context
import androidx.room.Room
import es.sebas1705.nanodemo.data.history.ConversationDao
import es.sebas1705.nanodemo.data.history.HistoryRepository
import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.data.history.NanoDemoDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryDataModule {

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepository): IHistoryRepository

    companion object {
        @Provides
        @Singleton
        fun provideNanoDemoDatabase(@ApplicationContext context: Context): NanoDemoDatabase =
            Room.databaseBuilder(context, NanoDemoDatabase::class.java, "nanodemo.db").build()

        @Provides
        fun provideConversationDao(database: NanoDemoDatabase): ConversationDao = database.conversationDao()
    }
}
