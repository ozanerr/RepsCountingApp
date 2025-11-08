package com.example.repscountingapp.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LatihanHistory::class], version = 1)
abstract class AppDatabase : RoomDatabase() {

    // Kasi tau database-nya kalau kita punya DAO ini
    abstract fun latihanDao(): LatihanDao

    companion object {
        // 'Volatile' biar nilainya selalu update di semua thread
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Ini pola 'singleton', biar database-nya cuma dibikin sekali
        fun getInstance(context: Context): AppDatabase {
            synchronized(this) {
                var instance = INSTANCE
                if (instance == null) {
                    instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "reps_counter_database"
                    ).build()
                    INSTANCE = instance
                }
                return instance
            }
        }
    }
}
