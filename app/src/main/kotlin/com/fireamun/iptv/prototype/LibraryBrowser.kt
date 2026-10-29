package com.fireamun.iptv.prototype

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.fireamun.iptv.core.designsystem.*
import com.fireamun.iptv.core.model.*
import com.fireamun.iptv.ui.mobile.CinemaTheme
import kotlinx.coroutines.flow.first

internal fun LibraryTab.tag() = "nav_" + when (this) {
    LibraryTab.LIVE -> "live"; LibraryTab.MOVIES -> "movies"; LibraryTab.SERIES -> "series"
    LibraryTab.SETTINGS -> "settings"; LibraryTab.FAVOURITES -> "favourites"; LibraryTab.SEARCH -> "search"
}
internal val LoadedCatalogueItems = SemanticsPropertyKey<Int>("LoadedCatalogueItems")
internal val ArtworkLoaded = SemanticsPropertyKey<Boolean>("ArtworkLoaded")
private val LocalBrowserTv = staticCompositionLocalOf { false }
// Foundation clickable uses touch-mode-dependent focus. TV targets must always accept remote focus.
@Composable private fun Modifier.tvFocus(): Modifier = if (LocalBrowserTv.current) focusProperties { canFocus = true } else this
private fun LibraryTab.label() = when (this) {
    LibraryTab.LIVE -> R.string.nav_live; LibraryTab.MOVIES -> R.string.nav_movies; LibraryTab.SERIES -> R.string.nav_series
    LibraryTab.SETTINGS -> R.string.nav_settings; LibraryTab.FAVOURITES -> R.string.nav_favourites; LibraryTab.SEARCH -> R.string.nav_search
}

@Composable
internal fun LibraryBrowser(state: LibraryState, items: LazyPagingItems<Channel>, television: Boolean, imageLoader: ImageLoader,
    onTab: (LibraryTab) -> Unit, onQuery: (String) -> Unit, onCategory: (String?) -> Unit, onPlay: (Int) -> Unit,
    onFavourite: (Channel) -> Unit, onBack: () -> Unit, onCloseSearch: () -> Unit, settings: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBrowserTv provides television) {
      CinemaTheme {
        val p = cinemaPalette()
        val pages = rememberSaveableStateHolder()
        val navigationFocus = remember { LibraryTab.entries.associateWith { FocusRequester() } }
        val wideTv = television && LocalConfiguration.current.screenWidthDp >= 600
        var navigationFocused by remember { mutableStateOf(false) }
        var topBarFocused by remember { mutableStateOf(false) }
        BackHandler(enabled = television && !state.canGoBack && state.tab != LibraryTab.SETTINGS && !navigationFocused && !topBarFocused) { navigationFocus.getValue(state.tab).requestFocus() }
        Surface(Modifier.fillMaxSize(), color = p.background, contentColor = p.text) {
         Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.safeDrawingPadding().consumeWindowInsets(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().onFocusChanged { topBarFocused = it.hasFocus }.padding(horizontal = if (television) 36.dp else 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.tab == LibraryTab.SEARCH && !state.canGoBack) {
                    val back = stringResource(R.string.browse_back)
                    BrowserTarget(onCloseSearch, Modifier.size(48.dp).testTag("search_back").semantics { contentDescription = back }, quiet = true) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CinemaIcon(CinemaSymbol.BACK, tint = p.text) }
                    }
                }
                Box(Modifier.weight(1f)) { AmarWordmark(p.text, Modifier.testTag("app_brand"), 20.sp) }
                val searchLabel = stringResource(R.string.nav_search)
                BrowserTarget({ onTab(LibraryTab.SEARCH) }, Modifier.size(48.dp).focusRequester(navigationFocus.getValue(LibraryTab.SEARCH)).testTag("nav_search")
                    .semantics { selected = state.tab == LibraryTab.SEARCH; contentDescription = searchLabel }, selected = state.tab == LibraryTab.SEARCH, quiet = true) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NavIcon(LibraryTab.SEARCH, Modifier.size(24.dp), state.tab == LibraryTab.SEARCH) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.tab == LibraryTab.SETTINGS) settings()
                else pages.SaveableStateProvider("${state.activeSourceId}:${state.catalogueGeneration}:${state.tab}:${state.canGoBack}") {
                    ContentBrowser(state, items, television, imageLoader, onTab, onQuery, onCategory, onPlay, onFavourite, onBack)
                }
            }
            HorizontalDivider(color = p.outline.copy(alpha = .2f))
            Row(Modifier.fillMaxWidth().onFocusChanged { navigationFocused = it.hasFocus }.background(p.panel).padding(horizontal = if (television) 32.dp else 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(if (television) 8.dp else 2.dp)) {
                listOf(LibraryTab.LIVE, LibraryTab.MOVIES, LibraryTab.SERIES, LibraryTab.FAVOURITES, LibraryTab.SETTINGS).forEach { tab ->
                    val selected = state.tab == tab
                    val label = stringResource(tab.label())
                    BrowserTarget(onClick = { onTab(tab) }, modifier = Modifier.weight(1f).focusRequester(navigationFocus.getValue(tab)).testTag(tab.tag()).semantics { this.selected = selected }, quiet = true) {
                        Column(Modifier.fillMaxWidth().heightIn(min = if (television) 52.dp else 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.height(3.dp).width(22.dp).clip(RoundedCornerShape(3.dp)).background(if (selected) p.focus else androidx.compose.ui.graphics.Color.Transparent))
                            if (wideTv) Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NavIcon(tab, Modifier.size(22.dp), selected)
                                Text(label, color = if (selected) p.focus else p.muted, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            } else Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                NavIcon(tab, Modifier.size(22.dp), selected)
                                Text(label, color = if (selected) p.focus else p.muted, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 2,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }
         }
        }
      }
    }
}

