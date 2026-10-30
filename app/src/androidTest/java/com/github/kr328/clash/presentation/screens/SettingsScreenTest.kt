package com.github.kr328.clash.presentation.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.R
import com.github.kr328.clash.service.settings.Ipv6Mode
import com.github.kr328.clash.service.settings.SettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private fun label(id: Int) = context.getString(id)

    @Test fun ipv6ChoicesSubmitEachValueAndRenderCommittedSnapshot() {
        var snapshot by mutableStateOf(SettingsSnapshot())
        val submitted = mutableListOf<Ipv6Mode>()
        compose.setContent {
            MaterialTheme {
                SettingsScreen("network-settings", snapshot,
                    onRuntime = { change ->
                        val updated = change(snapshot.desired)
                        submitted.add(updated.ipv6Mode)
                        snapshot = snapshot.copy(desired = updated)
                    }, onUi = {}, onNavigate = {}, onCheckUpdate = {}, onPermission = {})
            }
        }
        listOf(R.string.cs_proxy to Ipv6Mode.Proxy, R.string.cs_direct to Ipv6Mode.Direct,
            R.string.cs_block to Ipv6Mode.Block).forEach { (title, mode) ->
            compose.onNodeWithText(label(R.string.cs_ipv6)).performClick()
            compose.onNodeWithText(label(title)).performClick()
            compose.onNodeWithText(label(title)).assertIsDisplayed()
            compose.runOnIdle { assertEquals(mode, snapshot.desired.ipv6Mode) }
        }
        compose.runOnIdle { assertEquals(listOf(Ipv6Mode.Proxy, Ipv6Mode.Direct, Ipv6Mode.Block), submitted) }
    }

    @Test fun switchSubmitsChangeAndRendersUpdatedSnapshot() {
        var snapshot by mutableStateOf(SettingsSnapshot())
        var submissions = 0
        compose.setContent {
            MaterialTheme {
                SettingsScreen("network-settings", snapshot,
                    onRuntime = { change -> submissions++; snapshot = snapshot.copy(desired = change(snapshot.desired)) },
                    onUi = {}, onNavigate = {}, onCheckUpdate = {}, onPermission = {})
            }
        }
        val initial = snapshot.desired.enableVpn
        val vpnSwitch = compose.onAllNodes(isToggleable())[0]
        if (initial) vpnSwitch.assertIsOn() else vpnSwitch.assertIsOff()
        vpnSwitch.performClick()
        if (initial) vpnSwitch.assertIsOff() else vpnSwitch.assertIsOn()
        compose.runOnIdle { assertEquals(!initial, snapshot.desired.enableVpn); assertEquals(1, submissions) }
    }

    @Test fun backupAndPendingReconnectNavigateToTheirRoutes() {
        val routes = mutableListOf<String>()
        val snapshot = SettingsSnapshot(pendingRestartFields = listOf("enableVpn"))
        compose.setContent {
            MaterialTheme {
                SettingsScreen("settings", snapshot, onRuntime = {}, onUi = {},
                    onNavigate = { routes.add(it) }, onCheckUpdate = {}, onPermission = {})
            }
        }
        compose.onNodeWithText(label(R.string.cs_pending)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.cs_reconnect)).performClick()
        compose.onNodeWithText(label(R.string.cs_backup)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf("reconnect", "backup"), routes) }
    }
}
