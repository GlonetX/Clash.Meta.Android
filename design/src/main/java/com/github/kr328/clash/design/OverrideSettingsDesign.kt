package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.databinding.DesignSettingsOverideBinding
import com.github.kr328.clash.design.settings.NullableTextAdapter
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingItem
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.TextAdapter
import com.github.kr328.clash.design.settings.confirmSetting
import com.github.kr328.clash.design.settings.editSettingText
import com.github.kr328.clash.design.settings.editSettingTextList
import com.github.kr328.clash.design.settings.editSettingTextMap
import com.github.kr328.clash.design.settings.selectSetting
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The "Override" page: the raw mihomo config keys, mapped straight onto
 * [ConfigurationOverride].
 *
 * Behaviour is unchanged from the DSL version it replaces: the same fields,
 * the same `null` meaning "do not modify", and the same DNS group that greys
 * itself out when DNS is forced off. It is now a list of [SettingItem]s fed
 * to [SettingPage] instead of views appended to a `LinearLayout`.
 */
class OverrideSettingsDesign(
    context: Context,
    private val configuration: ConfigurationOverride,
) : Design<OverrideSettingsDesign.Request>(context) {
    enum class Request {
        ResetOverride
    }

    private val binding = DesignSettingsOverideBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val page = SettingPage(binding.recyclerList, binding.activityBarLayout)

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        refresh()
    }

    fun refresh() {
        page.render(buildItems())
    }

    suspend fun requestResetConfirm(): Boolean {
        return suspendCancellableCoroutine { ctx ->
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(R.string.reset_override_settings)
                .setMessage(R.string.reset_override_settings_message)
                .setPositiveButton(R.string.ok) { _, _ -> ctx.resume(true) }
                .setNegativeButton(R.string.cancel) { _, _ -> }
                .show()

            dialog.setOnDismissListener {
                if (!ctx.isCompleted)
                    ctx.resume(false)
            }

            ctx.invokeOnCancellation {
                dialog.dismiss()
            }
        }
    }

    fun requestClear() {
        requests.trySend(Request.ResetOverride)
    }

    private fun buildItems(): List<SettingItem> = listOf(
        SettingGroup(R.string.general, generalRows()),
        SettingGroup(R.string.dns, dnsRows()),
    )

    private fun generalRows(): List<SettingRow> = listOf(
        port("http_port", R.string.http_port) { configuration.httpPort = it },
        port("socks_port", R.string.socks_port) { configuration.socksPort = it },
        port("redirect_port", R.string.redirect_port) { configuration.redirectPort = it },
        port("tproxy_port", R.string.tproxy_port) { configuration.tproxyPort = it },
        port("mixed_port", R.string.mixed_port) { configuration.mixedPort = it },

        textList(
            id = "authentication",
            title = R.string.authentication,
            value = configuration.authentication,
        ) { configuration.authentication = it },

        triState(
            id = "allow_lan",
            title = R.string.allow_lan,
            value = configuration.allowLan,
            beforeApply = { picked ->
                if (picked == true && configuration.authentication.isNullOrEmpty()) {
                    context.confirmSetting(
                        title = context.getText(R.string.allow_lan_warning_title),
                        message = context.getText(R.string.allow_lan_warning_message),
                    )
                } else {
                    true
                }
            },
        ) { configuration.allowLan = it },

        triState(
            id = "ipv6",
            title = R.string.ipv6,
            value = configuration.ipv6,
        ) { configuration.ipv6 = it },

        text(
            id = "bind_address",
            title = R.string.bind_address,
            value = configuration.bindAddress,
            empty = R.string.default_,
        ) { configuration.bindAddress = it },

        text(
            id = "external_controller",
            title = R.string.external_controller,
            value = configuration.externalController,
            empty = R.string.default_,
        ) { configuration.externalController = it },

        text(
            id = "external_controller_tls",
            title = R.string.external_controller_tls,
            value = configuration.externalControllerTLS,
            empty = R.string.default_,
        ) { configuration.externalControllerTLS = it },

        textList(
            id = "allow_origins",
            title = R.string.allow_origins,
            value = configuration.externalControllerCors.allowOrigins,
        ) { configuration.externalControllerCors.allowOrigins = it },

        triState(
            id = "allow_private_network",
            title = R.string.allow_private_network,
            value = configuration.externalControllerCors.allowPrivateNetwork,
        ) { configuration.externalControllerCors.allowPrivateNetwork = it },

        text(
            id = "secret",
            title = R.string.secret,
            value = configuration.secret,
            empty = R.string.default_,
        ) { configuration.secret = it },

        choice(
            id = "mode",
            title = R.string.mode,
            labels = listOf(
                R.string.dont_modify,
                R.string.direct_mode,
                R.string.global_mode,
                R.string.rule_mode,
            ),
            values = listOf<TunnelState.Mode?>(
                null,
                TunnelState.Mode.Direct,
                TunnelState.Mode.Global,
                TunnelState.Mode.Rule,
            ),
            current = configuration.mode,
        ) { configuration.mode = it },

        choice(
            id = "log_level",
            title = R.string.log_level,
            labels = listOf(
                R.string.dont_modify,
                R.string.info,
                R.string.warning,
                R.string.error,
                R.string.debug,
                R.string.silent,
            ),
            values = listOf<LogMessage.Level?>(
                null,
                LogMessage.Level.Info,
                LogMessage.Level.Warning,
                LogMessage.Level.Error,
                LogMessage.Level.Debug,
                LogMessage.Level.Silent,
            ),
            current = configuration.logLevel,
        ) { configuration.logLevel = it },

        textMap(
            id = "hosts",
            title = R.string.hosts,
            value = configuration.hosts,
        ) { configuration.hosts = it },
    )

    private fun dnsRows(): List<SettingRow> {
        val enabled = configuration.dns.enable != false

        val rows = mutableListOf(
            SettingRow.Action(
                id = "dns_strategy",
                title = R.string.strategy,
                summary = context.getText(
                    when (configuration.dns.enable) {
                        true -> R.string.force_enable
                        false -> R.string.use_built_in
                        null -> R.string.dont_modify
                    }
                ),
                onClick = { anchor ->
                    choose(
                        anchor = anchor,
                        labels = listOf(
                            R.string.dont_modify,
                            R.string.force_enable,
                            R.string.use_built_in,
                        ).map { context.getText(it) },
                        values = listOf<Boolean?>(null, true, false),
                        current = configuration.dns.enable,
                    ) { picked ->
                        configuration.dns.enable = picked

                        refresh()
                    }
                },
            )
        )

        rows.addAll(
            listOf(
                triState(
                    id = "prefer_h3",
                    title = R.string.prefer_h3,
                    value = configuration.dns.preferH3,
                    enabled = enabled,
                ) { configuration.dns.preferH3 = it },

                text(
                    id = "listen",
                    title = R.string.listen,
                    value = configuration.dns.listen,
                    empty = R.string.disabled,
                    enabled = enabled,
                ) { configuration.dns.listen = it },

                triState(
                    id = "append_system_dns",
                    title = R.string.append_system_dns,
                    value = configuration.app.appendSystemDns,
                    enabled = enabled,
                ) { configuration.app.appendSystemDns = it },

                triState(
                    id = "dns_ipv6",
                    title = R.string.ipv6,
                    value = configuration.dns.ipv6,
                    enabled = enabled,
                ) { configuration.dns.ipv6 = it },

                triState(
                    id = "use_hosts",
                    title = R.string.use_hosts,
                    value = configuration.dns.useHosts,
                    enabled = enabled,
                ) { configuration.dns.useHosts = it },

                choice(
                    id = "enhanced_mode",
                    title = R.string.enhanced_mode,
                    labels = listOf(
                        R.string.dont_modify,
                        R.string.disabled,
                        R.string.fakeip,
                        R.string.mapping,
                    ),
                    values = listOf<ConfigurationOverride.DnsEnhancedMode?>(
                        null,
                        ConfigurationOverride.DnsEnhancedMode.None,
                        ConfigurationOverride.DnsEnhancedMode.FakeIp,
                        ConfigurationOverride.DnsEnhancedMode.Mapping,
                    ),
                    current = configuration.dns.enhancedMode,
                    enabled = enabled,
                ) { configuration.dns.enhancedMode = it },

                textList(
                    id = "name_server",
                    title = R.string.name_server,
                    value = configuration.dns.nameServer,
                    enabled = enabled,
                ) { configuration.dns.nameServer = it },

                textList(
                    id = "fallback",
                    title = R.string.fallback,
                    value = configuration.dns.fallback,
                    enabled = enabled,
                ) { configuration.dns.fallback = it },

                textList(
                    id = "default_name_server",
                    title = R.string.default_name_server,
                    value = configuration.dns.defaultServer,
                    enabled = enabled,
                ) { configuration.dns.defaultServer = it },

                textList(
                    id = "fakeip_filter",
                    title = R.string.fakeip_filter,
                    value = configuration.dns.fakeIpFilter,
                    enabled = enabled,
                ) { configuration.dns.fakeIpFilter = it },

                choice(
                    id = "fakeip_filter_mode",
                    title = R.string.fakeip_filter_mode,
                    labels = listOf(
                        R.string.dont_modify,
                        R.string.blacklist,
                        R.string.whitelist,
                        R.string.rule,
                    ),
                    values = listOf<ConfigurationOverride.FilterMode?>(
                        null,
                        ConfigurationOverride.FilterMode.BlackList,
                        ConfigurationOverride.FilterMode.WhiteList,
                        ConfigurationOverride.FilterMode.Rule,
                    ),
                    current = configuration.dns.fakeIPFilterMode,
                    enabled = enabled,
                ) { configuration.dns.fakeIPFilterMode = it },

                triState(
                    id = "geoip_fallback",
                    title = R.string.geoip_fallback,
                    value = configuration.dns.fallbackFilter.geoIp,
                    enabled = enabled,
                ) { configuration.dns.fallbackFilter.geoIp = it },

                text(
                    id = "geoip_fallback_code",
                    title = R.string.geoip_fallback_code,
                    value = configuration.dns.fallbackFilter.geoIpCode,
                    empty = R.string.raw_cn,
                    enabled = enabled,
                ) { configuration.dns.fallbackFilter.geoIpCode = it },

                textList(
                    id = "domain_fallback",
                    title = R.string.domain_fallback,
                    value = configuration.dns.fallbackFilter.domain,
                    enabled = enabled,
                ) { configuration.dns.fallbackFilter.domain = it },

                textList(
                    id = "ipcidr_fallback",
                    title = R.string.ipcidr_fallback,
                    value = configuration.dns.fallbackFilter.ipcidr,
                    enabled = enabled,
                ) { configuration.dns.fallbackFilter.ipcidr = it },

                textMap(
                    id = "name_server_policy",
                    title = R.string.name_server_policy,
                    value = configuration.dns.nameserverPolicy,
                    enabled = enabled,
                ) { configuration.dns.nameserverPolicy = it },
            )
        )

        return rows
    }

    // --------------------------------------------------------------- builders

    /**
     * A port number. `null` means "do not modify"; the adapter renders that as
     * `dont_modify` and an empty/zero port as `disabled`, exactly as the DSL
     * did.
     */
    private fun port(id: String, title: Int, apply: (Int?) -> Unit): SettingRow.Action {
        val value = when (id) {
            "http_port" -> configuration.httpPort
            "socks_port" -> configuration.socksPort
            "redirect_port" -> configuration.redirectPort
            "tproxy_port" -> configuration.tproxyPort
            else -> configuration.mixedPort
        }

        val shown = NullableTextAdapter.Port.from(value)

        val summary = when {
            shown == null -> context.getString(R.string.dont_modify)
            shown.isEmpty() -> context.getString(R.string.disabled)
            else -> shown
        }

        return SettingRow.Action(
            id = id,
            title = title,
            summary = summary,
            onClick = {
                launch(Dispatchers.Main) {
                    val text = context.editSettingText(
                        initial = shown,
                        title = context.getText(title),
                        hint = context.getText(title),
                    ) ?: return@launch

                    apply(NullableTextAdapter.Port.to(text))

                    refresh()
                }
            },
        )
    }

    private fun triState(
        id: String,
        title: Int,
        value: Boolean?,
        enabled: Boolean = true,
        beforeApply: (suspend (Boolean?) -> Boolean)? = null,
        apply: (Boolean?) -> Unit,
    ): SettingRow.Action {
        return SettingRow.Action(
            id = id,
            title = title,
            enabled = enabled,
            summary = context.getText(
                when (value) {
                    true -> R.string.enabled
                    false -> R.string.disabled
                    null -> R.string.dont_modify
                }
            ),
            onClick = { anchor ->
                choose(
                    anchor = anchor,
                    labels = listOf(
                        R.string.dont_modify,
                        R.string.enabled,
                        R.string.disabled,
                    ).map { context.getText(it) },
                    values = listOf<Boolean?>(null, true, false),
                    current = value,
                ) { picked ->
                    if (beforeApply != null && !beforeApply(picked))
                        return@choose

                    apply(picked)

                    refresh()
                }
            },
        )
    }

    private fun text(
        id: String,
        title: Int,
        value: String?,
        empty: Int,
        enabled: Boolean = true,
        apply: (String?) -> Unit,
    ): SettingRow.Action {
        return SettingRow.Action(
            id = id,
            title = title,
            enabled = enabled,
            summary = when {
                value == null -> context.getString(R.string.dont_modify)
                value.isEmpty() -> context.getString(empty)
                else -> value
            },
            onClick = {
                launch(Dispatchers.Main) {
                    val text = context.editSettingText(
                        initial = value,
                        title = context.getText(title),
                        hint = context.getText(title),
                    ) ?: return@launch

                    apply(text)

                    refresh()
                }
            },
        )
    }

    private fun textList(
        id: String,
        title: Int,
        value: List<String>?,
        enabled: Boolean = true,
        apply: (List<String>?) -> Unit,
    ): SettingRow.Action {
        return SettingRow.Action(
            id = id,
            title = title,
            enabled = enabled,
            summary = when {
                value == null -> context.getString(R.string.dont_modify)
                value.isEmpty() -> context.getString(R.string.empty)
                else -> context.getString(R.string.format_elements, value.size)
            },
            onClick = {
                launch(Dispatchers.Main) {
                    val edited = context.editSettingTextList(
                        initial = value,
                        title = context.getText(title),
                        adapter = TextAdapter.String,
                        newItemText = {
                            context.editSettingText(
                                initial = "",
                                title = context.getText(title),
                                hint = context.getText(title),
                                allowReset = false,
                                validator = { it.isNotBlank() },
                            )
                        },
                    ) ?: return@launch

                    apply(edited)

                    refresh()
                }
            },
        )
    }

    private fun textMap(
        id: String,
        title: Int,
        value: Map<String, String>?,
        enabled: Boolean = true,
        apply: (Map<String, String>?) -> Unit,
    ): SettingRow.Action {
        return SettingRow.Action(
            id = id,
            title = title,
            enabled = enabled,
            summary = when {
                value == null -> context.getString(R.string.dont_modify)
                value.isEmpty() -> context.getString(R.string.empty)
                else -> context.getString(R.string.format_elements, value.size)
            },
            onClick = {
                launch(Dispatchers.Main) {
                    val edited = context.editSettingTextMap(
                        initial = value,
                        title = context.getText(title),
                        keyAdapter = TextAdapter.String,
                        valueAdapter = TextAdapter.String,
                    ) ?: return@launch

                    apply(edited)

                    refresh()
                }
            },
        )
    }

    private fun <T> choice(
        id: String,
        title: Int,
        labels: List<Int>,
        values: List<T>,
        current: T?,
        enabled: Boolean = true,
        apply: (T?) -> Unit,
    ): SettingRow.Action {
        val texts = labels.map { context.getText(it) }

        val selected = values.indexOf(current).coerceAtLeast(0)

        return SettingRow.Action(
            id = id,
            title = title,
            enabled = enabled,
            summary = texts.getOrNull(selected) ?: context.getString(R.string.dont_modify),
            onClick = { anchor ->
                choose(anchor, texts, values, values.getOrNull(selected)) { picked ->
                    apply(picked)

                    refresh()
                }
            },
        )
    }

    private fun <T> choose(
        anchor: View,
        labels: List<CharSequence>,
        values: List<T>,
        current: T?,
        apply: suspend (T?) -> Unit,
    ) {
        val selected = values.indexOf(current).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(anchor, labels, selected)

            if (index != selected)
                apply(values[index])
        }
    }
}
