package com.knotssh.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ServerEntity::class,
        CredentialEntity::class,
        QuickCommandEntity::class,
        KnownHostEntity::class,
        CustomKeyEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KnotSshDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun credentialDao(): CredentialDao
    abstract fun quickCommandDao(): QuickCommandDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun customKeyDao(): CustomKeyDao

    companion object {
        const val DATABASE_NAME = "knotssh.db"
    }
}
