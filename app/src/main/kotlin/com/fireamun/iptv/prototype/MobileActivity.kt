package com.fireamun.iptv.prototype

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.fireamun.iptv.ui.mobile.MobileWelcome

class MobileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MobileWelcome(onChangeLanguage = {}) }
    }
}
