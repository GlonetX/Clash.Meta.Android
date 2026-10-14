package com.github.kr328.clash.design

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.View
import androidx.core.content.getSystemService
import androidx.core.widget.doOnTextChanged
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.ConfigurationOverride
import com.github.kr328.clash.design.databinding.DesignSettingsMetaFeatureBinding
import com.github.kr328.clash.design.databinding.DialogAgeKeyHelperBinding
import com.github.kr328.clash.design.settings.SettingGroup
import com.github.kr328.clash.design.settings.SettingItem
import com.github.kr328.clash.design.settings.SettingPage
import com.github.kr328.clash.design.settings.SettingRow
import com.github.kr328.clash.design.settings.TextAdapter
import com.github.kr328.clash.design.settings.editSettingTextList
import com.github.kr328.clash.design.settings.selectSetting
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The "Meta Features" page: age key helpers plus the sniffer / geodata
 * overrides.
 *
 * Behaviour is unchanged from the DSL version it replaces.
 */
class MetaFeatureSettingsDesign(
    context: Context,
    private val configuration: ConfigurationOverride,
) : Design<MetaFeatureSettingsDesign.Request>(context) {
    enum class Request {
        ResetOverride, ImportGeoIp, ImportGeoSite, ImportCountry, ImportASN
    }

    private val binding = DesignSettingsMetaFeatureBinding
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
        SettingGroup(
            R.string.age_key_category,
            listOf(
                SettingRow.Action(
                    id = "age_key_x25519",
                    title = R.string.age_key_type_x25519,
                    summary = context.getText(R.string.age_key_generate_summary),
                    onClick = { requestAgeKeyHelper(hybrid = false) },
                ),
                SettingRow.Action(
                    id = "age_key_hybrid",
                    title = R.string.age_key_type_hybrid,
                    summary = context.getText(R.string.age_key_generate_summary),
                    onClick = { requestAgeKeyHelper(hybrid = true) },
                ),
            ),
        ),

        SettingGroup(R.string.settings, metaRows()),

        SettingGroup(R.string.sniffer_setting, snifferRows()),

        SettingGroup(
            R.string.geox_files,
            listOf(
                SettingRow.Action(
                    id = "import_geoip",
                    title = R.string.import_geoip_file,
                    summary = context.getText(R.string.press_to_import),
                    icon = R.drawable.ic_baseline_add,
                    onClick = { requests.trySend(Request.ImportGeoIp) },
                ),
                SettingRow.Action(
                    id = "import_geosite",
                    title = R.string.import_geosite_file,
                    summary = context.getText(R.string.press_to_import),
                    icon = R.drawable.ic_baseline_add,
                    onClick = { requests.trySend(Request.ImportGeoSite) },
                ),
                SettingRow.Action(
                    id = "import_country",
                    title = R.string.import_country_file,
                    summary = context.getText(R.string.press_to_import),
                    icon = R.drawable.ic_baseline_add,
                    onClick = { requests.trySend(Request.ImportCountry) },
                ),
                SettingRow.Action(
                    id = "import_asn",
                    title = R.string.import_asn_file,
                    summary = context.getText(R.string.press_to_import),
                    icon = R.drawable.ic_baseline_add,
                    onClick = { requests.trySend(Request.ImportASN) },
                ),
            ),
        ),
    )

    private fun metaRows(): List<SettingRow> = listOf(
        triState(
            id = "unified_delay",
            title = R.string.unified_delay,
            value = configuration.unifiedDelay,
        ) { configuration.unifiedDelay = it },

        triState(
            id = "geodata_mode",
            title = R.string.geodata_mode,
            value = configuration.geodataMode,
        ) { configuration.geodataMode = it },

        triState(
            id = "tcp_concurrent",
            title = R.string.tcp_concurrent,
            value = configuration.tcpConcurrent,
        ) { configuration.tcpConcurrent = it },

        choice(
            id = "find_process_mode",
            title = R.string.find_process_mode,
            labels = listOf(
                R.string.dont_modify,
                R.string.off,
                R.string.strict,
                R.string.always,
            ),
            values = listOf<ConfigurationOverride.FindProcessMode?>(
                null,
                ConfigurationOverride.FindProcessMode.Off,
                ConfigurationOverride.FindProcessMode.Strict,
                ConfigurationOverride.FindProcessMode.Always,
            ),
            current = configuration.findProcessMode,
        ) { configuration.findProcessMode = it },
    )

    private fun snifferRows(): List<SettingRow> {
        val enabled = configuration.sniffer.enable != false

        val rows = mutableListOf(
            SettingRow.Action(
                id = "sniffer_strategy",
                title = R.string.strategy,
                summary = context.getText(
                    when (configuration.sniffer.enable) {
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
                        current = configuration.sniffer.enable,
                    ) { picked ->
                        configuration.sniffer.enable = picked

                        refresh()
                    }
                },
            )
        )

        rows.addAll(
            listOf(
                textList(
                    id = "sniff_http_ports",
                    title = R.string.sniff_http_ports,
                    value = configuration.sniffer.sniff.http.ports,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.http.ports = it },

                triState(
                    id = "sniff_http_override_destination",
                    title = R.string.sniff_http_override_destination,
                    value = configuration.sniffer.sniff.http.overrideDestination,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.http.overrideDestination = it },

                textList(
                    id = "sniff_tls_ports",
                    title = R.string.sniff_tls_ports,
                    value = configuration.sniffer.sniff.tls.ports,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.tls.ports = it },

                triState(
                    id = "sniff_tls_override_destination",
                    title = R.string.sniff_tls_override_destination,
                    value = configuration.sniffer.sniff.tls.overrideDestination,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.tls.overrideDestination = it },

                textList(
                    id = "sniff_quic_ports",
                    title = R.string.sniff_quic_ports,
                    value = configuration.sniffer.sniff.quic.ports,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.quic.ports = it },

                triState(
                    id = "sniff_quic_override_destination",
                    title = R.string.sniff_quic_override_destination,
                    value = configuration.sniffer.sniff.quic.overrideDestination,
                    enabled = enabled,
                ) { configuration.sniffer.sniff.quic.overrideDestination = it },

                triState(
                    id = "force_dns_mapping",
                    title = R.string.force_dns_mapping,
                    value = configuration.sniffer.forceDnsMapping,
                    enabled = enabled,
                ) { configuration.sniffer.forceDnsMapping = it },

                triState(
                    id = "parse_pure_ip",
                    title = R.string.parse_pure_ip,
                    value = configuration.sniffer.parsePureIp,
                    enabled = enabled,
                ) { configuration.sniffer.parsePureIp = it },

                triState(
                    id = "override_destination",
                    title = R.string.override_destination,
                    value = configuration.sniffer.overrideDestination,
                    enabled = enabled,
                ) { configuration.sniffer.overrideDestination = it },

                textList(
                    id = "force_domain",
                    title = R.string.force_domain,
                    value = configuration.sniffer.forceDomain,
                    enabled = enabled,
                ) { configuration.sniffer.forceDomain = it },

                textList(
                    id = "skip_domain",
                    title = R.string.skip_domain,
                    value = configuration.sniffer.skipDomain,
                    enabled = enabled,
                ) { configuration.sniffer.skipDomain = it },

                textList(
                    id = "skip_src_address",
                    title = R.string.skip_src_address,
                    value = configuration.sniffer.skipSrcAddress,
                    enabled = enabled,
                ) { configuration.sniffer.skipSrcAddress = it },

                textList(
                    id = "skip_dst_address",
                    title = R.string.skip_dst_address,
                    value = configuration.sniffer.skipDstAddress,
                    enabled = enabled,
                ) { configuration.sniffer.skipDstAddress = it },
            )
        )

        return rows
    }

    // --------------------------------------------------------------- builders

    private fun triState(
        id: String,
        title: Int,
        value: Boolean?,
        enabled: Boolean = true,
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
                    apply(picked)

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
            summary = texts.getOrElse(selected) ?: context.getString(R.string.dont_modify),
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
        apply: (T?) -> Unit,
    ) {
        val selected = values.indexOf(current).coerceAtLeast(0)

        launch(Dispatchers.Main) {
            val index = context.selectSetting(anchor, labels, selected)

            if (index != selected)
                apply(values[index])
        }
    }

    // ------------------------------------------------------------ age key

    private fun requestAgeKeyHelper(hybrid: Boolean) {
        launch(Dispatchers.Main) {
            val binding = DialogAgeKeyHelperBinding
                .inflate(context.layoutInflater, context.root, false)

            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(if (hybrid) R.string.age_key_type_hybrid else R.string.age_key_type_x25519)
                .setView(binding.root)
                .create()

            fun copy(label: String, value: String) {
                if (value.isBlank())
                    return

                val data = ClipData.newPlainText(label, value)
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(data)

                launch { showToast(R.string.copied, ToastDuration.Short) }
            }

            fun patchSecretKeyState() {
                val secretKey = binding.secretKeyView.text?.toString() ?: ""
                val valid = secretKey.isBlank() || Clash.veritySecretKeys(secretKey)

                binding.secretKeyLayout.error = if (valid)
                    null
                else
                    context.getText(R.string.age_secret_key_error)
            }

            fun patchPublicKeyState() {
                val publicKey = binding.publicKeyView.text?.toString() ?: ""
                val valid = publicKey.isBlank() || Clash.verityPublicKeys(publicKey)

                binding.publicKeyLayout.error = if (valid)
                    null
                else
                    context.getText(R.string.age_public_key_error)
            }

            dialog.setOnShowListener {
                binding.secretKeyView.doOnTextChanged { _, _, _, _ -> patchSecretKeyState() }
                binding.publicKeyView.doOnTextChanged { _, _, _, _ -> patchPublicKeyState() }

                binding.generateView.setOnClickListener {
                    val keyPair = if (hybrid) {
                        Clash.genHybridKeyPair()
                    } else {
                        Clash.genX25519KeyPair()
                    }

                    binding.secretKeyView.setText(keyPair.secretKey)
                    binding.publicKeyView.setText(keyPair.publicKey)
                }

                binding.toPublicKeyView.setOnClickListener {
                    val publicKey = Clash.toPublicKeys(
                        binding.secretKeyView.text?.toString() ?: ""
                    ).firstOrNull() ?: ""

                    binding.publicKeyView.setText(publicKey)
                }

                binding.copySecretKeyView.setOnClickListener {
                    copy("age_secret_key", binding.secretKeyView.text?.toString() ?: "")
                }

                binding.copyPublicKeyView.setOnClickListener {
                    copy("age_public_key", binding.publicKeyView.text?.toString() ?: "")
                }
            }

            dialog.show()
        }
    }
}
