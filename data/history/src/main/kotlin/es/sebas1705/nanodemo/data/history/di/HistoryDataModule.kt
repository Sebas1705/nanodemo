package es.sebas1705.nanodemo.data.history.di

import android.content.Context
import androidx.room.Room
import es.sebas1705.nanodemo.data.history.ConversationDao
import es.sebas1705.nanodemo.data.history.EmbeddingDao
import es.sebas1705.nanodemo.data.history.HistoryRepository
import es.sebas1705.nanodemo.data.history.IHistoryRepository
import es.sebas1705.nanodemo.data.history.IMetricsRepository
import es.sebas1705.nanodemo.data.history.MIGRATION_1_2
import es.sebas1705.nanodemo.data.history.MIGRATION_2_3
import es.sebas1705.nanodemo.data.history.MetricDao
import es.sebas1705.nanodemo.data.history.MetricsRepository
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

    @Binds
    @Singleton
    abstract fun bindMetricsRepository(impl: MetricsRepository): IMetricsRepository

    companion object {
        @Provides
        @Singleton
        fun provideNanoDemoDatabase(@ApplicationContext context: Context): NanoDemoDatabase =
            Room.databaseBuilder(context, NanoDemoDatabase::class.java, "nanodemo.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        @Provides
        fun provideConversationDao(database: NanoDemoDatabase): ConversationDao = database.conversationDao()

        @Provides
        fun provideMetricDao(database: NanoDemoDatabase): MetricDao = database.metricDao()

        @Provides
        fun provideEmbeddingDao(database: NanoDemoDatabase): EmbeddingDao = database.embeddingDao()
    }
}
