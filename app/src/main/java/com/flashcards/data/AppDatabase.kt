package com.flashcards.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Deck::class, Card::class, ReviewLog::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deckDao(): DeckDao
    abstract fun cardDao(): CardDao
    abstract fun reviewLogDao(): ReviewLogDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2: review log for stats, folders for decks. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Deck ADD COLUMN folder TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS ReviewLog (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        cardId INTEGER NOT NULL,
                        deckId INTEGER NOT NULL,
                        grade INTEGER NOT NULL,
                        at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ReviewLog_at ON ReviewLog(at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_ReviewLog_deckId ON ReviewLog(deckId)")
            }
        }

        /** v3: per-deck notification switch. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Deck ADD COLUMN notify INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "flashcards.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
