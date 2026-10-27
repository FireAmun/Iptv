package com.fireamun.iptv.prototype

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Dialog
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
        var navigationFocused by remember { mutableStateOf(false) }
        var topBarFocused by remember { mutableStateOf(false) }
        BackHandler(enabled = television && !state.canGoBack && state.tab != LibraryTab.SETTINGS && !navigationFocused && !topBarFocused) { navigationFocus.getValue(state.tab).requestFocus() }
        Surface(Modifier.fillMaxSize(), color = p.background, contentColor = p.text) {
         Column(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.safeDrawingPadding().consumeWindowInsets(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().onFocusChanged { topBarFocused = it.hasFocus }.padding(horizontal = if (television) 36.dp else 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.tab == LibraryTab.SEARCH && !state.canGoBack) {
                    val back = stringResource(R.string.browse_back)
                    val backGlyph = if (LocalLayoutDirection.current == LayoutDirection.Rtl) "›" else "‹"
                    BrowserTarget(onCloseSearch, Modifier.size(48.dp).testTag("search_back").semantics { contentDescription = back }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(backGlyph, fontSize = 28.sp, color = p.text) }
                    }
                }
                Box(Modifier.weight(1f)) { AmarWordmark(p.text, Modifier.testTag("app_brand"), 20.sp) }
                val searchLabel = stringResource(R.string.nav_search)
                BrowserTarget({ onTab(LibraryTab.SEARCH) }, Modifier.size(48.dp).focusRequester(navigationFocus.getValue(LibraryTab.SEARCH)).testTag("nav_search")
                    .semantics { selected = state.tab == LibraryTab.SEARCH; contentDescription = searchLabel }, selected = state.tab == LibraryTab.SEARCH) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { NavIcon(LibraryTab.SEARCH, Modifier.size(24.dp), state.tab == LibraryTab.SEARCH) }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.tab == LibraryTab.SETTINGS) settings()
                else pages.SaveableStateProvider("${state.activeSourceId}:${state.catalogueGeneration}:${state.tab}:${state.canGoBack}") {
                    ContentBrowser(state, items, television, imageLoader, onTab, onQuery, onCategory, onPlay, onFavourite, onBack)
                }
            }
            Row(Modifier.fillMaxWidth().onFocusChanged { navigationFocused = it.hasFocus }.background(p.panel).padding(horizontal = if (television) 32.dp else 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (television) 12.dp else 4.dp)) {
                LibraryTab.entries.filter { it != LibraryTab.SEARCH }.forEach { tab ->
                    val selected = state.tab == tab
                    val label = stringResource(tab.label())
                    BrowserTarget(onClick = { onTab(tab) }, modifier = Modifier.weight(1f).focusRequester(navigationFocus.getValue(tab)).testTag(tab.tag()).semantics { this.selected = selected }, selected = selected) {
                        Column(Modifier.fillMaxWidth().heightIn(min = if (television) 58.dp else 64.dp).padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                            NavIcon(tab, Modifier.size(22.dp), selected)
                            Text(label, color = if (selected) p.focus else p.muted, fontSize = if (television) 14.sp else 12.sp, lineHeight = 16.sp, maxLines = 2,
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

@Composable
private fun ContentBrowser(state: LibraryState, items: LazyPagingItems<Channel>, television: Boolean, imageLoader: ImageLoader,
    onTab: (LibraryTab) -> Unit, onQuery: (String) -> Unit, onCategory: (String?) -> Unit, onPlay: (Int) -> Unit, onFavourite: (Channel) -> Unit, onBack: () -> Unit) {
    val p = cinemaPalette()
    var detail by remember { mutableStateOf<Channel?>(null) }
    var lastFocusedId by rememberSaveable { mutableStateOf<Int?>(null) }
    var searchHasFocus by remember { mutableStateOf(false) }
    val padding = if (television) 36.dp else 20.dp
    val grid = rememberLazyGridState()
    val list = rememberLazyListState()
    val entryFocus = remember { FocusRequester() }
    val searchFocus = remember { FocusRequester() }
    val window = LocalWindowInfo.current
    val keyboard = LocalSoftwareKeyboardController.current
    val posters = !state.canGoBack && state.tab in setOf(LibraryTab.MOVIES, LibraryTab.SERIES, LibraryTab.FAVOURITES)
    val visibleStart = if (posters) grid.firstVisibleItemIndex else list.firstVisibleItemIndex
    val selectedIndex = items.itemSnapshotList.items.indexOfFirst { it.id == (lastFocusedId ?: state.selectedId) }
    val targetIndex = if (selectedIndex >= 0) selectedIndex else visibleStart.coerceAtMost((items.itemCount - 1).coerceAtLeast(0))
    LaunchedEffect(state.tab, state.activeSourceId, state.canGoBack, window.isWindowFocused, items.loadState.refresh, items.itemSnapshotList.items.firstOrNull()?.id) {
        if (television && window.isWindowFocused) {
            if (state.tab == LibraryTab.SEARCH && !state.canGoBack && (lastFocusedId == null || searchHasFocus)) { withFrameNanos { }; searchFocus.requestFocus() }
            else if (state.entryCount > 0) {
                snapshotFlow { items.itemCount > 0 || items.loadState.refresh is LoadState.Error }.first { it }
                if (items.itemCount > 0) {
                    val index = items.itemSnapshotList.items.indexOfFirst { it.id == (lastFocusedId ?: state.selectedId) }.takeIf { it >= 0 }
                        ?: (if (posters) grid.firstVisibleItemIndex else list.firstVisibleItemIndex).coerceAtMost(items.itemCount - 1)
                    if (posters) grid.scrollToItem(index) else list.scrollToItem(index)
                }
                withFrameNanos { }
                if (items.itemCount > 0) entryFocus.requestFocus()
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(start = padding, end = padding, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.canGoBack) TextButton(onClick = onBack, modifier = Modifier.tvFocus().testTag("library_back")) { Text(stringResource(R.string.browse_back), color = p.focus) }
            Text(state.collectionTitle ?: stringResource(state.tab.label()), fontSize = if (television) 34.sp else 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!state.canGoBack) Text(stringResource(when (state.tab) {
                LibraryTab.LIVE -> R.string.browse_live_caption; LibraryTab.MOVIES -> R.string.browse_movies_caption
                LibraryTab.SERIES -> R.string.browse_series_caption; LibraryTab.FAVOURITES -> R.string.browse_favourites_caption; else -> R.string.browse_search_caption
            }), color = p.muted, style = MaterialTheme.typography.bodyMedium)
            if (state.tab == LibraryTab.SEARCH && !state.canGoBack) OutlinedTextField(state.query, onQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(searchFocus).onFocusChanged { searchHasFocus = it.isFocused }.testTag("catalogue_search"), label = { Text(stringResource(R.string.browse_search_hint)) }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
            if (state.importing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.error != null) Text(stringResource(R.string.browse_failed), color = p.error, modifier = Modifier.testTag("import_error"))
        }
        if (state.categories.isNotEmpty() && !state.canGoBack) LazyRow(Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("category_list"), contentPadding = PaddingValues(horizontal = padding), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(selected = state.category == null, onClick = { onCategory(null) }, label = { Text(stringResource(R.string.browse_all_categories)) }, modifier = Modifier.tvFocus().testTag("category_all")) }
            items(state.categories, key = { it }) { category -> FilterChip(selected = state.category == category, onClick = { onCategory(category) }, modifier = Modifier.tvFocus(), label = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 240.dp)) }) }
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
                        onClick = { keyboard?.hide(); lastFocusedId = channel.id; if (channel.kind in setOf(ContentKind.MOVIE, ContentKind.SERIES)) detail = channel else onPlay(channel.id) }, onFavourite = { onFavourite(channel) }, onFocused = { lastFocusedId = channel.id })
                } }
                if (items.loadState.append is LoadState.Error) item(span = { GridItemSpan(maxLineSpan) }) { TextButton(onClick = { items.retry() }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_retry)) } }
            }
        } else {
            LazyColumn(state = list, modifier = Modifier.weight(1f).testTag("catalogue_list"), contentPadding = PaddingValues(start = padding, end = padding, bottom = 20.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items.itemCount, key = items.itemKey { it.id }) { index -> items[index]?.let { channel ->
                    BrowserTarget({ keyboard?.hide(); lastFocusedId = channel.id; if (!state.canGoBack && channel.kind in setOf(ContentKind.MOVIE, ContentKind.SERIES)) detail = channel else onPlay(channel.id) }, Modifier.fillMaxWidth().then(if (index == targetIndex) Modifier.focusRequester(entryFocus) else Modifier).testTag("channel_${channel.id}"), onFocused = { lastFocusedId = channel.id }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Artwork(channel, state, imageLoader, Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                channel.group?.let { Text(it, color = p.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                if (state.tab == LibraryTab.SEARCH && !state.canGoBack) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(stringResource(when (channel.kind) { ContentKind.MOVIE -> R.string.nav_movies; ContentKind.SERIES -> R.string.nav_series; else -> R.string.nav_live }), color = p.focus, style = MaterialTheme.typography.labelSmall)
                                    Metadata(channel)
                                }
                                if (channel.kind == ContentKind.EPISODE && channel.season != null && channel.episode != null) Text(stringResource(R.string.browse_episode, channel.season!!, channel.episode!!), color = p.muted, style = MaterialTheme.typography.bodySmall)
                            }
                            if (!state.canGoBack) FavouriteButton(channel) { onFavourite(channel) }
                        }
                    }
                } }
                if (items.loadState.append is LoadState.Error) item { TextButton(onClick = { items.retry() }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_retry)) } }
            }
        }
    }
    detail?.let { channel ->
        val playFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { detail = null }) {
            val dialogWindow = LocalWindowInfo.current
            Surface(shape = RoundedCornerShape(22.dp), color = p.panel) {
                Column(Modifier.widthIn(max = 500.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3)
                    Metadata(channel)
                    channel.group?.let { Text(it, color = p.muted) }
                    Button(onClick = { detail = null; onPlay(channel.id) }, modifier = Modifier.fillMaxWidth().tvFocus().focusRequester(playFocus).testTag("detail_play")) { Text(stringResource(if (channel.kind == ContentKind.SERIES) R.string.browse_episodes else R.string.browse_play)) }
                    OutlinedButton(onClick = { detail = null; onFavourite(channel) }, modifier = Modifier.fillMaxWidth().tvFocus().testTag("detail_favourite")) { Text(stringResource(if (channel.favourite) R.string.browse_remove_favourite else R.string.browse_add_favourite)) }
                    TextButton(onClick = { detail = null }, modifier = Modifier.tvFocus()) { Text(stringResource(R.string.browse_close), color = p.text) }
                }
            }
            LaunchedEffect(dialogWindow.isWindowFocused) { if (television && dialogWindow.isWindowFocused) { withFrameNanos { }; playFocus.requestFocus() } }
        }
    }
}

