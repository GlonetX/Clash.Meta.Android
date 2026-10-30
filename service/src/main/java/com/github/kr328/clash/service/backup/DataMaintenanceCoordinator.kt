package com.github.kr328.clash.service.backup

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/** All subscription/configuration writers enter here, including document FDs. */
object DataMaintenanceCoordinator {
    private val gate = Mutex()
    @Volatile var restoreInProgress: Boolean = false
        internal set
    private var writers = 0
    private class Lease : AbstractCoroutineContextElement(Key) {
        var active = true
        companion object Key : CoroutineContext.Key<Lease>
    }

    suspend fun ensureReady(context: Context) { exclusive(context) {} }

    suspend fun <T> exclusive(context: Context, block: suspend () -> T): T {
        if (currentCoroutineContext()[Lease]?.active == true) return block()
        return gate.withLock {
            val lease = Lease()
            try { withContext(lease) {
                // Recovery can fail during a later restore, not just at process startup.
                // Never admit a new operation while an unfinished journal exists.
                recoverBeforeOperation({ BackupArchive.recover(context.applicationContext) }, block)
            } } finally { lease.active = false }
        }
    }

    /** Call while inside exclusive before exposing a writable descriptor. */
    @Synchronized fun openedWriter() { check(!restoreInProgress) { "A restore is in progress" }; writers++ }
    @Synchronized fun closedWriter() { if (writers > 0) writers-- }
    @Synchronized fun requireNoWriters() { check(writers == 0) { "Close configuration editors before backing up or restoring" } }
}

internal suspend fun <T> recoverBeforeOperation(recover: suspend () -> Unit, operation: suspend () -> T): T {
    recover()
    return operation()
}
