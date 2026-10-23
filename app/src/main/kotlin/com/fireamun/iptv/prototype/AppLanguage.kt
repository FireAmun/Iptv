package com.fireamun.iptv.prototype

import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

internal fun AppCompatActivity.toggleAppLanguage() {
    val nextLanguage = if (resources.configuration.locales[0].language == "ar") "en" else "ar"
    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(nextLanguage))
}