@Composable
private fun BrowserTarget(onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, onFocused: () -> Unit = {}, content: @Composable () -> Unit) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    Box(modifier.tvFocus().onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }.clip(RoundedCornerShape(14.dp))
        .background(if (selected || focused) p.raised else p.panel)
        .border(if (focused) 2.dp else 1.dp, if (focused || selected) p.focus else p.outline.copy(alpha = .2f), RoundedCornerShape(14.dp))
        .clickable(role = Role.Button, onClick = onClick)) { content() }
}
@Composable
private fun PosterCard(channel: Channel, state: LibraryState, imageLoader: ImageLoader, modifier: Modifier, onClick: () -> Unit, onFavourite: () -> Unit, onFocused: () -> Unit) {
    val p = cinemaPalette()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BrowserTarget(onClick, modifier.fillMaxWidth().testTag("channel_${channel.id}"), onFocused = onFocused) {
            Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                Artwork(channel, state, imageLoader, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(74.dp).background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, Cinema.Night.copy(alpha = .88f)))))
                Text(channel.title ?: stringResource(R.string.browse_entry, channel.id + 1), Modifier.align(Alignment.BottomStart).padding(12.dp), color = Cinema.Cream, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { Metadata(channel) }; FavouriteButton(channel, onFavourite) }
    }
}
@Composable private fun Metadata(channel: Channel) {
    val p = cinemaPalette()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        channel.year?.let { Text(it.toString(), color = p.muted, fontSize = 12.sp) }
        channel.rating?.let { rating -> val value = String.format(java.util.Locale.ROOT, "%.1f", rating); val label = stringResource(R.string.browse_rating, value); Text("★ $value", color = p.focus, fontSize = 12.sp, modifier = Modifier.clearAndSetSemantics { contentDescription = label }) }
    }
}
@Composable private fun FavouriteButton(channel: Channel, onClick: () -> Unit) {
    val p = cinemaPalette()
    val label = stringResource(if (channel.favourite) R.string.browse_remove_favourite else R.string.browse_add_favourite)
    TextButton(onClick = onClick, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).tvFocus().testTag("favourite_${channel.id}").semantics { contentDescription = label }, contentPadding = PaddingValues(8.dp)) {
        Text(if (channel.favourite) "♥" else "♡", fontSize = 24.sp, color = if (channel.favourite) p.focus else p.muted)
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
    Canvas(modifier) {
        val u = size.minDimension / 24f
        val stroke = Stroke(1.7f * u)
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(color, Offset(x*u,y*u),Offset(x2*u,y2*u),1.7f*u)
        when (tab) {
            LibraryTab.LIVE -> { drawRoundRect(color,Offset(3*u,6*u),Size(18*u,13*u),CornerRadius(2*u),style=stroke);line(8f,22f,16f,22f);line(8f,2f,12f,6f);line(16f,2f,12f,6f) }
            LibraryTab.MOVIES -> { drawRoundRect(color,Offset(3*u,4*u),Size(18*u,17*u),CornerRadius(2*u),style=stroke);line(3f,10f,21f,10f);line(7f,4f,10f,10f);line(14f,4f,17f,10f) }
            LibraryTab.SERIES -> { drawRoundRect(color,Offset(3*u,8*u),Size(18*u,13*u),CornerRadius(2*u),style=stroke);line(5f,4f,19f,4f);line(8f,1f,16f,1f) }
            LibraryTab.SETTINGS -> { drawCircle(color,7*u,center,style=stroke);drawCircle(color,2.5f*u,center,style=stroke);repeat(8) { i -> val a=i*Math.PI/4;line(12+7*kotlin.math.cos(a).toFloat(),12+7*kotlin.math.sin(a).toFloat(),12+10*kotlin.math.cos(a).toFloat(),12+10*kotlin.math.sin(a).toFloat()) } }
            LibraryTab.FAVOURITES -> { val path=Path().apply { moveTo(12*u,21*u);cubicTo(-6*u,9*u,6*u,-3*u,12*u,6*u);cubicTo(18*u,-3*u,30*u,9*u,12*u,21*u) };drawPath(path,color,style=stroke) }
            LibraryTab.SEARCH -> { drawCircle(color,7*u,Offset(10*u,10*u),style=stroke);line(15f,15f,22f,22f) }
        }
    }
}
