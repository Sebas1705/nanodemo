package es.sebas1705.nanodemo.data.crashlytics.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import es.sebas1705.nanodemo.core.common.ErrorReporter
import es.sebas1705.nanodemo.data.crashlytics.CrashlyticsErrorReporter
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashlyticsModule {

    @Binds
    @Singleton
    abstract fun bindErrorReporter(impl: CrashlyticsErrorReporter): ErrorReporter
}
