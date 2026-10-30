package com.github.kr328.clash.service

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.remote.Remote
import com.github.kr328.clash.service.settings.SettingsUpdate
import com.github.kr328.clash.service.settings.UpdateStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsPlatformInstrumentedTest {
    @Test fun autoRestartSettingChangesTheRealReceiverAndRestoresItsValue() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activity = ActivityScenario.launch(MainActivity::class.java)
        try {
            val settings = withTimeout(15_000) { Remote.service.remote.get().settings() }
            val before = settings.getSnapshot()
            val component = ComponentName(context.packageName, "com.github.kr328.clash.RestartReceiver")
            try {
                val changed = settings.update(before.revision, SettingsUpdate(runtime = before.desired.copy(autoRestart = !before.desired.autoRestart)))
                assertTrue("Update failed: ${changed.error}", changed.status in setOf(UpdateStatus.Applied, UpdateStatus.Saved))
                val actual = context.packageManager.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                assertEquals(!before.desired.autoRestart, actual)
            } finally {
                val current = settings.getSnapshot()
                val restored = settings.update(current.revision, SettingsUpdate(runtime = current.desired.copy(autoRestart = before.desired.autoRestart)))
                assertTrue("Restore failed: ${restored.error}", restored.status in setOf(UpdateStatus.Applied, UpdateStatus.Saved))
            }
        } finally { activity.close() }
    }
}
