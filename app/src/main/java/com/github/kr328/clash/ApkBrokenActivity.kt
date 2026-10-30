package com.github.kr328.clash

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.design.compose.GlonetXTheme
import com.github.kr328.clash.presentation.screens.BrokenInstallationScreen
import com.github.kr328.clash.presentation.screens.SupportScaffold

/** Does not bind repositories: the service/native library may be unavailable. */
class ApkBrokenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlonetXTheme {
                SupportScaffold(stringResource(com.github.kr328.clash.design.R.string.application_broken), ::finish) {
                    BrokenInstallationScreen { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
                }
            }
        }
    }
}
