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

/** Add metadata/indexes only. Existing locator ciphertext and encryption contexts stay intact. */
internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_entries ADD COLUMN categoryKey TEXT")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN year INTEGER")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN rating REAL")
        db.execSQL("ALTER TABLE library_entries ADD COLUMN artwork BLOB")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_entries_sourceId_kind_position ON library_entries(sourceId, kind, position)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_library_entries_sourceId_groupLabel_position ON library_entries(sourceId, groupLabel, position)")
        db.execSQL("CREATE TABLE IF NOT EXISTS favourites (sourceId TEXT NOT NULL, itemId INTEGER NOT NULL, PRIMARY KEY(sourceId,itemId))")
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS entry_search USING FTS4(searchText TEXT NOT NULL, tokenize=unicode61)")
        // SQL backfill needs no source unwrap or playback decryption. Match normalizeSearch's Arabic map.
        // Limit nested expressions for older SQLite parser stacks; apply batches of replaces in SQL updates.
        db.execSQL("INSERT INTO entry_search(rowid,searchText) SELECT id, lower(coalesce(title,'') || ' ' || coalesce(groupLabel,'')) FROM library_entries")
        for (ch in listOf('\u0640', *('\u064b'..'\u065f').toList().toTypedArray(), '\u0670', *('\u06d6'..'\u06ed').toList().toTypedArray())) {
            db.execSQL("UPDATE entry_search SET searchText = replace(searchText,?, '') WHERE searchText LIKE ?", arrayOf(ch.toString(), "%$ch%"))
        }
        for (ch in "\u0622\u0623\u0625\u0671") db.execSQL("UPDATE entry_search SET searchText = replace(searchText,?, '\u0627') WHERE searchText LIKE ?", arrayOf(ch.toString(), "%$ch%"))
    }
}
