package com.github.kr328.clash.presentation.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.kr328.clash.MainActivity
import com.github.kr328.clash.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run against a clean install: validates the actual activity, Binder and migration path. */
@RunWith(AndroidJUnit4::class)
class FreshInstallSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun newInstallUsesPrivateNetworkDefaults() {
        val context = compose.activity
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.onNodeWithText(context.getString(R.string.cs_network)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(context.getString(R.string.cs_ipv6)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.cs_block)).assertIsDisplayed()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
    }
}
