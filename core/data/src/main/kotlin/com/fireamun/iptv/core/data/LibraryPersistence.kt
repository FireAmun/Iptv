package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.*

class LibrarySession(val source: SourceSpec, val sourceId: String?, val generation: String, val savedAtMillis: Long, val count: Int, val initialEntries: List<PlaylistEntry> = emptyList()) {
    override fun toString() = "[protected library session]"
}
private fun SavedLibrary.session() = LibrarySession(source, sourceId, savedAtMillis.toString(), savedAtMillis, entries.size, entries)

class SavedLibrary(val source: SourceSpec, val entries: List<PlaylistEntry>, val savedAtMillis: Long, val sourceId: String? = null) {
    override fun toString() = "[protected saved library]"
}
interface LibraryPersistence {
    suspend fun restore(): SavedLibrary?
    suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean = false, targetSourceId: String? = null): SavedLibrary
    suspend fun clear()
    suspend fun directory(): SourceDirectory
    suspend fun activate(sourceId: String): SavedLibrary
    suspend fun rename(sourceId: String, name: String)
    suspend fun remove(sourceId: String)
    suspend fun openSession(): LibrarySession? = restore()?.session()
    suspend fun activateSession(sourceId: String): LibrarySession = activate(sourceId).session()
    suspend fun page(request: BrowseRequest, offset: Int, limit: Int): BrowsePage = throw ImportFailure(ImportError.STORAGE)
    suspend fun categories(request: BrowseRequest): List<String> = emptyList()
    suspend fun item(sourceId: String, generation: String, itemId: Int): PlaylistEntry? = throw ImportFailure(ImportError.STORAGE)
    suspend fun artwork(reference: LibraryArtwork): MediaAddress? = null
    suspend fun setFavourite(sourceId: String, generation: String, itemId: Int, favourite: Boolean): Unit = throw ImportFailure(ImportError.STORAGE)
}
