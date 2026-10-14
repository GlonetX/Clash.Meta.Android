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
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
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
 */
class ServiceStatusPagerView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : FrameLayout(context, attributeSet, defStyleAttr) {

    private val pagerAdapter = PageAdapter()

    private val pager = ViewPager(context)

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

        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            override fun onPageSelected(position: Int) {
                highlightPage()
            }
        })
    }

    fun setServices(services: List<ServiceProbe>) {
        if (services == this.services)
            return

        this.services = services

        // The page contents are baked into the views, so the adapter has to be
        // detached and reattached rather than merely notified.
        pager.adapter = null
        pagerAdapter.services = services
        pager.adapter = pagerAdapter
        pager.currentItem = 0

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

    private inner class PageAdapter : PagerAdapter() {
        var services: List<ServiceProbe> = emptyList()

        override fun getCount(): Int = services.size

        override fun getItemPosition(position: Int): Int = POSITION_NONE

        override fun isViewFromObject(view: View, obj: Any): Boolean = view === obj

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val binding = AdapterDashboardServicePageBinding
                .inflate(context.layoutInflater, container, false)

            bind(binding, services[position])

            container.addView(binding.root)

            return binding.root
        }

        override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
            container.removeView(obj as View)
        }

        private fun bind(binding: AdapterDashboardServicePageBinding, probe: ServiceProbe) {
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