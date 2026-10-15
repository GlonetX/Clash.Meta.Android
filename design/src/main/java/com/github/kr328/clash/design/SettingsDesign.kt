package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignSettingsBinding
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.store.ServiceStore

/**
 * The settings root.
 *
 * Two cards: three frequently-used deep links with live summaries
 * (appearance / network / backup), then the four full pages. The summaries
 * are plain DataBinding strings refreshed by [refresh] whenever the stores
 * change on a page visit.
 */
class SettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val srvStore: ServiceStore,
    private val settings: SettingsStore,
) : Design<SettingsDesign.Request>(context) {
    enum class Request {
        OpenAppearance, OpenNetwork, OpenBackup,
        StartApp, StartNetwork, StartOverride, StartMetaFeature,
    }

    private val binding = DesignSettingsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    fun refresh() {
        binding.appearanceSummary = context.getString(
            if (settings.dynamicColor) R.string.settings_summary_dynamic_on
            else R.string.settings_summary_dynamic_off
        ) + " · " + context.getString(
            when (uiStore.darkMode.name) {
                "ForceBlack" -> R.string.always_black
                "ForceDark" -> R.string.always_dark
                "ForceLight" -> R.string.always_light
                else -> R.string.follow_system_android_10
            }
        )

        binding.networkSummary = context.getString(
            if (uiStore.enableVpn) R.string.settings_summary_vpn_on
            else R.string.settings_summary_vpn_off
        ) + " · " + srvStore.tunStackMode

        binding.backupSummary = settings.webDavUrl.takeIf { it.isNotBlank() }?.let {
            val last = settings.webDavLastBackup

            if (last > 0) {
                val minutes = (System.currentTimeMillis() - last) / 60000L

                when {
                    minutes < 1 -> context.getString(R.string.dashboard_provider_just_now)
                    minutes < 60 ->
                        context.getString(R.string.dashboard_provider_minutes_ago, minutes)
                    else -> context.getString(R.string.dashboard_provider_hours_ago, minutes / 60)
                }
            } else {
                context.getString(R.string.settings_webdav_never)
            }
        } ?: context.getString(R.string.settings_webdav_not_configured)

        binding.executePendingBindings()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.scrollRoot.bindAppBarElevation(binding.activityBarLayout)

        refresh()
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}