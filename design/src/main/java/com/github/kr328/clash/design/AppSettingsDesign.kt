package com.github.kr328.clash.design

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.text.InputType
import android.view.View
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.databinding.DesignSettingsCommonBinding
import com.github.kr328.clash.design.model.Behavior
import com.github.kr328.clash.design.model.DarkMode
import com.github.kr328.clash.design.model.ProbeServices
import com.github.kr328.clash.design.settings.DashboardWidgets
import com.github.kr328.clash.design.settings.NotificationStyle
import com.github.kr328.clash.design.settings.NavigationBarStyle
import com.github.kr328.clash.design.settings.SettingEntry
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingItem
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.SettingsBackup
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.settings.SubscriptionInfo
import com.github.kr328.clash.design.settings.SubscriptionMeta
import com.github.kr328.clash.design.settings.UpdateChannel
import com.github.kr328.clash.design.settings.WidgetSize
import com.github.kr328.clash.design.settings.WebDav
import com.github.kr328.clash.design.settings.appLocales
import com.github.kr328.clash.design.settings.chooseSettingOption
import com.github.kr328.clash.design.settings.confirmSetting
import com.github.kr328.clash.design.settings.editSettingEntries
import com.github.kr328.clash.design.settings.editSettingText
import com.github.kr328.clash.design.settings.selectSetting
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.ValidatorHttpUrl
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.service.store.ServiceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

/**
 * The "App" settings page: dashboard layout, app behaviour, appearance, the
 * service notification and config management.
 *
 * Everything is declared as an immutable [SettingItem] list and handed to
 * [SettingPage]; the only imperative code here is what a row does when it is
 * tapped. See DESIGN_SYSTEM.md.
 */
