package com.poultryguard.ai.data.cache

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.poultryguard.ai.data.model.MortalityRecord
import com.poultryguard.ai.data.model.Veterinarian
import com.poultryguard.ai.data.model.SystemStats

@Database(entities = [MortalityRecord::class, Veterinarian::class, SystemStats::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun mortalityDao(): MortalityDao
    abstract fun vetDao(): VetDao
    abstract fun systemStatsDao(): SystemStatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "poultry_guard_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
