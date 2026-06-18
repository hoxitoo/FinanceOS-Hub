package com.financeos.hub.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

// Repositories are @Singleton classes with @Inject constructors — Hilt binds them automatically.
// This module is a placeholder for future interface-based bindings if needed.
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