@Composable
private fun ContentBrowser(state: LibraryState, items: LazyPagingItems<Channel>, television: Boolean, imageLoader: ImageLoader,
    onTab: (LibraryTab) -> Unit, onQuery: (String) -> Unit, onCategory: (String?) -> Unit, onPlay: (Int) -> Unit, onFavourite: (Channel) -> Unit, onBack: () -> Unit) {
    val p = cinemaPalette()
    var detail by remember { mutableStateOf<Channel?>(null) }
    var lastFocusedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchHasFocus by remember { mutableStateOf(false) }
    var choosingCategory by remember { mutableStateOf(false) }
    var restoreCategoryFocus by remember { mutableStateOf(false) }
    var categoryRowHasFocus by remember { mutableStateOf(false) }
    val padding = if (television) 36.dp else 20.dp
    val grid = rememberLazyGridState()
    val list = rememberLazyListState()
    val entryFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val categoryFocus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val posters = !state.canGoBack && state.tab in setOf(LibraryTab.MOVIES, LibraryTab.SERIES, LibraryTab.FAVOURITES)
    val visibleStart = if (posters) grid.firstVisibleItemIndex else list.firstVisibleItemIndex
    val selectedIndex = items.itemSnapshotList.items.indexOfFirst { it.id == (lastFocusedId ?: state.selectedId) }
    val targetIndex = if (selectedIndex >= 0) selectedIndex else visibleStart.coerceAtMost((items.itemCount - 1).coerceAtLeast(0))
    LaunchedEffect(state.tab, state.activeSourceId, state.canGoBack, window.isWindowFocused, items.loadState.refresh, items.itemSnapshotList.items.firstOrNull()?.id, choosingCategory) {
        if (television && window.isWindowFocused && !choosingCategory && !categoryRowHasFocus) {
            if (restoreCategoryFocus && state.categories.isNotEmpty() && !state.canGoBack) {
                withFrameNanos { }
                if (!categoryRowHasFocus) categoryFocus.requestFocus()
            }
            else if (state.tab == LibraryTab.SEARCH && !state.canGoBack && (lastFocusedId == null || searchHasFocus)) {
                withFrameNanos { }
                if (!categoryRowHasFocus) searchFocus.requestFocus()
            }
            else if (state.entryCount > 0) {
                snapshotFlow { items.itemCount > 0 || items.loadState.refresh is LoadState.Error }.first { it }
                if (categoryRowHasFocus) return@LaunchedEffect
                if (items.itemCount > 0) {
                    val index = items.itemSnapshotList.items.indexOfFirst { it.id == (lastFocusedId ?: state.selectedId) }.takeIf { it >= 0 }
                        ?: (if (posters) grid.firstVisibleItemIndex else list.firstVisibleItemIndex).coerceAtMost(items.itemCount - 1)
                    if (posters) grid.scrollToItem(index) else list.scrollToItem(index)
                }
                withFrameNanos { }
                if (items.itemCount > 0 && !categoryRowHasFocus) entryFocus.requestFocus()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = padding, end = padding, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.canGoBack) TextButton(onClick = onBack, modifier = Modifier.tvFocus().testTag("library_back")) { Text(stringResource(R.string.browse_back), color = p.focus) }
            Text(state.collectionTitle ?: stringResource(state.tab.label()), fontSize = if (television) 32.sp else 28.sp, lineHeight = if (television) 40.sp else 36.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!state.canGoBack) Text(stringResource(when (state.tab) {
                LibraryTab.LIVE -> R.string.browse_live_caption; LibraryTab.MOVIES -> R.string.browse_movies_caption
                LibraryTab.SERIES -> R.string.browse_series_caption; LibraryTab.FAVOURITES -> R.string.browse_favourites_caption; else -> R.string.browse_search_caption
            }), color = p.muted, fontSize = 14.sp, lineHeight = 21.sp)
            if (state.tab == LibraryTab.SEARCH && !state.canGoBack) OutlinedTextField(state.query, onQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(searchFocus).onFocusChanged { searchHasFocus = it.isFocused; if (it.isFocused) restoreCategoryFocus = false }.testTag("catalogue_search"),
                placeholder = { Text(stringResource(R.string.browse_search_hint)) }, leadingIcon = { CinemaIcon(CinemaSymbol.SEARCH, tint = p.muted) }, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = p.panel, unfocusedContainerColor = p.panel, focusedBorderColor = p.focus, unfocusedBorderColor = p.outline.copy(alpha = .45f), cursorColor = p.focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error != null) Text(stringResource(R.string.browse_failed), color = p.error, modifier = Modifier.testTag("import_error"))
        }
        // Paging/window updates may finish after the user has moved to a chip.
        // Preserve that deliberate focus instead of replaying an earlier restoration.
        if (state.categories.isNotEmpty() && !state.canGoBack) Row(Modifier.fillMaxWidth().onFocusChanged { categoryRowHasFocus = it.hasFocus }.padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f).testTag("category_list"), contentPadding = PaddingValues(start = padding, end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = state.category == null, onClick = { restoreCategoryFocus = false; onCategory(null) }, label = { Text(stringResource(R.string.browse_all_categories)) }, modifier = Modifier.tvFocus().testTag("category_all")) }
                items(state.categories, key = { it }) { category -> FilterChip(selected = state.category == category, onClick = { restoreCategoryFocus = false; onCategory(category) }, modifier = Modifier.tvFocus(), label = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 220.dp)) }) }
            }
            val categoryLabel = stringResource(R.string.browse_categories)
            BrowserTarget({ keyboard?.hide(); restoreCategoryFocus = false; choosingCategory = true }, Modifier.padding(end = padding).size(48.dp).focusRequester(categoryFocus).testTag("browse_categories").semantics { contentDescription = categoryLabel }) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CinemaIcon(CinemaSymbol.CATEGORIES, tint = p.focus) }
            }
        }
        if (items.itemCount == 0) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(Modifier.padding(padding).widthIn(max = 440.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (items.loadState.refresh is LoadState.Loading) { CircularProgressIndicator(); Text(stringResource(R.string.browse_loading)) }
                    else {
                        NavIcon(if (state.tab == LibraryTab.FAVOURITES) LibraryTab.FAVOURITES else state.tab, Modifier.size(52.dp), true)
                        Text(stringResource(if (state.entryCount == 0) R.string.browse_no_source else if (state.tab == LibraryTab.FAVOURITES) R.string.browse_no_favourites else R.string.browse_empty), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(if (state.entryCount == 0) R.string.browse_no_source_body else if (state.tab == LibraryTab.FAVOURITES) R.string.browse_no_favourites_body else R.string.browse_empty_body), color = p.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        if (items.loadState.refresh is LoadState.Error) Button(onClick = { items.retry() }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_retry)) }
                        if (state.entryCount == 0) Button(onClick = { onTab(LibraryTab.SETTINGS) }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.nav_settings)) }
                    }
                }
            }
        } else if (posters) {
            LazyVerticalGrid(columns = GridCells.Adaptive(if (television) 128.dp else 145.dp), state = grid, modifier = Modifier.weight(1f).testTag("catalogue_grid").semantics { this[LoadedCatalogueItems] = items.itemCount },
                contentPadding = PaddingValues(start = padding, end = padding, bottom = 20.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(items.itemCount, key = items.itemKey { it.id }) { index -> items[index]?.let { channel ->
                    PosterCard(channel, state, imageLoader, Modifier.then(if (index == targetIndex) Modifier.focusRequester(entryFocus) else Modifier),
                        onClick = { keyboard?.hide(); lastFocusedId = channel.id; if (channel.kind in setOf(ContentKind.MOVIE, ContentKind.SERIES)) detail = channel else onPlay(channel.id) }, onFavourite = { restoreCategoryFocus = false; onFavourite(channel) }, onFocused = { restoreCategoryFocus = false; lastFocusedId = channel.id })
                } }
                if (items.loadState.append is LoadState.Error) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = { items.retry() }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_retry)) } }
            }
        } else {
            LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("catalogue_list"), contentPadding = PaddingValues(start = padding, end = padding, bottom = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.itemCount, key = items.itemKey { it.id }) { index -> items[index]?.let { channel ->
                    BrowserTarget({ keyboard?.hide(); lastFocusedId = channel.id; if (!state.canGoBack && channel.kind in setOf(ContentKind.MOVIE, ContentKind.SERIES)) detail = channel else onPlay(channel.id) }, Modifier.fillMaxWidth().then(if (index == targetIndex) Modifier.focusRequester(entryFocus) else Modifier).testTag("channel_${channel.id}"), onFocused = { restoreCategoryFocus = false; lastFocusedId = channel.id }, quiet = true) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(channel, state, imageLoader, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), fontSize = if (television) 17.sp else 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                channel.group?.let { Text(it, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                if (state.tab == LibraryTab.SEARCH && !state.canGoBack) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(when (channel.kind) { ContentKind.MOVIE -> R.string.nav_movies; ContentKind.SERIES -> R.string.nav_series; else -> R.string.nav_live }), color = p.focus, style = MaterialTheme.typography.labelSmall)
                                    Metadata(channel)
                                }
                                if (channel.kind == ContentKind.EPISODE && channel.season != null && channel.episode != null) Text(stringResource(R.string.browse_episode, channel.season!!, channel.episode!!), color = p.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!state.canGoBack) FavouriteButton(channel, onClick = { restoreCategoryFocus = false; onFavourite(channel) })
                        }
                    }
                    HorizontalDivider(color = p.outline.copy(alpha = .18f))
                } }
                if (items.loadState.append is LoadState.Error) item { TextButton(onClick = { items.retry() }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_retry)) } }
            }
        }
    }
    if (choosingCategory) CategoryPicker(state.categories, state.category, television,
        onSelect = { category -> choosingCategory = false; restoreCategoryFocus = true; onCategory(category) },
        onDismiss = { choosingCategory = false; restoreCategoryFocus = true })
    detail?.let { channel ->
        val playFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { detail = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val dialogWindow = LocalWindowInfo.current
            BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 620.dp)), shape = RoundedCornerShape(22.dp), color = p.panel, contentColor = p.text) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), Modifier.weight(1f), fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, maxLines = 3)
                            val closeLabel = stringResource(R.string.browse_close)
                            IconButton(onClick = { detail = null }, modifier = Modifier.size(48.dp).tvFocus().semantics { contentDescription = closeLabel }) { CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted) }
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Artwork(channel, state, imageLoader, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
                            Metadata(channel)
                            channel.group?.let { Text(it, color = p.muted) }
                        }
                        Button(onClick = { detail = null; onPlay(channel.id) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).tvFocus().focusRequester(playFocus).testTag("detail_play")) { Text(stringResource(if (channel.kind == ContentKind.SERIES) R.string.browse_episodes else R.string.browse_play), fontWeight = FontWeight.Bold) }
                        OutlinedButton(onClick = { detail = null; restoreCategoryFocus = false; onFavourite(channel) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).tvFocus().testTag("detail_favourite")) {
                            CinemaIcon(if (channel.favourite) CinemaSymbol.FAVOURITE_FILLED else CinemaSymbol.FAVOURITE, Modifier.size(20.dp), p.focus)
                            Spacer(Modifier.width(8.dp)); Text(stringResource(if (channel.favourite) R.string.browse_remove_favourite else R.string.browse_add_favourite), color = p.text)
                        }
                    }
                }
            }
            LaunchedEffect(dialogWindow.isWindowFocused) { if (television && dialogWindow.isWindowFocused) { withFrameNanos { }; playFocus.requestFocus() } }
        }
    }
}

