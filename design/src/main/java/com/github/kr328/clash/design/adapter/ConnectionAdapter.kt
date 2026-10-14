package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterConnectionBinding
import com.github.kr328.clash.design.model.ConnectionItemDisplay
import com.github.kr328.clash.design.util.layoutInflater

class ConnectionAdapter(
    private val context: Context,
    private val close: (String) -> Unit,
) : RecyclerView.Adapter<ConnectionAdapter.Holder>() {
    class Holder(val binding: AdapterConnectionBinding) : RecyclerView.ViewHolder(binding.root)

    var connections: List<ConnectionItemDisplay> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterConnectionBinding
                .inflate(context.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.connection = connections[position]
    }

    override fun getItemCount(): Int {
        return connections.size
    }
}
