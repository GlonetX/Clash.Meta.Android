package com.github.kr328.clash.service.backup

/** Both operations must be idempotent: a committed journal may be replayed at any point. */
internal fun <T> rescheduleProfiles(old: List<T>, current: List<T>, cancel: (T) -> Unit, schedule: (T) -> Unit) {
    old.forEach(cancel)
    current.forEach(schedule)
}
