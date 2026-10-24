package com.fireamun.iptv.ui.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun MobileWelcome(onChangeLanguage: () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(stringResource(R.string.mobile_title), style = MaterialTheme.typography.headlineLarge)
                Text(stringResource(R.string.mobile_description), style = MaterialTheme.typography.bodyLarge)
                Button(onClick = onChangeLanguage, modifier = Modifier.testTag("language_switch")) {
                    Text(stringResource(R.string.mobile_language))
                }
            }
        }
    }
}
