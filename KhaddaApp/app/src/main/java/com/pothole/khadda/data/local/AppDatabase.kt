package com.pothole.khadda.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.pothole.khadda.model.DetectionSession
import com.pothole.khadda.model.PotholeEvent

/**
 * Local Room Database implementation.
 * Matches UML Class Diagram: LocalDatabase (dbName = "khadda_pothole.db").
 */
@Database(
    entities = [PotholeEvent::class, DetectionSession::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun potholeDao(): PotholeDao
    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "khadda_pothole.db"
                ).fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
