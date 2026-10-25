package com.fireamun.iptv.core.storage

import androidx.room.*

@Entity(tableName = "source_state")
internal data class SourceRow(@PrimaryKey val slot: Int = 1, val sourceId: String, val generation: String, val secret: ByteArray, val savedAt: Long) {
    override fun toString() = "[protected source row]"
}
@Entity(tableName = "item_identities", indices = [Index(value = ["sourceId", "identityKey"], unique = true)])
internal data class IdentityRow(@PrimaryKey val id: Int, val sourceId: String, val identityKey: String)
@Entity(tableName = "library_entries")
internal data class EntryRow(@PrimaryKey val id: Int, val position: Int, val title: String?, val groupLabel: String?, val kind: String, val seriesId: Long?, val providerKey: String?, val address: ByteArray?) {
    override fun toString() = "[protected catalogue row]"
}
@Dao
internal interface LibraryDao {
    @Query("SELECT * FROM source_state WHERE slot = 1") suspend fun source(): SourceRow?
    @Query("SELECT * FROM library_entries ORDER BY position") suspend fun entries(): List<EntryRow>
    @Query("SELECT * FROM item_identities WHERE sourceId = :sourceId") suspend fun identities(sourceId: String): List<IdentityRow>
    @Query("SELECT COALESCE(MAX(id), -1) FROM item_identities") suspend fun maxId(): Int
    @Insert suspend fun insertIdentities(rows: List<IdentityRow>)
    @Insert suspend fun insertEntries(rows: List<EntryRow>)
    @Upsert suspend fun source(row: SourceRow)
    @Query("DELETE FROM library_entries") suspend fun clearEntries()
    @Query("DELETE FROM item_identities") suspend fun clearIdentities()
    @Query("DELETE FROM source_state") suspend fun clearSource()
}
@Database(entities = [SourceRow::class, IdentityRow::class, EntryRow::class], version = 1, exportSchema = true)
internal abstract class LibraryDatabase : RoomDatabase() { abstract fun library(): LibraryDao }
