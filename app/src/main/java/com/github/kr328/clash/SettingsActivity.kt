package com.github.kr328.clash

import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.design.SettingsDesign
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.service.store.ServiceStore
import com.github.kr328.clash.design.R as DesignR
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class SettingsActivity : BaseActivity<SettingsDesign>() {
    override suspend fun main() {
        val design = SettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            SettingsStore(this),
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        SettingsDesign.Request.OpenAppearance ->
                            startActivity(
                                AppSettingsActivity::class.intent.putExtra(
                                    Intents.EXTRA_SCROLL_TO,
                                    "group/" + DesignR.string.settings_group_appearance,
                                )
                            )
                        SettingsDesign.Request.OpenNetwork ->
                            startActivity(
                                NetworkSettingsActivity::class.intent.putExtra(
                                    Intents.EXTRA_SCROLL_TO,
                                    "group/" + DesignR.string.settings_group_core,
                                )
                            )
                        SettingsDesign.Request.OpenBackup ->
                            startActivity(
                                AppSettingsActivity::class.intent.putExtra(
                                    Intents.EXTRA_SCROLL_TO,
                                    "group/" + DesignR.string.settings_group_config,
                                )
                            )
                        SettingsDesign.Request.StartApp ->
                            startActivity(AppSettingsActivity::class.intent)
                        SettingsDesign.Request.StartNetwork ->
                            startActivity(NetworkSettingsActivity::class.intent)
                        SettingsDesign.Request.StartOverride ->
                            startActivity(OverrideSettingsActivity::class.intent)
                        SettingsDesign.Request.StartMetaFeature ->
                            startActivity(MetaFeatureSettingsActivity::class.intent)
                    }
                }
            }
        }
    }
}