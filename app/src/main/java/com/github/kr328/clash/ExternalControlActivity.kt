package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.design.MainDesign
import com.github.kr328.clash.design.settings.SettingsStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService
import com.github.kr328.clash.util.withProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.URI
import java.util.*
import com.github.kr328.clash.design.R

class ExternalControlActivity : Activity(), CoroutineScope by MainScope() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val settings = SettingsStore(this)
        // The app's own dynamic shortcuts carry a private token so they keep
        // working when external control is disabled. Other apps cannot read it.
        val trustedShortcut = settings.externalControlToken.isNotBlank() &&
                intent.getStringExtra(EXTRA_CONTROL_TOKEN) == settings.externalControlToken

        if (!settings.externalControlEnabled && !trustedShortcut) {
            Toast.makeText(this, R.string.external_control_disabled, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        when(intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return finish()
                val url = uri.getQueryParameter("url") ?: return finish()

                launch {
                    if (!isSafeProfileUrl(url)) {
                        Toast.makeText(
                            this@ExternalControlActivity,
                            R.string.external_control_invalid_url,
                            Toast.LENGTH_LONG,
                        ).show()
                        finish()
                        return@launch
                    }

                    val uuid = withProfile {
                        val type = when (uri.getQueryParameter("type")?.lowercase(Locale.getDefault())) {
                            "url" -> Profile.Type.Url
                            "file" -> Profile.Type.File
                            else -> Profile.Type.Url
                        }
                        val rawName = uri.getQueryParameter("name") ?: getString(R.string.new_profile)
                        val name = rawName.trim()
                            .ifEmpty { getString(R.string.new_profile) }
                            .take(128)

                        val parsedInterval = uri.getQueryParameter("update-interval")?.toLongOrNull() ?: 0L
                        val updateInterval = if (parsedInterval > 0) parsedInterval.coerceAtLeast(15L) else 0L
                        val intervalMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(updateInterval)

                        create(type, name).also {
                            patch(it, name, url, intervalMs, null)
                        }
                    }
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                    finish()
                }
                return
            }

            Intents.ACTION_TOGGLE_CLASH -> {
                if (isClashRunning()) {
                    stopClash()
                } else {
                    startClash()
                }
            }
            
            Intents.ACTION_START_CLASH -> {
                if (isClashRunning()) {
                    Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
                } else {
                    startClash()
                }
            }
            
            Intents.ACTION_STOP_CLASH -> {
                stopClash()
            }
        }
        return finish()
    }

    /**
     * Rejects profile URLs that cannot safely be fetched from a deep link:
     * non-HTTP(S)/content schemes and HTTP(S) hosts that resolve to the local
     * device or the local network. Called from the IO-backed coroutine so the
     * DNS lookup never blocks the UI thread.
     */
    private fun isSafeProfileUrl(url: String): Boolean {
        return try {
            val parsed = URI(url)
            val scheme = parsed.scheme?.lowercase(Locale.ROOT) ?: return false

            when (scheme) {
                "content" -> return true
                "http", "https" -> Unit
                else -> return false
            }

            val host = parsed.host
                ?.lowercase(Locale.ROOT)
                ?.removePrefix("[")
                ?.removeSuffix("]")
                ?.removeSuffix(".")
                ?: return false

            if (host.isEmpty() || host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local"))
                return false

            val addresses = InetAddress.getAllByName(host)

            if (addresses.isEmpty())
                return false

            addresses.none {
                it.isLoopbackAddress ||
                    it.isLinkLocalAddress ||
                    it.isSiteLocalAddress ||
                    it.isAnyLocalAddress
            }
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        /** Private token used by the app's own dynamic shortcuts. */
        const val EXTRA_CONTROL_TOKEN = "com.github.kr328.clash.extra.CONTROL_TOKEN"
    }

    private fun isClashRunning(): Boolean {
        return StatusClient(this).currentProfile() != null
    }

    private fun startClash() {
//        if (currentProfile == null) {
//            Toast.makeText(this, R.string.no_profile_selected, Toast.LENGTH_LONG).show()
//            return
//        }
        val vpnRequest = startClashService()
        if (vpnRequest != null) {
            Toast.makeText(this, R.string.unable_to_start_vpn, Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(this, R.string.external_control_started, Toast.LENGTH_LONG).show()
    }

    private fun stopClash() {
        stopClashService()
        Toast.makeText(this, R.string.external_control_stopped, Toast.LENGTH_LONG).show()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
