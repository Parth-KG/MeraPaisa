package com.kg.merapaisa.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Person::class, Transaction::class], version = 5, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun personDao(): PersonDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mera_paisa_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }
    }
}