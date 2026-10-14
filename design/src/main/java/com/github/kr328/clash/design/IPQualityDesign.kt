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
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
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

            if (quality.ip.isNotBlank()) {
                entries.add(KvAdapter.Entry.Item(KvItem("IP", quality.ip)))
            }

            quality.asn?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("ASN", it)))
            }

            quality.ipApiISP?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("ISP", it)))
            }

            quality.scamalyticsScore?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.risk_score)} (Scamalytics)", "$it / 100 ${quality.scamalyticsRisk ?: ""}", highlight = true)))
            }

            quality.proxyCheckRisk?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.risk_score)} (proxycheck.io)", "$it / 100", highlight = true)))
            }

            quality.proxyCheckProxy?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.is_proxy), localizeBool(it))))
            }

            quality.proxyCheckType?.takeIf { it.isNotBlank() && it != "-" }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.proxy_type), it)))
            }

            quality.ipApiProxy?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem("${context.getString(R.string.is_proxy)} (ip-api)", localizeBool(it))))
            }

            quality.ipApiHosting?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.datacenter), localizeBool(it))))
            }

            quality.ipApiMobile?.takeIf { it.isNotBlank() }?.let {
                entries.add(KvAdapter.Entry.Item(KvItem(context.getString(R.string.mobile_network), localizeBool(it))))
            }

            adapter.submit(entries)
        }
    }

    private fun localizeBool(value: String): String {
        return when (value.lowercase()) {
            "true", "yes" -> context.getString(R.string.value_yes)
            "false", "no" -> context.getString(R.string.value_no)
            else -> value
        }
    }
}
