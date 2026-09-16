package com.example.geminifnanodemo.data.repositories.di

import com.example.geminifnanodemo.data.repositories.interfaces.IInferenceRepository
import com.example.geminifnanodemo.data.repositories.repos.InferenceRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesDataModule {

    @Binds
    @Singleton
    abstract fun bindInferenceRepository(impl: InferenceRepository): IInferenceRepository
}
