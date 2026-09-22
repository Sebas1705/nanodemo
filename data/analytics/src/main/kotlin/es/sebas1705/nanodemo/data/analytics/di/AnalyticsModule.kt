package es.sebas1705.nanodemo.data.analytics.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import es.sebas1705.nanodemo.core.common.AnalyticsTracker
import es.sebas1705.nanodemo.data.analytics.FirebaseAnalyticsTracker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    @Singleton
    abstract fun bindAnalyticsTracker(impl: FirebaseAnalyticsTracker): AnalyticsTracker
}
