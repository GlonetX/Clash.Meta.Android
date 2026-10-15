package com.github.kr328.clash.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.github.kr328.clash.common.Global
import java.util.concurrent.atomic.AtomicInteger

class StatusProvider : ContentProvider() {
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        return when (method) {
            METHOD_CURRENT_PROFILE -> {
                return if (serviceRunning)
                    Bundle().apply {
                        putString("name", currentProfile)
                    }
                else
                    null
            }
            else -> super.call(method, arg, extras)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw IllegalArgumentException("Stub!")
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        throw IllegalArgumentException("Stub!")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw IllegalArgumentException("Stub!")
    }

    override fun getType(uri: Uri): String? {
        throw IllegalArgumentException("Stub!")
    }

    override fun onCreate(): Boolean {
        return true
    }

    companion object {
        const val METHOD_CURRENT_PROFILE = "currentProfile"

        private const val CLASH_SERVICE_RUNNING_FILE = "service_running.lock"

        /**
         * Monotonic id of the service instance that currently owns the core.
         * Every [claimService] hands out a fresh one, so an instance that is
         * on its way out can tell whether it is still the owner before it
         * clears shared state or broadcasts "stopped".
         */
        private val serviceIds = AtomicInteger(0)

        @Volatile
        private var currentServiceId = 0

        /** True while the core is up. Drives [call] and the boot marker. */
        var serviceRunning: Boolean = false
            private set

        /**
         * True while a service instance owns the core *and is not tearing
         * down*. An instance drops this the moment it starts shutting down,
         * so a start dispatched during teardown takes over instead of being
         * swallowed by the duplicate-instance guard.
         */
        var serviceAlive: Boolean = false
            private set

        /**
         * Claim the core for a new service instance. Returns the id the
         * caller has to pass back to [releaseService].
         */
        fun claimService(): Int {
            val id = serviceIds.incrementAndGet()

            currentServiceId = id
            serviceAlive = true
            serviceRunning = true

            shouldStartClashOnBoot = true

            return id
        }

        /**
         * Hand the core back. Ignored when a newer instance has taken over
         * in the meantime, so a superseded instance cannot clear the state
         * of its successor. Returns true when this call released the core.
         */
        fun releaseService(id: Int): Boolean {
            if (currentServiceId != id)
                return false

            serviceAlive = false
            serviceRunning = false

            shouldStartClashOnBoot = false

            return true
        }

        /**
         * Drop the duplicate-instance guard without releasing the core.
         *
         * Called from the runtime's `finally`, before `stopSelf()` is
         * dispatched: the next start then finds no live owner and takes over
         * rather than waiting for this instance's `onDestroy`.
         */
        fun abandonService(id: Int) {
            if (currentServiceId == id)
                serviceAlive = false
        }

        var shouldStartClashOnBoot: Boolean
            get() = Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).exists()
            set(value) {
                Global.application.filesDir.resolve(CLASH_SERVICE_RUNNING_FILE).apply {
                    if (value)
                        createNewFile()
                    else
                        delete()
                }
            }
        var currentProfile: String? = null
    }
}