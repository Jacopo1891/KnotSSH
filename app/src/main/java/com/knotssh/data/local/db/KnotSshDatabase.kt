package com.knotssh.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ServerEntity::class,
        CredentialEntity::class,
        QuickCommandEntity::class,
        KnownHostEntity::class,
        CustomKeyEntity::class,
        FolderEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class KnotSshDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun credentialDao(): CredentialDao
    abstract fun quickCommandDao(): QuickCommandDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun customKeyDao(): CustomKeyDao
    abstract fun folderDao(): FolderDao

    companion object {
        const val DATABASE_NAME = "knotssh.db"

        /** Folders and manual ordering. Written by hand so existing servers survive the upgrade. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `folders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL, " +
                        "`isExpanded` INTEGER NOT NULL)"
                )
                db.execSQL("ALTER TABLE `servers` ADD COLUMN `folderId` INTEGER")
                db.execSQL("ALTER TABLE `servers` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
