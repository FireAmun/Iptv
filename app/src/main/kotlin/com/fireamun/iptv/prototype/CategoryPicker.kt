package com.fireamun.iptv.prototype

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fireamun.iptv.core.designsystem.*

/** Only filters already-supplied labels. Choosing a row delegates to the existing category action. */
@Composable
internal fun CategoryPicker(categories: List<String>, selected: String?, television: Boolean,
    onSelect: (String?) -> Unit, onDismiss: () -> Unit) {
    val p = cinemaPalette()
    var query by remember { mutableStateOf("") }
    val shown = remember(categories, query) { categories.filter { it.contains(query.trim(), ignoreCase = true) } }
    val close = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismiss() { keyboard?.hide(); onDismiss() }
    fun choose(category: String?) { keyboard?.hide(); onSelect(category) }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val focusManager = LocalFocusManager.current
        val window = LocalWindowInfo.current
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp),
            contentAlignment = if (television) Alignment.Center else Alignment.BottomCenter) {
            Surface(Modifier.widthIn(max = if (television) 600.dp else 520.dp).fillMaxWidth().heightIn(max = minOf(maxHeight, 640.dp)).testTag("category_picker"),
                color = p.panel, contentColor = p.text, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, p.outline.copy(alpha = .3f))) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.browse_all_categories), Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp)
                        val label = stringResource(R.string.browse_close)
                        IconButton(onClick = ::dismiss, modifier = Modifier.size(48.dp).focusProperties { canFocus = true }.focusRequester(close).testTag("category_picker_close").semantics { contentDescription = label }) {
                            CinemaIcon(CinemaSymbol.CLOSE, tint = p.muted)
                        }
                    }
                    OutlinedTextField(query, { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("category_search")
                            .onPreviewKeyEvent { event ->
                                // A single-line editor consumes vertical arrows for cursor movement.
                                // On a remote they navigate between the picker controls instead.
                                if (television && event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.DirectionDown || event.key == Key.DirectionUp)) {
                                    keyboard?.hide()
                                    focusManager.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                                } else false
                            },
                        placeholder = { Text(stringResource(R.string.browse_find_category)) }, leadingIcon = { CinemaIcon(CinemaSymbol.SEARCH, tint = p.muted) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = p.background, unfocusedContainerColor = p.background,
                            focusedBorderColor = p.focus, unfocusedBorderColor = p.outline.copy(alpha = .45f), cursorColor = p.focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }))
                    if (shown.isEmpty()) Text(stringResource(R.string.browse_no_categories), Modifier.padding(vertical = 24.dp).testTag("category_picker_empty"), color = p.muted)
                    LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 420.dp).testTag("category_picker_list"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item(key = "all") { CategoryRow(stringResource(R.string.browse_all_categories), selected == null, "category_pick_all", television) { choose(null) } }
                        itemsIndexed(shown, key = { _, name -> "category:$name" }) { index, name -> CategoryRow(name, name == selected, "category_pick_$index", television) { choose(name) } }
                    }
                }
            }
        }
        LaunchedEffect(window.isWindowFocused) { if (television && window.isWindowFocused) { withFrameNanos { }; close.requestFocus() } }
    }
}

@Composable
private fun CategoryRow(label: String, isSelected: Boolean, tag: String, television: Boolean, onClick: () -> Unit) {
    val p = cinemaPalette()
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = if (television) 56.dp else 52.dp).testTag(tag)
        .focusProperties { canFocus = true }.onFocusChanged { focused = it.isFocused }.clip(RoundedCornerShape(12.dp))
        .background(if (isSelected) p.focus.copy(alpha = .12f) else if (focused) p.raised else p.panel)
        .border(if (focused) 2.dp else 0.dp, if (focused) p.focus else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(12.dp))
        .semantics { selected = isSelected }.clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = if (isSelected) p.focus else p.text, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
        if (isSelected) CinemaIcon(CinemaSymbol.CHECK, tint = p.focus)
    }
}
