package com.github.kr328.clash.design.adapter

import android.content.Context
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.common.compat.getDrawableCompat
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.LogFile
import com.github.kr328.clash.design.util.format
import com.github.kr328.clash.design.view.AppRow

class LogFileAdapter(
    private val context: Context,
    private val open: (LogFile) -> Unit,
) : RecyclerView.Adapter<LogFileAdapter.Holder>() {
    class Holder(val row: AppRow) : RecyclerView.ViewHolder(row)

    private val fileIcon = context.getDrawableCompat(R.drawable.ic_outline_article)

    var logs: List<LogFile> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(AppRow(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            icon = fileIcon
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = logs[position]

        holder.row.text = current.fileName
        holder.row.subtext = current.date.format(context)
        holder.row.setOnClickListener {
            open(current)
        }
    }

    override fun getItemCount(): Int {
        return logs.size
    }
}
