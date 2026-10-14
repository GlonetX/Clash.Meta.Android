package com.github.kr328.clash.design.view

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dashboard.ServiceProbe
import com.github.kr328.clash.design.databinding.AdapterDashboardServicePageBinding
import com.github.kr328.clash.design.model.ServiceAvailability
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * The streaming service probes as one swipeable card.
 *
 * The probe set itself is unchanged — it already exists in `LatencyActivity`,
 * which owns the service list, and is deliberately not duplicated or widened
 * here. This view only renders the results it is handed: one page per
 * service, with the status dot cut out of a rounded badge so the colour reads
 * at a glance and keeps its shape on a light background.
 *
 * Built on ViewPager2 (the pager ProxyDesign already uses); the legacy
 * `androidx.viewpager` artifact is not a declared dependency of this module.
 */
class ServiceStatusPagerView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : FrameLayout(context, attributeSet, defStyleAttr) {

    private val pageAdapter = PageAdapter()

    private val pager = ViewPager2(context)

    private val indicator = LinearLayout(context)

    private var services: List<ServiceProbe> = emptyList()

    private val dotSize: Int
    private val dotMargin: Int

    private val activeColor: Int
    private val inactiveColor: Int

    init {
        dotSize = (6 * resources.displayMetrics.density).toInt()
        dotMargin = (3 * resources.displayMetrics.density).toInt()

        activeColor = context.resolveThemedColor(MaterialR.attr.colorPrimary)
        inactiveColor = context.resolveThemedColor(MaterialR.attr.colorOutline)

        indicator.orientation = LinearLayout.HORIZONTAL
        indicator.gravity = Gravity.CENTER

        addView(pager, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(
            indicator,
            LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ),
        )

        indicator.visibility = GONE

        pager.adapter = pageAdapter

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                highlightPage()
            }
        })
    }

    fun setServices(services: List<ServiceProbe>) {
        if (services == this.services)
            return

        val old = this.services
        this.services = services

        pageAdapter.services = services

        if (old.size == services.size) {
            // Content-only change (a latency or availability moved): rebind
            // the visible pages without a structural change, which would
            // reset the pager's position.
            if (services.isNotEmpty())
                pageAdapter.notifyItemRangeChanged(0, services.size)
        } else {
            pageAdapter.notifyDataSetChanged()
            pager.currentItem = 0
        }

        rebuildIndicator()
    }

    private fun rebuildIndicator() {
        indicator.removeAllViews()

        if (services.size < 2) {
            indicator.visibility = GONE
            return
        }

        indicator.visibility = VISIBLE

        services.indices.forEach {
            val dot = View(context)

            dot.layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                setMargins(dotMargin, 0, dotMargin, 0)
            }

            dot.background = ContextCompat.getDrawable(context, R.drawable.bg_dashboard_status_dot)

            indicator.addView(dot)
        }

        highlightPage()
    }

    private fun highlightPage() {
        for (i in 0 until indicator.childCount) {
            indicator.getChildAt(i).backgroundTintList = ColorStateList.valueOf(
                if (i == pager.currentItem) activeColor else inactiveColor
            )
        }
    }

    private inner class PageAdapter :
        RecyclerView.Adapter<PageAdapter.PageHolder>() {

        var services: List<ServiceProbe> = emptyList()

        override fun getItemCount(): Int = services.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder {
            return PageHolder(
                AdapterDashboardServicePageBinding.inflate(
                    context.layoutInflater,
                    parent,
                    false
                )
            )
        }

        override fun onBindViewHolder(holder: PageHolder, position: Int) {
            holder.bind(services[position])
        }

        inner class PageHolder(
            private val binding: AdapterDashboardServicePageBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(probe: ServiceProbe) {
                val color = statusColor(probe.availability)

                binding.serviceName.text = probe.name
                binding.serviceStatus.text = statusText(probe.availability)
                binding.serviceStatus.setTextColor(color)
                binding.serviceLatency.text = if (probe.latencyMillis >= 0) {
                    context.getString(R.string.dashboard_probe_latency, probe.latencyMillis)
                } else {
                    ""
                }
                binding.statusDot.backgroundTintList = ColorStateList.valueOf(color)
            }
        }

        private fun statusText(availability: ServiceAvailability): String = context.getString(
            when (availability) {
                ServiceAvailability.OK -> R.string.dashboard_status_ok
                ServiceAvailability.RESTRICTED -> R.string.dashboard_status_restricted
                ServiceAvailability.BLOCKED -> R.string.dashboard_status_blocked
                ServiceAvailability.TIMEOUT -> R.string.dashboard_status_timeout
                ServiceAvailability.FAILED -> R.string.dashboard_status_failed
                ServiceAvailability.CHECKING -> R.string.dashboard_status_checking
            }
        )

        private fun statusColor(availability: ServiceAvailability): Int =
            context.resolveThemedColor(
                when (availability) {
                    ServiceAvailability.OK -> R.attr.delayGood
                    ServiceAvailability.RESTRICTED, ServiceAvailability.TIMEOUT -> R.attr.delayMedium
                    ServiceAvailability.BLOCKED, ServiceAvailability.FAILED -> MaterialR.attr.colorError
                    ServiceAvailability.CHECKING -> MaterialR.attr.colorOutline
                }
            )
    }
}
