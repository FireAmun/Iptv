package com.fireamun.iptv.core.data

import com.fireamun.iptv.core.model.SourceDirectory

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
}
