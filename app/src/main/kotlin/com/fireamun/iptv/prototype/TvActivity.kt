package com.fireamun.iptv.prototype

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.fireamun.iptv.ui.tv.TvWelcome

class TvActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TvWelcome(onChangeLanguage = { toggleAppLanguage() }) }
    }
}
