package com.github.kr328.clash.design.model

enum class ServiceAvailability {
    CHECKING, OK, RESTRICTED, BLOCKED, TIMEOUT, FAILED
}

/**
 * Presentation model for one probed service row.
 */
class ServiceStatus(
    val name: String,
    val colors: ServiceStatusColors,
) {
    var availability: ServiceAvailability = ServiceAvailability.CHECKING
    var latencyMs: Long = -1

    val pillText: String
        get() = when (availability) {
            ServiceAvailability.OK -> "✓"
            ServiceAvailability.RESTRICTED -> "!"
            ServiceAvailability.BLOCKED -> "✕"
            else -> "…"
        }

    val statusText: String
        get() = when (availability) {
            ServiceAvailability.CHECKING -> colors.checkingText
            ServiceAvailability.OK -> colors.okText
            ServiceAvailability.RESTRICTED -> colors.restrictedText
            ServiceAvailability.BLOCKED -> colors.blockedText
            ServiceAvailability.TIMEOUT -> colors.timeoutText
            ServiceAvailability.FAILED -> colors.failedText
        }

    val latencyText: String
        get() = if (latencyMs >= 0) "$latencyMs ms" else ""

    val statusColor: Int
        get() = when (availability) {
            ServiceAvailability.OK -> colors.good
            ServiceAvailability.RESTRICTED, ServiceAvailability.TIMEOUT -> colors.medium
            ServiceAvailability.BLOCKED, ServiceAvailability.FAILED -> colors.error
            ServiceAvailability.CHECKING -> colors.neutral
        }

    val statusColorStateList: android.content.res.ColorStateList
        get() = android.content.res.ColorStateList.valueOf(statusColor)
}

/**
 * Theme-resolved colors and localized labels for service status rows.
 */
class ServiceStatusColors(
    val good: Int,
    val medium: Int,
    val error: Int,
    val neutral: Int,
    val checkingText: String,
    val okText: String,
    val restrictedText: String,
    val blockedText: String,
    val timeoutText: String,
    val failedText: String,
)
