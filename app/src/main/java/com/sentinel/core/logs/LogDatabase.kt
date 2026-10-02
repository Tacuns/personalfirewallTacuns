package com.sentinel.core.logs

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PacketLogEntity::class, ChangeHistoryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class LogDatabase : RoomDatabase() {

    abstract fun packetLogDao(): PacketLogDao

    abstract fun changeHistoryDao(): ChangeHistoryDao

    companion object {
        @Volatile private var INSTANCE: LogDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE packet_logs ADD COLUMN threatLabel TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        // Adds the change-history table. Existing logs and rules are untouched, so an
        // update can never lose a user's rules. The statement matches the table Room
        // generates for ChangeHistoryEntity exactly, or Room rejects the migration.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `change_history` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`timestampMs` INTEGER NOT NULL, " +
                        "`action` TEXT NOT NULL, " +
                        "`target` TEXT NOT NULL, " +
                        "`beforeValue` TEXT NOT NULL, " +
                        "`afterValue` TEXT NOT NULL)"
                )
            }
        }

        fun getInstance(context: Context): LogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LogDatabase::class.java,
                    "sentinel-logs.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .enableMultiInstanceInvalidation()
                .build()
                .also { INSTANCE = it }
            }
    }
}
