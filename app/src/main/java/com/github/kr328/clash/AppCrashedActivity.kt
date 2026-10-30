package com.github.kr328.clash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.kr328.clash.design.compose.GlonetXTheme
import com.github.kr328.clash.log.SystemLogcat
import com.github.kr328.clash.presentation.screens.CrashScreen
import com.github.kr328.clash.presentation.screens.SupportScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CrashLogViewModel : ViewModel() {
    private val mutableLogs = MutableStateFlow<String?>(null)
    val logs = mutableLogs.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            mutableLogs.value = runCatching { SystemLogcat.dumpCrash().takeLast(128 * 1024) }
                .getOrElse { it.message ?: it.javaClass.simpleName }
        }
    }
}

/** Standalone, so diagnostics remain usable when Binder or the native core failed. */
class AppCrashedActivity : ComponentActivity() {
    private val model: CrashLogViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlonetXTheme {
                val logs = model.logs.collectAsStateWithLifecycle().value
                SupportScaffold(stringResource(com.github.kr328.clash.design.R.string.application_crashed), ::finish) {
                    CrashScreen(logs) {
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, logs)
                        }, getString(R.string.support_share_logs)))
                    }
                }
            }
        }
    }
}
