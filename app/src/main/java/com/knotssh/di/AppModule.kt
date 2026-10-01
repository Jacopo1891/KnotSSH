package com.knotssh.di

import android.content.Context
import androidx.room.Room
import com.knotssh.data.local.db.CredentialDao
import com.knotssh.data.local.db.CustomKeyDao
import com.knotssh.data.local.db.FolderDao
import com.knotssh.data.local.db.KnownHostDao
import com.knotssh.data.local.db.QuickCommandDao
import com.knotssh.data.local.db.ServerDao
import com.knotssh.data.local.db.KnotSshDatabase
import com.knotssh.data.repository.CredentialRepositoryImpl
import com.knotssh.data.repository.CustomKeyRepositoryImpl
import com.knotssh.data.repository.FolderRepositoryImpl
import com.knotssh.data.repository.KnownHostRepositoryImpl
import com.knotssh.data.repository.QuickCommandRepositoryImpl
import com.knotssh.data.repository.ServerRepositoryImpl
import com.knotssh.domain.repository.CredentialRepository
import com.knotssh.domain.repository.CustomKeyRepository
import com.knotssh.domain.repository.FolderRepository
import com.knotssh.domain.repository.KnownHostRepository
import com.knotssh.domain.repository.QuickCommandRepository
import com.knotssh.domain.repository.ServerRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KnotSshDatabase =
        Room.databaseBuilder(context, KnotSshDatabase::class.java, KnotSshDatabase.DATABASE_NAME)
            .addMigrations(KnotSshDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideServerDao(db: KnotSshDatabase): ServerDao = db.serverDao()

    @Provides
    fun provideCredentialDao(db: KnotSshDatabase): CredentialDao = db.credentialDao()

    @Provides
    fun provideQuickCommandDao(db: KnotSshDatabase): QuickCommandDao = db.quickCommandDao()

    @Provides
    fun provideKnownHostDao(db: KnotSshDatabase): KnownHostDao = db.knownHostDao()

    @Provides
    fun provideCustomKeyDao(db: KnotSshDatabase): CustomKeyDao = db.customKeyDao()

    @Provides
    fun provideFolderDao(db: KnotSshDatabase): FolderDao = db.folderDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindServerRepository(impl: ServerRepositoryImpl): ServerRepository

    @Binds
    @Singleton
    abstract fun bindCredentialRepository(impl: CredentialRepositoryImpl): CredentialRepository

    @Binds
    @Singleton
    abstract fun bindQuickCommandRepository(impl: QuickCommandRepositoryImpl): QuickCommandRepository

    @Binds
    @Singleton
    abstract fun bindKnownHostRepository(impl: KnownHostRepositoryImpl): KnownHostRepository

    @Binds
    @Singleton
    abstract fun bindCustomKeyRepository(impl: CustomKeyRepositoryImpl): CustomKeyRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
