package com.example.facerecognize.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FaceEntity::class], version = 2)
@TypeConverters(FaceConverters::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        // Миграция с версии 1 на версию 2
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Добавляем столбец age с пустым значением по умолчанию
                database.execSQL("ALTER TABLE faces ADD COLUMN age TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_database"
                )
                .addMigrations(MIGRATION_1_2)  // Добавляем миграцию
                .fallbackToDestructiveMigration()  // Если миграция не сработает, пересоздать базу
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 