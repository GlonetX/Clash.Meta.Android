package com.github.kr328.clash.presentation.screens

import androidx.compose.ui.test.assertIsDisplayed
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

@RunWith(AndroidJUnit4::class)
class AppearanceSettingsSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languagePreferenceChangesTheLiveComposeScreen() {
        val context = compose.activity
        compose.onAllNodesWithText("Settings")[0].performClick()
        compose.onNodeWithText(context.getString(R.string.cs_application)).performClick()
        compose.onNodeWithText(context.getString(R.string.cs_language)).performClick()
        compose.onNodeWithText("简体中文").performClick()
        try {
            compose.waitUntil(15_000) { compose.onAllNodesWithText("语言").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("语言").assertIsDisplayed()
        } finally {
            compose.onNodeWithText("语言").performClick()
            compose.onAllNodesWithText("跟随系统")[1].performClick()
            compose.waitUntil(15_000) { compose.onAllNodesWithText(context.getString(R.string.cs_language)).fetchSemanticsNodes().isNotEmpty() }
        }
    }
}