@Composable
private fun BrowserTarget(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, onFocused: () -> Unit = {}, quiet: Boolean = false, content: @Composable () -> Unit) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    Box(modifier.tvFocus().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }.clip(RoundedCornerShape(14.dp))
        .background(if (focused) p.raised else if (selected) p.focus.copy(alpha = .10f) else if (quiet) androidx.compose.ui.graphics.Color.Transparent else p.panel)
        .border(if (focused) 2.dp else if (quiet) 0.dp else 1.dp, if (focused) p.focus else if (quiet) androidx.compose.ui.graphics.Color.Transparent else p.outline.copy(alpha = .2f), RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, onClick = onClick)) { content() }
}
@Composable
private fun PosterCard(channel: Channel, state: LibraryState, imageLoader: ImageLoader, modifier: Modifier, onClick: () -> Unit, onFavourite: () -> Unit, onFocused: () -> Unit) {
    val p = cinemaPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
            BrowserTarget(onClick, modifier.fillMaxSize().testTag("channel_${channel.id}"), onFocused = onFocused) {
                Artwork(channel, state, imageLoader, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(74.dp).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, Cinema.Night.copy(alpha = .88f)))))
                Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), Modifier.align(Alignment.BottomStart).padding(12.dp), color = Cinema.Cream, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp).clip(RoundedCornerShape(24.dp)).background(Cinema.Night.copy(alpha = .72f))) { FavouriteButton(channel, onFavourite, onArtwork = true) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Metadata(channel)
            if (state.tab == LibraryTab.FAVOURITES) Text(stringResource(when (channel.kind) { ContentKind.MOVIE -> R.string.nav_movies; ContentKind.SERIES -> R.string.nav_series; else -> R.string.nav_live }), color = p.muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}
@Composable private fun Metadata(channel: Channel) {
    val p = cinemaPalette()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        channel.year?.let { Text(it.toString(), color = p.muted, fontSize = 12.sp) }
        channel.rating?.let { rating -> val value = String.format(java.util.Locale.ROOT, "%.1f", rating); val label = stringResource(R.string.browse_rating, value); Text("★ $value", color = p.focus, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = label }) }
    }
}
@Composable private fun FavouriteButton(channel: Channel, onClick: () -> Unit, onArtwork: Boolean = false) {
    val p = cinemaPalette()
    val label = stringResource(if (channel.favourite) R.string.browse_remove_favourite else R.string.browse_add_favourite)
    TextButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).tvFocus().testTag("favourite_${channel.id}").semantics { contentDescription = label }, contentPadding = PaddingValues(8.dp)) {
        CinemaIcon(if (channel.favourite) CinemaSymbol.FAVOURITE_FILLED else CinemaSymbol.FAVOURITE, tint = if (channel.favourite) { if (onArtwork) Cinema.Saffron else p.focus } else if (onArtwork) Cinema.Cream else p.muted)
    }
}
@Composable private fun Artwork(channel: Channel, state: LibraryState, imageLoader: ImageLoader, modifier: Modifier) {
    val p = cinemaPalette()
    var loaded by remember(channel.id, state.activeSourceId, state.catalogueGeneration) { mutableStateOf(false) }
    Box(modifier.testTag("artwork_${channel.id}").semantics { this[ArtworkLoaded] = loaded }.background(Brush.linearGradient(listOf(p.raised, p.panel, p.raised))), contentAlignment = Alignment.Center) {
        NavIcon(when (channel.kind) { ContentKind.MOVIE -> LibraryTab.MOVIES; ContentKind.SERIES -> LibraryTab.SERIES; else -> LibraryTab.LIVE }, Modifier.size(32.dp), true)
        if (channel.hasArtwork && state.activeSourceId != null && state.catalogueGeneration != null) AsyncImage(
            model = LibraryArtwork(state.activeSourceId!!, channel.id, state.catalogueGeneration!!), imageLoader = imageLoader, contentDescription = null,
            modifier = Modifier.fillMaxSize(), onSuccess = { loaded = true }, contentScale = if (channel.kind in setOf(ContentKind.LIVE, ContentKind.UNKNOWN)) ContentScale.Fit else ContentScale.Crop)
    }
}
@Composable private fun NavIcon(tab: LibraryTab, modifier: Modifier, selected: Boolean) {
    val p = cinemaPalette(); val color = if (selected) p.focus else p.muted
    CinemaIcon(when (tab) { LibraryTab.LIVE -> CinemaSymbol.LIVE; LibraryTab.MOVIES -> CinemaSymbol.MOVIES; LibraryTab.SERIES -> CinemaSymbol.SERIES; LibraryTab.FAVOURITES -> CinemaSymbol.FAVOURITE; LibraryTab.SETTINGS -> CinemaSymbol.SETTINGS; LibraryTab.SEARCH -> CinemaSymbol.SEARCH }, modifier, color)
}
