package com.github.kr328.clash.design

import android.content.Context
import android.net.Uri
import android.view.View
import androidx.annotation.StringRes
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.SettingTips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class HelpDesign(
    context: Context,
    private val openLink: (Uri) -> Unit,
) : Design<Unit>(context) {
    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val page = SettingPage(binding.recyclerList, binding.activityBarLayout)

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        page.render(
            listOf(
                SettingTips(R.string.tips_help),
                SettingGroup(
                    R.string.document,
                    listOf(
                        link("clash_wiki", R.string.clash_wiki, R.string.clash_wiki_url),
                        link("clash_meta_wiki", R.string.clash_meta_wiki, R.string.clash_meta_wiki_url),
                    ),
                ),
                SettingGroup(
                    R.string.sources,
                    listOf(
                        link("core", R.string.clash_meta_core, R.string.clash_meta_core_url),
                        link("android", R.string.clash_meta_for_android, R.string.meta_github_url),
                        link("glonetx", R.string.reforged_by_glonetx, R.string.glonetx_url),
                    ),
                ),
            )
        )
    }

    private fun link(id: String, @StringRes title: Int, @StringRes url: Int): SettingRow.Action =
        SettingRow.Action(
            id = id,
            title = title,
            summary = context.getText(url),
            icon = R.drawable.ic_settings_about,
            onClick = { openLink(Uri.parse(context.getString(url))) },
        )
}