package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.adapter.KvAdapter
import com.github.kr328.clash.design.databinding.DesignLatencyBinding
import com.github.kr328.clash.design.model.KvItem
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LatencyDesign(
    context: Context,
    private val services: List<String>,
) : Design<LatencyDesign.Request>(context) {
    enum class Request {
        Refresh,
    }

    private val binding = DesignLatencyBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = KvAdapter(context)
    private val results = arrayOfNulls<String>(services.size)

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)

        showLoading()
    }

    fun requestRefresh() {
        requests.trySend(Request.Refresh)
    }

    private fun showLoading() {
        results.fill(null)

        adapter.submit(
            services.map { KvAdapter.Entry.Item(KvItem(it, context.getString(R.string.ip_querying))) },
        )
    }

    fun resetAll() {
        showLoading()
    }

    suspend fun update(index: Int, latencyMs: Long) {
        withContext(Dispatchers.Main) {
            results[index] = if (latencyMs >= 0) "$latencyMs ms" else context.getString(R.string.timeout)

            adapter.submit(
                services.mapIndexed { i, name ->
                    KvAdapter.Entry.Item(KvItem(name, results[i] ?: context.getString(R.string.ip_querying)))
                },
            )
        }
    }
}
