package com.fireamun.iptv.ui.tv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*

@Composable
fun TvWelcome(onChangeLanguage: () -> Unit) {
    val languageFocus = remember { FocusRequester() }
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 48.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.tv_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.tv_description), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onChangeLanguage, modifier = Modifier.focusRequester(languageFocus).testTag("language_switch")) {
                    Text(stringResource(R.string.tv_language))
                }
            }
        }
    }
    LaunchedEffect(Unit) { languageFocus.requestFocus() }
}
