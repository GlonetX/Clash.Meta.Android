package com.github.kr328.clash.common.constants

import com.github.kr328.clash.common.util.packageName

object Intents {
    /**
     * Public control surface. These strings are hardcoded on purpose: they are
     * what Tasker/Shortcuts automations target, so they must not move when the
     * applicationId or the flavor changes. See README "Automation".
     */
    private const val PUBLIC_PREFIX = "com.glonetx.clash.action"

    // Public
    val ACTION_PROVIDE_URL = "$PUBLIC_PREFIX.PROVIDE_URL"
    val ACTION_START_CLASH = "$PUBLIC_PREFIX.START_CLASH"
    val ACTION_STOP_CLASH = "$PUBLIC_PREFIX.STOP_CLASH"
    val ACTION_TOGGLE_CLASH = "$PUBLIC_PREFIX.TOGGLE_CLASH"

    const val EXTRA_NAME = "name"
    const val EXTRA_NAV_ROUTE = "com.glonetx.android.NAV_ROUTE"

    // Self
    val ACTION_SERVICE_RECREATED = "$packageName.intent.action.CLASH_RECREATED"
    val ACTION_CLASH_STARTED = "$packageName.intent.action.CLASH_STARTED"
    val ACTION_CLASH_STOPPED = "$packageName.intent.action.CLASH_STOPPED"
    val ACTION_CLASH_REQUEST_STOP = "$packageName.intent.action.CLASH_REQUEST_STOP"
    val ACTION_PROFILE_CHANGED = "$packageName.intent.action.PROFILE_CHANGED"
    val ACTION_PROFILE_UPDATE_COMPLETED = "$packageName.intent.action.PROFILE_UPDATE_COMPLETED"
    val ACTION_PROFILE_UPDATE_FAILED = "$packageName.intent.action.PROFILE_UPDATE_FAILED"
    val ACTION_PROFILE_REQUEST_UPDATE = "$packageName.intent.action.REQUEST_UPDATE"
    val ACTION_PROFILE_SCHEDULE_UPDATES = "$packageName.intent.action.SCHEDULE_UPDATES"
    val ACTION_PROFILE_LOADED = "$packageName.intent.action.PROFILE_LOADED"
    val ACTION_OVERRIDE_CHANGED = "$packageName.intent.action.OVERRIDE_CHANGED"

    const val EXTRA_STOP_REASON = "stop_reason"
    const val EXTRA_UUID = "uuid"
    const val EXTRA_FAIL_REASON = "fail_reason"

    /** Settings deep link: scroll the target page to this row identityKey. */
    const val EXTRA_SCROLL_TO = "scroll_to"
}
