package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EditingSessionEntity::class,
        BatchExportItemEntity::class,
        EditedVideoMetadataEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class OpenShortsLocalDatabase : RoomDatabase() {
    abstract fun editingSessionDao(): EditingSessionDao
    abstract fun batchExportDao(): BatchExportDao
    abstract fun editedVideoMetadataDao(): EditedVideoMetadataDao

    companion object {
        @Volatile
        private var INSTANCE: OpenShortsLocalDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `edited_videos_metadata` (
                        `id` TEXT NOT NULL,
                        `userId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `videoUri` TEXT NOT NULL,
                        `outputFilePath` TEXT,
                        `galleryUri` TEXT,
                        `sourceVideoUri` TEXT,
                        `trimStartMs` INTEGER NOT NULL,
                        `trimEndMs` INTEGER NOT NULL,
                        `durationMs` INTEGER NOT NULL,
                        `appliedFiltersSummary` TEXT NOT NULL,
                        `captionCount` INTEGER NOT NULL,
                        `exportSource` TEXT NOT NULL,
                        `createdAtMs` INTEGER NOT NULL,
                        `updatedAtMs` INTEGER NOT NULL,
                        `exportedAtMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_edited_videos_metadata_userId_exportedAtMs` ON `edited_videos_metadata` (`userId`, `exportedAtMs`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_edited_videos_metadata_videoUri` ON `edited_videos_metadata` (`videoUri`)"
                )
            }
        }

        fun getInstance(context: Context): OpenShortsLocalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    OpenShortsLocalDatabase::class.java,
                    "openshorts_local.db"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
