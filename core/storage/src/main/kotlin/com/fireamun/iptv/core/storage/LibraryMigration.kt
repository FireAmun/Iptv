package com.fireamun.iptv.core.storage

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Preserve v1 source UUID/generation/envelope and item IDs; no ciphertext transformation. */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE source_state ADD COLUMN kind TEXT")
        db.execSQL("ALTER TABLE source_state ADD COLUMN label BLOB")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN sourceId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE library_entries SET sourceId = (SELECT sourceId FROM source_state WHERE slot = 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS active_library (slot INTEGER NOT NULL, sourceId TEXT NOT NULL, PRIMARY KEY(slot))")
        db.execSQL("INSERT INTO active_library (slot, sourceId) SELECT 1, sourceId FROM source_state WHERE slot = 1")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_source_state_sourceId ON source_state(sourceId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_entries_sourceId_position ON library_entries(sourceId, position)")
    }
}
