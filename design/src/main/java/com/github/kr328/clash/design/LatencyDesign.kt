package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.adapter.ServiceStatusAdapter
import com.github.kr328.clash.design.databinding.DesignLatencyBinding
import com.github.kr328.clash.design.model.ServiceAvailability
import com.github.kr328.clash.design.model.ServiceStatus
import com.github.kr328.clash.design.model.ServiceStatusColors
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
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

    private val adapter = ServiceStatusAdapter(context)

    private val colors = ServiceStatusColors(
        good = context.resolveThemedColor(R.attr.delayGood),
        medium = context.resolveThemedColor(R.attr.delayMedium),
        error = context.resolveThemedColor(com.google.android.material.R.attr.colorError),
        neutral = context.resolveThemedColor(com.google.android.material.R.attr.colorOnSurfaceVariant),
        checkingText = context.getString(R.string.status_checking),
        okText = context.getString(R.string.status_ok),
        restrictedText = context.getString(R.string.status_restricted),
        blockedText = context.getString(R.string.status_blocked),
        timeoutText = context.getString(R.string.status_timeout),
        failedText = context.getString(R.string.status_failed),
    )

    private val statuses = services.map {
        ServiceStatus(it, colors)
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)

        showChecking()
    }

    fun requestRefresh() {
        requests.trySend(Request.Refresh)
    }

    private fun showChecking() {
        statuses.forEach {
            it.availability = ServiceAvailability.CHECKING
            it.latencyMs = -1
        }

        refresh()
    }

    fun resetAll() {
        showChecking()
    }

    suspend fun update(index: Int, availability: ServiceAvailability, latencyMs: Long) {
        withContext(Dispatchers.Main) {
            statuses[index].let {
                it.availability = availability
                it.latencyMs = latencyMs
            }

            refresh()
        }
    }

    private fun refresh() {
        adapter.services = statuses.toList()
        adapter.notifyDataSetChanged()
    }
}
