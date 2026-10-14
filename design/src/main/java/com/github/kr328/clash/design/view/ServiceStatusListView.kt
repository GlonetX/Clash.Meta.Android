package com.github.kr328.clash.design.view

import android.content.Context
import android.content.res.ColorStateList
import android.text.TextUtils
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.dashboard.ServiceProbe
import com.github.kr328.clash.design.model.ServiceAvailability
import com.github.kr328.clash.design.util.resolveThemedColor
import com.google.android.material.R as MaterialR

/**
 * Every detected service in one flat card — a compact row per service:
 * status dot, service name, status text, latency.
 *
 * Replaces the swipeable [ServiceStatusPagerView] per product feedback: the
 * point of the card is seeing every service at a glance, and a pager hides
 * all but one behind a swipe.
 */
class ServiceStatusListView @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    @AttrRes defStyleAttr: Int = 0,
) : LinearLayout(context, attributeSet, defStyleAttr) {

    private val rowSpacing =
        resources.getDimensionPixelSize(R.dimen.app_space_sm)
    private val dotSize =
        resources.getDimensionPixelSize(R.dimen.dashboard_service_dot_size)
    private val textGap =
        resources.getDimensionPixelSize(R.dimen.app_space_sm)

    init {
        orientation = VERTICAL
    }

    fun setServices(services: List<ServiceProbe>) {
        removeAllViews()

        services.forEachIndexed { index, probe ->
            if (index > 0) {
                val spacer = View(context)
                addView(spacer, LayoutParams(LayoutParams.MATCH_PARENT, rowSpacing))
            }

            addView(ServiceRow(context, probe))
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

    private fun latencyText(probe: ServiceProbe): String? {
        if (probe.latencyMillis <= 0)
            return null

        return "${probe.latencyMillis} ms"
    }

    private inner class ServiceRow(context: Context, probe: ServiceProbe) :
        LinearLayout(context) {

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            val color = statusColor(probe.availability)

            val dot = View(context).apply {
                background = context.getDrawable(R.drawable.bg_dashboard_status_dot)
                backgroundTintList = ColorStateList.valueOf(color)
            }
            addView(dot, LayoutParams(dotSize, dotSize))

            val name = TextView(context).apply {
                text = probe.name
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTextAppearance(R.style.TextAppearance_App_Body)
            }
            addView(
                name,
                LayoutParams(0, LayoutParams.WRAP_CONTENT).apply {
                    weight = 1f
                    marginStart = textGap
                }
            )

            val status = TextView(context).apply {
                text = statusText(probe.availability)
                maxLines = 1
                setTextAppearance(R.style.TextAppearance_App_Label)
            }
            addView(
                status,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            )

            latencyText(probe)?.let { latency ->
                val latencyView = TextView(context).apply {
                    text = latency
                    maxLines = 1
                    setTextAppearance(R.style.TextAppearance_App_Label)
                    setTextColor(color)
                }
                addView(
                    latencyView,
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                        marginStart = textGap
                    }
                )
            }
        }
    }
}