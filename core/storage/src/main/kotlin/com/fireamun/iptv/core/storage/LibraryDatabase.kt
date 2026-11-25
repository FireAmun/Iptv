package com.fireamun.iptv.core.storage

import androidx.room.*
import androidx.sqlite.db.SupportSQLiteQuery

@Entity(tableName = "source_state", indices = [Index(value = ["sourceId"], unique = true)])
internal data class SourceRow(@PrimaryKey val slot: Int = 1, val sourceId: String, val generation: String, val secret: ByteArray, val savedAt: Long, val kind: String? = null, val label: ByteArray? = null) {
    override fun toString() = "[protected source row]"
}
@Entity(tableName = "item_identities", indices = [Index(value = ["sourceId", "identityKey"], unique = true)])
internal data class IdentityRow(@PrimaryKey val id: Int, val sourceId: String, val identityKey: String)
@Entity(tableName = "library_entries", indices = [Index(value = ["sourceId", "position"]), Index(value = ["sourceId", "kind", "position"]), Index(value = ["sourceId", "groupLabel", "position"]), Index(value = ["sourceId", "guideKey"])])
internal data class EntryRow(@PrimaryKey val id: Int, val position: Int, val title: String?, val groupLabel: String?, val kind: String, val seriesId: Long?, val providerKey: String?, val address: ByteArray?, @ColumnInfo(defaultValue = "''") val sourceId: String,
    val categoryKey: String? = null, val year: Int? = null, val rating: Double? = null, val artwork: ByteArray? = null, val guideKey: String? = null) {
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
/** Guide links are sealed with the source protector; [error] is an ImportError name. */
@Entity(tableName = "guide_state")
internal data class GuideStateRow(@PrimaryKey val sourceId: String, val manualLink: ByteArray? = null, val playlistLink: ByteArray? = null, val fetchedAtMs: Long? = null,
    val attemptedAtMs: Long? = null, val programmeCount: Int = 0, val error: String? = null) {
    override fun toString() = "[protected guide state]"
}
@Entity(tableName = "guide_channels", primaryKeys = ["sourceId", "alias"])
internal data class GuideChannelRow(val sourceId: String, val alias: String, val channelId: String)
@Entity(tableName = "guide_programmes", primaryKeys = ["sourceId", "channelId", "startMs"])
internal data class GuideProgrammeRow(val sourceId: String, val channelId: String, val startMs: Long, val endMs: Long, val title: String, val description: String?)
internal data class NowNextRow(val itemId: Int, val startMs: Long, val endMs: Long, val title: String)
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
    @Query("SELECT * FROM guide_state WHERE sourceId = :sourceId") suspend fun guideState(sourceId: String): GuideStateRow?
    @Upsert suspend fun guideState(row: GuideStateRow)
    @Query("SELECT DISTINCT guideKey FROM library_entries WHERE sourceId = :sourceId AND guideKey IS NOT NULL") suspend fun guideKeys(sourceId: String): List<String>
    @Query("SELECT guideKey FROM library_entries WHERE sourceId = :sourceId AND id = :id") suspend fun guideKey(sourceId: String, id: Int): String?
    @Query("SELECT channelId FROM guide_channels WHERE sourceId = :sourceId AND alias = :alias") suspend fun guideChannel(sourceId: String, alias: String): String?
    @Query("""SELECT * FROM guide_programmes WHERE sourceId = :sourceId AND channelId = :channelId AND endMs > :fromMs AND startMs < :untilMs
        ORDER BY startMs LIMIT :limit""") suspend fun programmes(sourceId: String, channelId: String, fromMs: Long, untilMs: Long, limit: Int): List<GuideProgrammeRow>
    @Query("""SELECT e.id AS itemId, p.startMs, p.endMs, p.title FROM library_entries e
        JOIN guide_channels c ON c.sourceId = e.sourceId AND c.alias = e.guideKey
        JOIN guide_programmes p ON p.sourceId = c.sourceId AND p.channelId = c.channelId
        WHERE e.sourceId = :sourceId AND e.id IN (:ids) AND p.endMs > :nowMs AND p.startMs < :untilMs
        ORDER BY e.id, p.startMs""") suspend fun nowNext(sourceId: String, ids: List<Int>, nowMs: Long, untilMs: Long): List<NowNextRow>
    @Insert suspend fun insertGuideChannels(rows: List<GuideChannelRow>)
    @Insert suspend fun insertProgrammes(rows: List<GuideProgrammeRow>)
    @Query("DELETE FROM guide_channels WHERE sourceId = :sourceId") suspend fun clearGuideChannels(sourceId: String)
    @Query("DELETE FROM guide_programmes WHERE sourceId = :sourceId") suspend fun clearProgrammes(sourceId: String)
    @Query("DELETE FROM guide_state WHERE sourceId = :sourceId") suspend fun clearGuideState(sourceId: String)
    @Query("DELETE FROM guide_channels") suspend fun clearGuideChannels()
    @Query("DELETE FROM guide_programmes") suspend fun clearProgrammes()
    @Query("DELETE FROM guide_state") suspend fun clearGuideState()
}
@Database(entities = [SourceRow::class, IdentityRow::class, EntryRow::class, ActiveRow::class, SearchRow::class, FavouriteRow::class, WatchProgressRow::class,
    GuideStateRow::class, GuideChannelRow::class, GuideProgrammeRow::class], version = 5, exportSchema = true)
internal abstract class LibraryDatabase : RoomDatabase() { abstract fun library(): LibraryDao }