class AppSettingsDesign(
    context: Context,
    private val uiStore: UiStore,
    private val srvStore: ServiceStore,
    private val settings: SettingsStore,
    private val behavior: Behavior,
    private val running: Boolean,
    private val subscriptions: List<SubscriptionInfo>,
    private val onHideIconChange: (Boolean) -> Unit,
) : Design<AppSettingsDesign.Request>(context) {
    enum class Request {
        ReCreateAllActivities,
        OpenBatteryOptimizationSettings,
        OpenApplicationSettings,
    }

    private val binding = DesignSettingsCommonBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val page = SettingPage(binding.recyclerList, binding.activityBarLayout)

    init {
        binding.surface = surface

        binding.activityBarLayout.applyFrom(context)

        refresh()
    }

    /** Re-reads every store and rebinds. Safe to call from the event loop. */
    fun refresh() {
        page.render(buildItems())
    }

    private fun buildItems(): List<SettingItem> = listOf(
        SettingGroup(
            R.string.settings_group_dashboard,
            listOf(
                SettingRow.Action(
                    id = "dashboard_widgets",
                    title = R.string.settings_widgets,
                    summary = context.getString(
                        R.string.settings_format_count_of,
                        settings.dashboardWidgetSizes()
                            .count { (_, size) -> size != WidgetSize.Hidden },
                        DashboardWidgets.ALL.size,
                    ),
                    icon = R.drawable.ic_baseline_view_list,
                    onClick = { editDashboardWidgets() },
                ),
                SettingRow.Action(
                    id = "probe_services",
                    title = R.string.settings_probe_services,
                    summary = context.getString(
                        R.string.settings_format_count_of,
                        ProbeServices.resolve(settings).size,
                        ProbeServices.ALL.size,
                    ),
                    icon = R.drawable.ic_settings_movie,
                    onClick = { editProbeServices() },
                ),
            ),
        ),

        SettingGroup(
            R.string.behavior,
            listOf(
                SettingRow.Switch(
                    id = "auto_restart",
                    title = R.string.auto_restart,
                    summary = context.getText(R.string.allow_clash_auto_restart),
                    icon = R.drawable.ic_baseline_restore,
                    checked = behavior.autoRestart,
                    onToggle = { checked ->
                        behavior.autoRestart = checked

                        refresh()
                    },
                ),
                SettingRow.Switch(
                    id = "hide_app_icon",
                    title = R.string.hide_app_icon_title,
                    summary = context.getText(R.string.hide_app_icon_desc),
                    icon = R.drawable.ic_baseline_hide,
                    checked = uiStore.hideAppIcon,
                    onToggle = { checked ->
                        uiStore.hideAppIcon = checked

                        onHideIconChange(checked)

                        requests.trySend(Request.ReCreateAllActivities)
                    },
                ),
                SettingRow.Switch(
                    id = "hide_from_recents",
                    title = R.string.hide_from_recents_title,
                    summary = context.getText(R.string.hide_from_recents_desc),
                    icon = R.drawable.ic_baseline_stack,
                    checked = uiStore.hideFromRecents,
                    onToggle = { checked ->
                        uiStore.hideFromRecents = checked

                        requests.trySend(Request.ReCreateAllActivities)
                    },
                ),
                SettingRow.Action(
                    id = "notification_style",
                    title = R.string.settings_notification_style,
                    icon = R.drawable.ic_settings_notification,
                    summary = context.getText(notificationStyleText(settings.notificationStyle)),
                    onClick = { anchor -> selectNotificationStyle(anchor) },
                ),
                SettingRow.Switch(
                    id = "lightweight_mode",
                    title = R.string.settings_lightweight_mode,
                    summary = context.getText(R.string.settings_lightweight_mode_summary),
                    icon = R.drawable.ic_baseline_flash_on,
                    checked = settings.lightweightMode,
                    onToggle = { checked ->
                        settings.lightweightMode = checked

                        requests.trySend(Request.ReCreateAllActivities)
                    },
                ),
                SettingRow.Switch(
                    id = "app_update_auto_check",
                    title = R.string.settings_app_update_auto_check,
                    summary = context.getText(R.string.settings_app_update_auto_check_summary),
                    icon = R.drawable.ic_baseline_update,
                    checked = settings.appUpdateAutoCheck,
                    onToggle = { checked ->
                        settings.appUpdateAutoCheck = checked

                        refresh()
                    },
                ),
                SettingRow.Action(
                    id = "app_update_channel",
                    title = R.string.settings_app_update_channel,
                    icon = R.drawable.ic_baseline_update,
                    enabled = settings.appUpdateAutoCheck,
                    summary = context.getText(updateChannelText(settings.appUpdateChannel)),
                    onClick = { anchor -> selectUpdateChannel(anchor) },
                ),
                batteryRow(),
            ),
        ),

        SettingGroup(
            R.string.settings_group_appearance,
            listOf(
                SettingRow.Action(
                    id = "dark_mode",
                    title = R.string.dark_mode,
                    icon = R.drawable.ic_baseline_brightness_4,
                    summary = context.getText(darkModeText(uiStore.darkMode)),
                    onClick = { anchor -> selectDarkMode(anchor) },
                ),
                SettingRow.Switch(
                    id = "dynamic_color",
                    title = R.string.settings_dynamic_color,
                    summary = context.getText(
                        if (Build.VERSION.SDK_INT >= 31)
                            R.string.settings_dynamic_color_summary
                        else
                            R.string.settings_dynamic_color_unsupported
                    ),
                    icon = R.drawable.ic_settings_palette,
                    enabled = Build.VERSION.SDK_INT >= 31,
                    checked = Build.VERSION.SDK_INT >= 31 && settings.dynamicColor,
                    onToggle = { checked ->
                        settings.dynamicColor = checked

                        requests.trySend(Request.ReCreateAllActivities)
                    },
                ),
                SettingRow.Action(
                    id = "navigation_bar_style",
                    title = R.string.settings_navigation_bar_style,
                    icon = R.drawable.ic_baseline_apps,
                    summary = context.getText(navigationBarText(settings.navigationBarStyle)),
                    onClick = { anchor -> selectNavigationBarStyle(anchor) },
                ),
                SettingRow.Action(
                    id = "text_scale",
                    title = R.string.settings_text_scale,
                    icon = R.drawable.ic_settings_format_size,
                    summary = context.getString(
                        R.string.settings_format_percent,
                        settings.textScalePercent,
                    ),
                    onClick = { anchor -> selectTextScale(anchor) },
                ),
                SettingRow.Action(
                    id = "app_language",
                    title = R.string.settings_language,
                    icon = R.drawable.ic_settings_language,
                    summary = languageText(),
                    onClick = { anchor -> selectLanguage(anchor) },
                ),
            ),
        ),

        SettingGroup(
            R.string.service,
            listOf(
                SettingRow.Switch(
                    id = "dynamic_notification",
                    title = R.string.show_traffic,
                    summary = context.getText(R.string.show_traffic_summary),
                    icon = R.drawable.ic_baseline_domain,
                    enabled = !running,
                    checked = srvStore.dynamicNotification,
                    onToggle = { checked ->
                        srvStore.dynamicNotification = checked

                        refresh()
                    },
                ),
            ),
        ),

        SettingGroup(
            R.string.settings_group_config,
            listOf(
                SettingRow.Action(
                    id = "webdav_url",
                    title = R.string.settings_webdav_url,
                    icon = R.drawable.ic_baseline_cloud_download,
                    summary = settings.webDavUrl.ifEmpty {
                        context.getString(R.string.settings_format_not_set)
                    },
                    onClick = { editWebDavUrl() },
                ),
                SettingRow.Action(
                    id = "webdav_directory",
                    title = R.string.settings_webdav_directory,
                    icon = R.drawable.ic_outline_folder,
                    summary = settings.webDavDirectory.ifEmpty {
                        context.getString(R.string.settings_format_not_set)
                    },
                    onClick = { editWebDavDirectory() },
                ),
                SettingRow.Action(
                    id = "webdav_username",
                    title = R.string.settings_webdav_username,
                    icon = R.drawable.ic_baseline_apps,
                    summary = settings.webDavUsername.ifEmpty {
                        context.getString(R.string.settings_format_not_set)
                    },
                    onClick = { editWebDavUsername() },
                ),
                SettingRow.Action(
                    id = "webdav_password",
                    title = R.string.settings_webdav_password,
                    icon = R.drawable.ic_baseline_key,
                    summary = if (settings.webDavPassword.isEmpty())
                        context.getString(R.string.settings_format_not_set)
                    else
                        context.getString(R.string.settings_password_set),
                    onClick = { editWebDavPassword() },
                ),
                SettingRow.Action(
                    id = "webdav_backup",
                    title = R.string.settings_webdav_backup,
                    icon = R.drawable.ic_baseline_save,
                    enabled = settings.webDavUrl.isNotBlank(),
                    summary = context.getString(
                        R.string.settings_webdav_last_backup,
                        lastBackupText(),
                    ),
                    onClick = { backupNow() },
                ),
                SettingRow.Action(
                    id = "webdav_restore",
                    title = R.string.settings_webdav_restore,
                    icon = R.drawable.ic_baseline_restore,
                    enabled = settings.webDavUrl.isNotBlank(),
                    summary = context.getText(R.string.settings_webdav_restore_summary),
                    onClick = { restoreBackup() },
                ),
                SettingRow.Action(
                    id = "global_user_agent",
                    title = R.string.settings_user_agent,
                    icon = R.drawable.ic_baseline_public,
                    summary = settings.globalUserAgent.ifEmpty {
                        context.getString(R.string.settings_default_user_agent)
                    },
                    onClick = { editUserAgent() },
                ),
                SettingRow.Action(
                    id = "subscription_meta",
                    title = R.string.settings_subscription_meta,
                    icon = R.drawable.ic_outline_label,
                    summary = context.getString(
                        R.string.settings_format_count_of,
                        settings.subscriptionMeta().values.count { it.tag.isNotEmpty() },
                        subscriptions.size,
                    ),
                    onClick = { editSubscriptionMeta() },
                ),
                SettingRow.Switch(
                    id = "update_subscriptions_on_start",
                    title = R.string.settings_update_subscriptions_on_start,
                    summary = context.getText(
                        R.string.settings_update_subscriptions_on_start_summary
                    ),
                    icon = R.drawable.ic_baseline_sync,
                    checked = settings.updateSubscriptionsOnStart,
                    onToggle = { checked ->
                        settings.updateSubscriptionsOnStart = checked

                        refresh()
                    },
                ),
            ),
        ),
    )

    // ------------------------------------------------------------------- rows

    private fun batteryRow(): SettingRow.Action {
        val supported = Build.VERSION.SDK_INT >= 23
        val exempt = supported && isIgnoringBatteryOptimizations()

        val summary = when {
            !supported -> context.getString(R.string.settings_battery_optimization_unsupported)
            exempt -> context.getString(R.string.settings_battery_optimization_exempt)
            else -> context.getString(R.string.settings_battery_optimization_grant)
        }

        return SettingRow.Action(
            id = "battery_optimization",
            title = R.string.settings_battery_optimization,
            icon = R.drawable.ic_settings_battery,
            enabled = supported && !exempt,
            showChevron = !exempt,
            summary = summary,
            onClick = {
                requests.trySend(
                    if (isIgnoringBatteryOptimizations())
                        Request.OpenApplicationSettings
                    else
                        Request.OpenBatteryOptimizationSettings
                )
            },
        )
    }

    // ---------------------------------------------------------------- actions

    private fun editDashboardWidgets() {
        launch(Dispatchers.Main) {
            val initial = settings.dashboardWidgetOrder(DashboardWidgets.keys)
                .map { key ->
                    SettingEntry.Sized(
                        key = key,
                        title = context.getText(widgetTitle(key)),
                        summary = context.getText(widgetSizeText(settings.dashboardWidgetSize(key))),
                        size = settings.dashboardWidgetSize(key),
                    )
                }

            val edited = context.editSettingEntries(
                title = context.getText(R.string.settings_widgets),
                initial = initial,
            ) ?: return@launch

            settings.setDashboardWidgetOrder(edited.map { it.key })

            for (entry in edited) {
                if (entry is SettingEntry.Sized)
                    settings.setDashboardWidgetSize(entry.key, entry.size)
            }

            requests.trySend(Request.ReCreateAllActivities)
        }
    }

    private fun editProbeServices() {
        launch(Dispatchers.Main) {
            // resolve() so a never-configured store opens with every
            // service checked, matching what is actually probed.
            val enabled = ProbeServices.resolve(settings).map { it.name }.toSet()

            val initial = settings.probeServiceOrder(ProbeServices.names)
                .map { name ->
                    SettingEntry.Enabled(
                        key = name,
                        title = name,
                        checked = name in enabled,
                    )
                }

            val edited = context.editSettingEntries(
                title = context.getText(R.string.settings_probe_services),
                initial = initial,
            ) ?: return@launch

            settings.setProbeServiceOrder(edited.map { it.key })

            settings.probeServiceEnabled = edited
                .filterIsInstance<SettingEntry.Enabled>()
                .filter { it.checked }
                .map { it.key }
                .toSet()
        }
    }

    private fun selectNotificationStyle(anchor: View) {
        val values = NotificationStyle.values()

        choose(
            anchor = anchor,
            title = R.string.settings_notification_style,
            labels = values.map { context.getText(notificationStyleText(it)) },
            selected = values.indexOf(settings.notificationStyle),
        ) { index ->
            if (index != values.indexOf(settings.notificationStyle)) {
                settings.notificationStyle = values[index]

                requests.trySend(Request.ReCreateAllActivities)
            }
        }
    }

    private fun selectUpdateChannel(anchor: View) {
        val values = UpdateChannel.values()

        choose(
            anchor = anchor,
            title = R.string.settings_app_update_channel,
            labels = values.map { context.getText(updateChannelText(it)) },
            selected = values.indexOf(settings.appUpdateChannel),
        ) { index ->
            if (index != values.indexOf(settings.appUpdateChannel)) {
                settings.appUpdateChannel = values[index]

                refresh()
            }
        }
    }

    private fun selectDarkMode(anchor: View) {
        val values = DarkMode.values()

        choose(
            anchor = anchor,
            title = R.string.dark_mode,
            labels = listOf(
                context.getText(R.string.follow_system_android_10),
                context.getText(R.string.always_light),
                context.getText(R.string.always_dark),
                context.getText(R.string.always_black),
            ),
            selected = values.indexOf(uiStore.darkMode),
        ) { index ->
            if (index != values.indexOf(uiStore.darkMode)) {
                uiStore.darkMode = values[index]

                requests.trySend(Request.ReCreateAllActivities)
            }
        }
    }

    private fun selectNavigationBarStyle(anchor: View) {
        val values = NavigationBarStyle.values()

        choose(
            anchor = anchor,
            title = R.string.settings_navigation_bar_style,
            labels = values.map { context.getText(navigationBarText(it)) },
            selected = values.indexOf(settings.navigationBarStyle),
        ) { index ->
            if (index != values.indexOf(settings.navigationBarStyle)) {
                settings.navigationBarStyle = values[index]

                requests.trySend(Request.ReCreateAllActivities)
            }
        }
    }

    private fun selectTextScale(anchor: View) {
        val values = (SettingsStore.TEXT_SCALE_MIN..SettingsStore.TEXT_SCALE_MAX step
            SettingsStore.TEXT_SCALE_STEP).toList()

        choose(
            anchor = anchor,
            title = R.string.settings_text_scale,
            labels = values.map { "$it%" },
            selected = values.indexOf(settings.textScalePercent),
        ) { index ->
            if (index != values.indexOf(settings.textScalePercent)) {
                settings.textScale = values[index]

                requests.trySend(Request.ReCreateAllActivities)
            }
        }
    }

    private fun selectLanguage(anchor: View) {
        val locales = appLocales(context)

        choose(
            anchor = anchor,
            title = R.string.settings_language,
            labels = locales.map { it.label },
            selected = locales.indexOfFirst { it.tag == settings.appLanguage },
        ) { index ->
            if (index != locales.indexOfFirst { it.tag == settings.appLanguage }) {
                settings.appLanguage = locales[index].tag

                requests.trySend(Request.ReCreateAllActivities)
            }
        }
    }

    private fun editWebDavUrl() {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = settings.webDavUrl,
                title = context.getText(R.string.settings_webdav_url),
                hint = context.getText(R.string.url),
                error = context.getString(R.string.settings_invalid_url),
                allowReset = false,
                validator = ValidatorHttpUrl,
            ) ?: return@launch

            settings.webDavUrl = text.trim()

            refresh()
        }
    }

    private fun editWebDavDirectory() {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = settings.webDavDirectory,
                title = context.getText(R.string.settings_webdav_directory),
                hint = context.getText(R.string.settings_webdav_directory),
                allowReset = false,
            ) ?: return@launch

            settings.webDavDirectory = text.trim()

            refresh()
        }
    }

    private fun editWebDavUsername() {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = settings.webDavUsername,
                title = context.getText(R.string.settings_webdav_username),
                allowReset = false,
            ) ?: return@launch

            settings.webDavUsername = text.trim()

            refresh()
        }
    }

    private fun editWebDavPassword() {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = settings.webDavPassword,
                title = context.getText(R.string.settings_webdav_password),
                allowReset = false,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            ) ?: return@launch

            settings.webDavPassword = text

            refresh()
        }
    }

    private fun editUserAgent() {
        launch(Dispatchers.Main) {
            val text = context.editSettingText(
                initial = settings.globalUserAgent,
                title = context.getText(R.string.settings_user_agent),
                hint = context.getText(R.string.settings_user_agent_summary),
            ) ?: return@launch

            settings.globalUserAgent = text.trim()

            refresh()
        }
    }

    private fun editSubscriptionMeta() {
        launch(Dispatchers.Main) {
            if (subscriptions.isEmpty()) {
                showToast(R.string.settings_subscription_no_profiles, ToastDuration.Long)

                return@launch
            }

            val meta = settings.subscriptionMeta()

            val chosen = context.chooseSettingOption(
                title = context.getText(R.string.settings_subscription_meta),
                labels = subscriptions.map { subscription ->
                    val tag = meta[subscription.uuid]?.tag.orEmpty()

                    if (tag.isEmpty())
                        subscription.name
                    else
                        context.getString(
                            R.string.settings_subscription_tag_format,
                            subscription.name,
                            tag,
                        )
                },
                values = subscriptions.map { it.uuid },
            ) ?: return@launch

            val current = settings.subscriptionMeta()[chosen] ?: SubscriptionMeta(chosen)

            val tag = context.editSettingText(
                initial = current.tag,
                title = context.getText(R.string.settings_subscription_tag_hint),
                hint = context.getText(R.string.settings_subscription_tag_hint),
            ) ?: current.tag

            val note = context.editSettingText(
                initial = current.note,
                title = context.getText(R.string.settings_subscription_note_hint),
                hint = context.getText(R.string.settings_subscription_note_hint),
                allowReset = false,
            ) ?: current.note

            settings.setSubscriptionMeta(chosen, tag.trim(), note.trim())

            refresh()
        }
    }

    private fun backupNow() {
        launch(Dispatchers.Main) {
            val config = webDavConfig()

            if (config == null) {
                showToast(R.string.settings_webdav_url_required, ToastDuration.Long)

                return@launch
            }

            showToast(R.string.settings_action_in_progress, ToastDuration.Indefinite)

            try {
                val archive = SettingsBackup.create(context)

                WebDav.upload(config, SettingsBackup.backupFileName(), archive)

                settings.webDavLastBackup = System.currentTimeMillis()

                showToast(
                    context.getString(R.string.settings_webdav_backup_ok, archive.size / 1024),
                    ToastDuration.Long,
                )
            } catch (e: Exception) {
                showToast(
                    context.getString(
                        R.string.settings_webdav_backup_failed,
                        e.message ?: e.javaClass.simpleName,
                    ),
                    ToastDuration.Long,
                )
            }

            refresh()
        }
    }

    private fun restoreBackup() {
        launch(Dispatchers.Main) {
            val config = webDavConfig()

            if (config == null) {
                showToast(R.string.settings_webdav_url_required, ToastDuration.Long)

                return@launch
            }

            showToast(R.string.settings_action_in_progress, ToastDuration.Indefinite)

            val remote = try {
                WebDav.list(config)
            } catch (e: Exception) {
                showToast(
                    context.getString(
                        R.string.settings_webdav_restore_failed,
                        e.message ?: e.javaClass.simpleName,
                    ),
                    ToastDuration.Long,
                )

                return@launch
            }

            if (remote.isEmpty()) {
                showToast(R.string.settings_webdav_no_backups, ToastDuration.Long)

                return@launch
            }

            val chosen = if (remote.size == 1) {
                remote.first()
            } else {
                context.chooseSettingOption(
                    title = context.getText(R.string.settings_webdav_pick_backup),
                    labels = remote.map { it.name },
                    values = remote,
                ) ?: return@launch
            }

            val confirmed = context.confirmSetting(
                title = context.getText(R.string.settings_webdav_restore),
                message = context.getText(R.string.settings_webdav_restore_confirm),
            )

            if (!confirmed)
                return@launch

            try {
                val archive = WebDav.download(config, chosen.name)

                SettingsBackup.restore(context, archive)

                showToast(
                    context.getString(R.string.settings_webdav_restore_ok, chosen.name),
                    ToastDuration.Long,
                )
            } catch (e: Exception) {
                showToast(
                    context.getString(
                        R.string.settings_webdav_restore_failed,
                        e.message ?: e.javaClass.simpleName,
                    ),
                    ToastDuration.Long,
                )
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Shared shape of every "pick one of N" row: shows a popup anchored to the
     * row that was tapped and applies the result through [apply].
     */
    private fun choose(
        anchor: View,
        @androidx.annotation.StringRes title: Int,
        labels: List<CharSequence>,
        selected: Int,
        apply: (Int) -> Unit,
    ) {
        val current = selected.coerceIn(0, (labels.size - 1).coerceAtLeast(0))

        launch(Dispatchers.Main) {
            val index = context.selectSetting(anchor, labels, current)

            if (index != current)
                apply(index)
        }
    }

    private fun webDavConfig(): WebDav.Config? {
        if (settings.webDavUrl.isBlank())
            return null

        return WebDav.Config(
            url = settings.webDavUrl,
            directory = settings.webDavDirectory,
            username = settings.webDavUsername,
            password = settings.webDavPassword,
        )
    }

    private fun languageText(): CharSequence {
        val tag = settings.appLanguage

        if (tag.isEmpty())
            return context.getString(R.string.settings_language_system)

        return appLocales(context).firstOrNull { it.tag == tag }?.label ?: tag
    }

    private fun lastBackupText(): String {
        val at = settings.webDavLastBackup

        if (at <= 0)
            return context.getString(R.string.settings_webdav_never)

        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(at))
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < 23)
            return false

        val power = context.getSystemService<PowerManager>() ?: return false

        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun notificationStyleText(style: NotificationStyle): Int = when (style) {
        NotificationStyle.Default -> R.string.settings_notification_style_default
        NotificationStyle.Compact -> R.string.settings_notification_style_compact
        NotificationStyle.Minimal -> R.string.settings_notification_style_minimal
    }

    private fun updateChannelText(channel: UpdateChannel): Int = when (channel) {
        UpdateChannel.Stable -> R.string.settings_app_update_channel_stable
        UpdateChannel.Prerelease -> R.string.settings_app_update_channel_prerelease
    }

    private fun navigationBarText(style: NavigationBarStyle): Int = when (style) {
        NavigationBarStyle.BottomBar -> R.string.settings_navigation_bar_bottom
        NavigationBarStyle.FloatingDock -> R.string.settings_navigation_bar_dock
    }

    private fun darkModeText(mode: DarkMode): Int = when (mode) {
        DarkMode.Auto -> R.string.follow_system_android_10
        DarkMode.ForceLight -> R.string.always_light
        DarkMode.ForceDark -> R.string.always_dark
        DarkMode.ForceBlack -> R.string.always_black
    }

    private fun widgetTitle(key: String): Int {
        return DashboardWidgets.ALL.firstOrNull { it.key == key }?.titleRes
            ?: R.string.settings_widgets
    }

    private fun widgetSizeText(size: WidgetSize): Int = when (size) {
        WidgetSize.Large -> R.string.settings_widget_size_large
        WidgetSize.Small -> R.string.settings_widget_size_small
        WidgetSize.Hidden -> R.string.settings_widget_size_hidden
    }
}
