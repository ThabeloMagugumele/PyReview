package com.pyreview.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [UserProfile::class, CodeSubmission::class, Review::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun reviewDao(): ReviewDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pyreview.db")
                .addMigrations(ADD_ARCHIVED, DROP_OFFLINE_ACCOUNTS)
                .build()

        val ADD_ARCHIVED = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reviews ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val DROP_OFFLINE_ACCOUNTS = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS local_accounts")
                db.execSQL("DROP TABLE IF EXISTS coding_standards")
                db.execSQL("UPDATE reviews SET reviewStatus = 'QUICK_CHECK' WHERE reviewStatus = 'DEMO'")
            }
        }
    }
}
