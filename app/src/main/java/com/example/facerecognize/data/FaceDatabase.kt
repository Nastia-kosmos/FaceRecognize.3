package com.example.facerecognize.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FaceEntity::class], version = 3)
@TypeConverters(FaceConverters::class)
abstract class FaceDatabase : RoomDatabase() {
    abstract fun faceDao(): FaceDao

    companion object {
        @Volatile
        private var INSTANCE: FaceDatabase? = null

        fun getDatabase(context: Context): FaceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FaceDatabase::class.java,
                    "face_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE faces ADD COLUMN age TEXT NOT NULL DEFAULT ''")
            }
        }
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Создаем временную таблицу с новой структурой
                database.execSQL("""
                    CREATE TABLE faces_temp (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        imagePath TEXT NOT NULL,
                        faceEmbedding TEXT NOT NULL,
                        age TEXT NOT NULL DEFAULT '',
                        imageHash TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """)
                
                // Копируем данные из старой таблицы во временную
                database.execSQL("""
                    INSERT INTO faces_temp (id, name, imagePath, faceEmbedding, age, timestamp)
                    SELECT id, name, imagePath, faceEmbedding, age, timestamp FROM faces
                """)
                
                // Удаляем старую таблицу
                database.execSQL("DROP TABLE faces")
                
                // Переименовываем временную таблицу
                database.execSQL("ALTER TABLE faces_temp RENAME TO faces")
            }
        }
    }
} 