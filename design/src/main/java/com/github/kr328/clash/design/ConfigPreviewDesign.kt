package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import com.github.kr328.clash.design.databinding.DesignConfigPreviewBinding
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.root
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ConfigPreviewDesign(context: Context) : Design<ConfigPreviewDesign.Request>(context) {
    enum class Request

    private val binding = DesignConfigPreviewBinding
        .inflate(context.layoutInflater, context.root, false)

    override val root: View
        get() = binding.root

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)
    }

    suspend fun update(content: String?) {
        withContext(Dispatchers.Main) {
            binding.configText.text = content
                ?: context.getString(R.string.final_config_failed)
        }
    }
}
