package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.SettingTips
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root

class ApkBrokenDesign(context: Context) : Design<ApkBrokenDesign.Request>(context) {
    data class Request(val url: String)

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
                SettingTips(R.string.application_broken_tips),
                SettingGroup(
                    R.string.reinstall,
                    listOf(
                        SettingRow.Action(
                            id = "releases",
                            title = R.string.github_releases,
                            summary = context.getText(R.string.meta_github_url),
                            icon = R.drawable.ic_settings_about,
                            onClick = {
                                requests.trySend(
                                    Request(context.getString(R.string.meta_github_url))
                                )
                            },
                        ),
                    ),
                ),
            )
        )
    }
}
