package com.fireamun.iptv.core.data

class SavedLibrary(val source: SourceSpec, val entries: List<PlaylistEntry>, val savedAtMillis: Long) {
    override fun toString() = "[protected saved library]"
}
interface LibraryPersistence {
    suspend fun restore(): SavedLibrary?
    suspend fun save(source: SourceSpec, entries: List<PlaylistEntry>, refreshing: Boolean = false): SavedLibrary
    suspend fun clear()
}
