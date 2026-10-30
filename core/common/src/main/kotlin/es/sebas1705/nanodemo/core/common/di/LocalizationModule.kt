package es.sebas1705.nanodemo.core.common.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import es.sebas1705.nanodemo.core.common.AppTextsProvider
import es.sebas1705.nanodemo.core.common.R
import es.sebas1705.nanodemo.core.common.appTextsFor
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LocalizationModule {

    /**
     * Se lee un resource en vez de `Configuration.locales[0]` porque el primer idioma de la lista no siempre es
     * el de la UI: qué carpeta `values-*` gana lo decide Android (y depende también de las traducciones de las
     * librerías), y un resource de esas mismas carpetas siempre coincide con lo que se ve en pantalla.
     * Se resuelve en cada llamada para seguir un cambio de idioma con la app abierta.
     */
    @Provides
    @Singleton
    fun provideAppTextsProvider(@ApplicationContext context: Context): AppTextsProvider =
        AppTextsProvider { appTextsFor(context.getString(R.string.ui_language)) }
}
