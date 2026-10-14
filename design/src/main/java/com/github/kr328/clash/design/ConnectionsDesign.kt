package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.core.model.ConnectionItem
import com.github.kr328.clash.design.adapter.ConnectionAdapter
import com.github.kr328.clash.design.databinding.DesignConnectionsBinding
import com.github.kr328.clash.design.model.toDisplay
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.applyLinearAdapter
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConnectionsDesign(context: Context) : Design<ConnectionsDesign.Request>(context) {
    sealed class Request {
        object Refresh : Request()

        data class Close(val id: String) : Request()
        data class CloseAll(val ids: List<String>) : Request()
    }

    private val binding = DesignConnectionsBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    private val adapter = ConnectionAdapter(context) {
        requests.trySend(Request.Close(it))
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.bindAppBarElevation(binding.activityBarLayout)
        binding.mainList.recyclerList.applyLinearAdapter(context, adapter)
    }

    fun requestCloseAll() {
        val ids = adapter.connections.map { it.id }

        if (ids.isNotEmpty())
            requests.trySend(Request.CloseAll(ids))
    }

    suspend fun update(connections: List<ConnectionItem>) {
        val now = System.currentTimeMillis() / 1000

        val display = withContext(Dispatchers.Default) {
            connections
                .map { it.toDisplay(now) { id -> requests.trySend(Request.Close(id)) } }
                .sortedByDescending { it.download }
        }

        withContext(Dispatchers.Main) {
            adapter.connections = display

            binding.countView.text =
                context.getString(R.string.active_connections, display.size)

            binding.emptyView.visibility =
                if (display.isEmpty()) View.VISIBLE else View.GONE

            adapter.notifyDataSetChanged()
        }
    }
}
