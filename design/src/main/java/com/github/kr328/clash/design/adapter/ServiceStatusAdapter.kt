package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterServiceStatusBinding
import com.github.kr328.clash.design.model.ServiceStatus
import com.github.kr328.clash.design.util.layoutInflater

class ServiceStatusAdapter(
    private val context: Context,
) : RecyclerView.Adapter<ServiceStatusAdapter.Holder>() {
    class Holder(val binding: AdapterServiceStatusBinding) : RecyclerView.ViewHolder(binding.root)

    var services: List<ServiceStatus> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterServiceStatusBinding
                .inflate(context.layoutInflater, parent, false)
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.binding.service = services[position]
    }

    override fun getItemCount(): Int {
        return services.size
    }
}
