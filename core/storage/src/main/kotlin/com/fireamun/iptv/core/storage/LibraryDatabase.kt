package com.fireamun.iptv.core.storage

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Entity(tableName = "source_state", indices = [Index(value = ["sourceId"], unique = true)])
internal data class SourceRow(@PrimaryKey val slot: Int = 1, val sourceId: String, val generation: String, val secret: ByteArray, val savedAt: Long, val kind: String? = null, val label: ByteArray? = null) {
    override fun toString() = "[protected source row]"
}
@Entity(tableName = "item_identities", indices = [Index(value = ["sourceId", "identityKey"], unique = true)])
internal data class IdentityRow(@PrimaryKey val id: Int, val sourceId: String, val identityKey: String)
@Entity(tableName = "library_entries", indices = [Index(value = ["sourceId", "position"]), Index(value = ["sourceId", "kind", "position"]), Index(value = ["sourceId", "groupLabel", "position"])])
internal data class EntryRow(@PrimaryKey val id: Int, val position: Int, val title: String?, val groupLabel: String?, val kind: String, val seriesId: Long?, val providerKey: String?, val address: ByteArray?, @ColumnInfo(defaultValue = "''") val sourceId: String,
    val categoryKey: String? = null, val year: Int? = null, val rating: Double? = null, val artwork: ByteArray? = null) {
    override fun toString() = "[protected catalogue row]"
}
@Entity(tableName = "active_library")
internal data class ActiveRow(@PrimaryKey val slot: Int = 1, val sourceId: String)
@Fts4(tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "entry_search")
internal data class SearchRow(@PrimaryKey @ColumnInfo(name = "rowid") val id: Int, val searchText: String)
@Entity(tableName = "favourites", primaryKeys = ["sourceId", "itemId"])
internal data class FavouriteRow(val sourceId: String, val itemId: Int)
@Entity(tableName = "watch_progress", primaryKeys = ["sourceId", "refDigest"], indices = [Index(value = ["sourceId", "kind", "completed", "watchedAtMs"])])
internal data class WatchProgressRow(val sourceId: String, val refDigest: String, val rootItemId: Int, val kind: String, val positionMs: Long,
    val durationMs: Long?, val completed: Boolean, val watchedAtMs: Long, val snapshot: ByteArray?) {
    override fun toString() = "[protected watch progress row]"
}
internal data class JoinedWatchRow(@Embedded val watch: WatchProgressRow, val rootTitle: String?, val rootGroup: String?, val rootKind: String, val rootArtwork: ByteArray?, val generation: String)
internal data class BrowseRow(val id: Int, val title: String?, val groupLabel: String?, val kind: String, val categoryKey: String?, val year: Int?, val rating: Double?, val hasArtwork: Boolean, val favourite: Boolean)
@Dao
internal interface LibraryDao {
    @Query("SELECT * FROM source_state WHERE sourceId = (SELECT sourceId FROM active_library WHERE slot = 1)") suspend fun source(): SourceRow?
    @Query("SELECT * FROM source_state WHERE sourceId = :sourceId") suspend fun source(sourceId: String): SourceRow?
    @Query("SELECT * FROM source_state ORDER BY slot") suspend fun sources(): List<SourceRow>
    @Query("SELECT sourceId FROM active_library WHERE slot = 1") suspend fun active(): String?
    @Query("SELECT COALESCE(MAX(slot), 0) FROM source_state") suspend fun maxSlot(): Int
    @Query("SELECT * FROM library_entries WHERE sourceId = :sourceId ORDER BY position") suspend fun entries(sourceId: String): List<EntryRow>
    @Query("SELECT * FROM library_entries WHERE sourceId = :sourceId AND id = :id") suspend fun entry(sourceId: String, id: Int): EntryRow?
    @Query("SELECT COUNT(*) FROM library_entries WHERE sourceId = :sourceId") suspend fun count(sourceId: String): Int
    @RawQuery suspend fun browse(query: SupportSQLiteQuery): List<BrowseRow>
    @RawQuery suspend fun browseCount(query: SupportSQLiteQuery): Int
    @RawQuery suspend fun categories(query: SupportSQLiteQuery): List<String>
    @Upsert suspend fun favourite(row: FavouriteRow)
    @Query("DELETE FROM favourites WHERE sourceId = :sourceId AND itemId = :id") suspend fun clearFavourite(sourceId: String, id: Int)
    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE sourceId = :sourceId AND itemId = :id)") suspend fun isFavourite(sourceId: String, id: Int): Boolean
    @Query("SELECT itemId FROM favourites WHERE sourceId = :sourceId") suspend fun favouriteIds(sourceId: String): List<Int>
    @Query("DELETE FROM favourites WHERE sourceId = :sourceId") suspend fun clearFavourites(sourceId: String)
    @Query("DELETE FROM favourites") suspend fun clearFavourites()
    @Query("SELECT * FROM watch_progress WHERE sourceId = :sourceId AND refDigest = :refDigest") suspend fun progress(sourceId: String, refDigest: String): WatchProgressRow?
    @Upsert suspend fun progress(row: WatchProgressRow)
    @Query("DELETE FROM watch_progress WHERE sourceId = :sourceId AND refDigest = :refDigest") suspend fun clearProgress(sourceId: String, refDigest: String)
    @Query("DELETE FROM watch_progress WHERE sourceId = :sourceId") suspend fun clearProgress(sourceId: String)
    @Query("DELETE FROM watch_progress") suspend fun clearProgress()
    @Query("""SELECT w.*, e.title AS rootTitle, e.groupLabel AS rootGroup, e.kind AS rootKind, e.artwork AS rootArtwork, s.generation AS generation
        FROM watch_progress w JOIN library_entries e ON e.sourceId = w.sourceId AND e.id = w.rootItemId
        JOIN source_state s ON s.sourceId = w.sourceId
        WHERE w.sourceId = :sourceId AND w.kind = :kind AND w.completed = 0
        AND ((w.kind = 'MOVIE' AND e.kind = 'MOVIE' AND w.snapshot IS NULL)
          OR (w.kind = 'EPISODE' AND e.kind = 'EPISODE' AND w.snapshot IS NULL)
          OR (w.kind = 'EPISODE' AND e.kind = 'SERIES' AND w.snapshot IS NOT NULL))
        ORDER BY w.watchedAtMs DESC, w.refDigest DESC LIMIT :limit""") suspend fun recentProgress(sourceId: String, kind: String, limit: Int): List<JoinedWatchRow>
    @Query("DELETE FROM entry_search WHERE rowid IN (SELECT id FROM library_entries WHERE sourceId = :sourceId)") suspend fun clearSearch(sourceId: String)
    @Query("DELETE FROM entry_search") suspend fun clearSearch()
    @Query("SELECT * FROM item_identities WHERE sourceId = :sourceId AND identityKey IN (:identityKeys)") suspend fun identities(sourceId: String, identityKeys: List<String>): List<IdentityRow>
    @Query("SELECT COALESCE(MAX(id), -1) FROM item_identities") suspend fun maxId(): Int
    @Insert suspend fun insertIdentities(rows: List<IdentityRow>)
    @Insert suspend fun insertEntries(rows: List<EntryRow>)
    @Upsert suspend fun source(row: SourceRow)
    @Upsert suspend fun active(row: ActiveRow)
    @Query("DELETE FROM library_entries WHERE sourceId = :sourceId") suspend fun clearEntries(sourceId: String)
    @Query("DELETE FROM item_identities WHERE sourceId = :sourceId") suspend fun clearIdentities(sourceId: String)
    @Query("DELETE FROM source_state WHERE sourceId = :sourceId") suspend fun clearSource(sourceId: String)
    @Query("DELETE FROM library_entries") suspend fun clearEntries()
    @Query("DELETE FROM item_identities") suspend fun clearIdentities()
    @Query("DELETE FROM source_state") suspend fun clearSource()
    @Query("DELETE FROM active_library") suspend fun clearActive()
}
@Database(entities = [SourceRow::class, IdentityRow::class, EntryRow::class, ActiveRow::class, SearchRow::class, FavouriteRow::class, WatchProgressRow::class], version = 4, exportSchema = true)
internal abstract class LibraryDatabase : RoomDatabase() { abstract fun library(): LibraryDao }
