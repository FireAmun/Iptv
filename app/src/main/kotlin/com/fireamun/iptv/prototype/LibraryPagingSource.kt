package com.fireamun.iptv.prototype

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fireamun.iptv.core.data.ImportFailure
import com.fireamun.iptv.core.data.LibraryPersistence
import com.fireamun.iptv.core.model.*
import kotlinx.coroutines.CancellationException

internal class LibraryPagingSource(
    private val persistence: LibraryPersistence?,
    private val request: BrowseRequest?,
    private val local: List<Channel>?,
) : PagingSource<Int, Channel>() {
    override fun getRefreshKey(state: PagingState<Int, Channel>): Int? = state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { it.prevKey?.plus(60) ?: it.nextKey?.minus(60) }?.coerceAtLeast(0)
    }
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Channel> = try {
        val offset = params.key ?: 0
        val limit = params.loadSize.coerceAtMost(120)
        val page = if (local != null) {
            val terms = searchTerms(request?.query.orEmpty())
            val items = local.filter { channel ->
                matchesKind(channel.kind, request?.kind) && (request?.category == null || channel.group == request.category) &&
                    request?.hidden?.blocks(channel.kind, channel.group) != true &&
                    (request?.favouritesOnly != true || channel.favourite) && terms.all { term ->
                        searchTerms("${channel.title.orEmpty()} ${channel.group.orEmpty()}").any { if (term.all(Char::isDigit)) it == term else it.startsWith(term) }
                    }
            }
            BrowsePage(items.drop(offset).take(limit), items.size)
        } else if (request != null && persistence != null) persistence.page(request, offset, limit) else BrowsePage(emptyList(), 0)
        LoadResult.Page(page.items, if (offset == 0) null else (offset - limit).coerceAtLeast(0),
            if (offset + page.items.size < page.total && page.items.isNotEmpty()) offset + page.items.size else null,
            itemsBefore = offset.coerceAtMost(page.total), itemsAfter = (page.total - offset - page.items.size).coerceAtLeast(0))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (_: ImportFailure) { LoadResult.Error(ImportFailure(ImportError.STORAGE)) }
}
