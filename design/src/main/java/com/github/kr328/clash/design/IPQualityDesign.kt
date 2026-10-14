package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.core.model.IPQuality
import com.github.kr328.clash.design.adapter.KvAdapter
import com.github.kr328.clash.design.databinding.DesignIpQualityBinding
import com.github.kr328.clash.design.model.KvItem
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class IPQualityDesign(context: Context) : Design<IPQualityDesign.Request>(context) {
    enum class Request {
        Refresh,
    }

    private val binding = DesignIpQualityBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = KvAdapter(context)

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)

        adapter.submit(
            listOf(
                KvAdapter.Entry.Header(context.getString(R.string.ip_quality)),
                KvAdapter.Entry.Item(KvItem(context.getString(R.string.ip_querying), null)),
            ),
        )
    }

    fun requestRefresh() {
        requests.trySend(Request.Refresh)
    }

    suspend fun update(quality: IPQuality?) {
        withContext(Dispatchers.Main) {
            val entries = mutableListOf<KvAdapter.Entry>()

            if (quality == null) {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.ip_query_failed), null)))
                adapter.submit(entries)
                return@withContext
            }

            entries.add(KvAdapter.Entry.Header(context.getString(R.string.ip_quality)))

            quality.scamalyticsScore?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.risk_score)} (Scamalytics)", "$it / 100 ${quality.scamalyticsRisk ?: ""}")))
            }
            quality.ipqsFraudScore?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.risk_score)} (IPQualityScore)", "$it / 100")))
            }
            quality.ipqsProxy?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.is_proxy), it)))
            }
            quality.ipqsVpn?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("VPN", it)))
            }
            quality.abuseConfidence?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.abuse_score)} (AbuseIPDB)", "$it / 100")))
            }
            quality.abuseUsageType?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.usage_type), it)))
            }
            quality.ip2locationProxy?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.proxy_type)} (IP2Location)", it)))
            }
            quality.ip2locationUsage?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.usage_type)} (IP2Location)", it)))
            }

            adapter.submit(entries)
        }
    }
}
