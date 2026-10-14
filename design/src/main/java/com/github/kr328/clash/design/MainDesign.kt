package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.github.kr328.clash.core.model.ExternalIP
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.core.util.trafficDownload
import com.github.kr328.clash.core.util.trafficDownloadValue
import com.github.kr328.clash.core.util.trafficTotal
import com.github.kr328.clash.core.util.trafficUpload
import com.github.kr328.clash.core.util.trafficUploadValue
import com.github.kr328.clash.design.databinding.DesignAboutBinding
import com.github.kr328.clash.design.databinding.DesignMainBinding
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.github.kr328.clash.design.util.toBytesString
import com.github.kr328.clash.design.util.toDisplayString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainDesign(context: Context) : Design<MainDesign.Request>(context) {
    enum class Request {
        ToggleStatus,
        ToggleTun,
        OpenProxy,
        OpenProfiles,
        OpenProviders,
        OpenLogs,
        OpenSettings,
        OpenHelp,
        OpenAbout,
        RefreshIP,
    }

    private val binding = DesignMainBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val attachedPages = mutableListOf<View>()

    init {
        binding.self = this

        binding.colorClashStarted = context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
        binding.colorClashStopped = context.resolveThemedColor(R.attr.colorClashStopped)

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_dashboard -> showPage(0)
                R.id.page_profiles -> showPage(1)
                R.id.page_settings -> showPage(2)
            }
            true
        }
    }

    fun attachPage(view: View, title: String) {
        binding.pagesContainer.addView(
            view,
            binding.pagesContainer.childCount,
            android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        attachedPages.add(view)

        view.visibility = View.GONE

        view.findViewById<android.widget.TextView>(R.id.activity_bar_title_view)?.text = title

        // embedded pages navigate back to the dashboard instead of finishing the activity
        view.findViewById<View>(R.id.activity_bar_close_view)?.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.page_dashboard
        }
    }

    private fun showPage(index: Int) {
        binding.dashboardPage.visibility = if (index == 0) View.VISIBLE else View.GONE

        attachedPages.forEachIndexed { i, page ->
            page.visibility = if (index == i + 1) View.VISIBLE else View.GONE
        }
    }

    fun showProfilesPage() {
        binding.bottomNavigation.selectedItemId = R.id.page_profiles
    }

    fun showSettingsPage() {
        binding.bottomNavigation.selectedItemId = R.id.page_settings
    }

    fun navigateBackToDashboard(): Boolean {
        if (binding.bottomNavigation.selectedItemId == R.id.page_dashboard)
            return false

        binding.bottomNavigation.selectedItemId = R.id.page_dashboard

        return true
    }

    suspend fun setProfileName(name: String?) {
        withContext(Dispatchers.Main) {
            binding.profileName = name
        }
    }

    suspend fun setProfileSummary(summary: String?) {
        withContext(Dispatchers.Main) {
            binding.profileSummary = summary
        }
    }

    suspend fun setClashRunning(running: Boolean) {
        withContext(Dispatchers.Main) {
            binding.clashRunning = running
        }
    }

    suspend fun setTunEnabled(enabled: Boolean) {
        withContext(Dispatchers.Main) {
            binding.tunEnabled = enabled
        }
    }

    suspend fun setForwarded(value: Long) {
        withContext(Dispatchers.Main) {
            binding.forwarded = value.trafficTotal()
        }
    }

    suspend fun setNetworkSpeed(traffic: com.github.kr328.clash.core.model.Traffic) {
        withContext(Dispatchers.Main) {
            binding.speedUp = traffic.trafficUpload() + "/s"
            binding.speedDown = traffic.trafficDownload() + "/s"

            binding.trafficChart.push(traffic.trafficUploadValue(), traffic.trafficDownloadValue())
        }
    }

    suspend fun setMemoryUsage(value: Long) {
        withContext(Dispatchers.Main) {
            binding.memoryUsage = value.toBytesString()
        }
    }

    suspend fun setIPInfo(exit: ExternalIP?, direct: ExternalIP?) {
        withContext(Dispatchers.Main) {
            binding.ipStatus = null

            binding.exitIPAddr = exit?.ip
            binding.exitIPDisplay = exit?.toDisplayString()
            binding.exitIPISP = exit?.isp?.takeIf { it.isNotBlank() }

            binding.directIPAddr = direct?.ip
            binding.directIPDisplay = direct?.toDisplayString()
        }
    }

    suspend fun setIPInfoLoading() {
        withContext(Dispatchers.Main) {
            binding.ipStatus = context.getString(R.string.ip_querying)
        }
    }

    suspend fun setIPInfoFailed() {
        withContext(Dispatchers.Main) {
            binding.ipStatus = context.getString(R.string.ip_query_failed)
        }
    }

    suspend fun setMode(mode: TunnelState.Mode) {
        withContext(Dispatchers.Main) {
            binding.mode = when (mode) {
                TunnelState.Mode.Direct -> context.getString(R.string.direct_mode)
                TunnelState.Mode.Global -> context.getString(R.string.global_mode)
                TunnelState.Mode.Rule -> context.getString(R.string.rule_mode)
                else -> context.getString(R.string.rule_mode)
            }
        }
    }

    suspend fun setHasProviders(has: Boolean) {
        withContext(Dispatchers.Main) {
            binding.hasProviders = has
        }
    }

    suspend fun showAbout(versionName: String) {
        withContext(Dispatchers.Main) {
            val binding = DesignAboutBinding.inflate(context.layoutInflater).apply {
                this.versionName = versionName
            }

            AlertDialog.Builder(context)
                .setView(binding.root)
                .show()
        }
    }

    fun request(request: Request) {
        requests.trySend(request)
    }
}
