package com.github.kr328.clash.design.adapter

import android.content.Context
import android.content.res.ColorStateList
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.databinding.AdapterProfileBinding
import com.github.kr328.clash.design.model.ProfilePageState
import com.github.kr328.clash.design.ui.ObservableCurrentTime
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.service.model.Profile

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile) -> Unit,
    private val onUpdateClicked: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root)

    private val currentTime = ObservableCurrentTime()

    private val groupBg by lazy {
        ColorStateList.valueOf(context.resolveThemedColor(com.github.kr328.clash.design.R.attr.colorGroupBackground))
    }
    private val primaryColor by lazy {
        context.resolveThemedColor(com.google.android.material.R.attr.colorPrimary)
    }
    private val transparent by lazy {
        ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
    }

    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()

    fun updateElapsed() {
        currentTime.update()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterProfileBinding
                .inflate(context.layoutInflater, parent, false)
                .also { it.currentTime = currentTime }
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = profiles[position]
        val binding = holder.binding

        binding.profile = current

        // FlClash-style selected state: tonal background + primary stroke
        binding.rootView.apply {
            strokeColor = if (current.active) ColorStateList.valueOf(primaryColor) else transparent
            strokeWidth = if (current.active) 2 else 0
            setCardBackgroundColor(groupBg)
            rippleColor = ColorStateList.valueOf(primaryColor)
        }

        binding.setClicked {
            onClicked(current)
        }
        binding.setMenu {
            onMenuClicked(current)
        }
        binding.setUpdate {
            onUpdateClicked(current)
        }
    }

    override fun getItemCount(): Int {
        return profiles.size
    }
}