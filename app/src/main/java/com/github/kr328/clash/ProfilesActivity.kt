package com.github.kr328.clash

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.common.util.setUUID
import com.github.kr328.clash.common.util.ticker
import com.github.kr328.clash.design.ProfilesDesign
import com.github.kr328.clash.design.store.DynamicColorsStore
import com.github.kr328.clash.design.store.ProfileUpdateStore
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.util.ApplicationObserver
import com.github.kr328.clash.util.withProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit
import com.github.kr328.clash.design.R

class ProfilesActivity : BaseActivity<ProfilesDesign>() {
    private var startupUpdateRunning = false

    override suspend fun main() {
        val design = ProfilesDesign(this)

        setContentDesign(design)

        val ticker = ticker(TimeUnit.MINUTES.toMillis(1))

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ActivityStart, Event.ProfileChanged -> {
                            design.fetch()

                            if (it == Event.ActivityStart) {
                                requestStartupUpdate(design)
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProfilesDesign.Request.Create ->
                            startActivity(NewProfileActivity::class.intent)
                        ProfilesDesign.Request.Configure ->
                            showProfilesSettings()
                        ProfilesDesign.Request.UpdateAll ->
                            withProfile {
                                try {
                                    queryAll().forEach { p ->
                                        if (p.imported && p.type != Profile.Type.File)
                                            update(p.uuid)
                                    }
                                }
                                finally {
                                    withContext(Dispatchers.Main) {
                                        design.finishUpdateAll();
                                    }
                                }
                            }
                        is ProfilesDesign.Request.Update ->
                            withProfile { update(it.profile.uuid) }
                        is ProfilesDesign.Request.Delete ->
                            withProfile { delete(it.profile.uuid) }
                        is ProfilesDesign.Request.Edit ->
                            startActivity(PropertiesActivity::class.intent.setUUID(it.profile.uuid))
                        is ProfilesDesign.Request.Active -> {
                            withProfile {
                                if (it.profile.imported)
                                    setActive(it.profile)
                                else
                                    design.requestSave(it.profile)
                            }
                        }
                        is ProfilesDesign.Request.Duplicate -> {
                            val uuid = withProfile { clone(it.profile.uuid) }

                            startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                        }
                        is ProfilesDesign.Request.ShareQrCode ->
                            design.showProfileQrCode(it.profile)
                    }
                }
                if (activityStarted) {
                    ticker.onReceive {
                        design.updateElapsed()
                    }
                }
            }
        }
    }

    private suspend fun ProfilesDesign.fetch() {
        withProfile {
            patchProfiles(queryAll())
        }
    }

    /**
     * Launch-time subscription refresh.
     *
     * The app shipped per-card updates and a `ProfileReceiver` alarm for
     * profiles with a cadence, but nothing ran when the app itself opened: a
     * subscription with no interval, or one whose interval had elapsed while
     * the device was off, stayed stale until the user noticed.
     *
     * Honours [ProfileUpdateStore.updateOnLaunch] and each profile's own
     * interval, and skips anything the service would refuse to schedule.
     * `update()` is fire-and-forget - it hands the UUID to `ProfileWorker` - so
     * this returns immediately and the "updated / failed" toasts come from the
     * existing callbacks.
     */
    private suspend fun requestStartupUpdate(design: ProfilesDesign) {
        if (!ProfileUpdateStore(this).updateOnLaunch || startupUpdateRunning)
            return

        val now = System.currentTimeMillis()

        val candidates = withProfile {
            queryAll().filter {
                it.imported &&
                        it.type != Profile.Type.File &&
                        it.interval >= MINIMUM_AUTO_UPDATE_INTERVAL &&
                        now - it.updatedAt >= it.interval
            }
        }

        if (candidates.isEmpty())
            return

        startupUpdateRunning = true
        design.setUpdateAllRunning(true)

        try {
            withProfile {
                candidates.forEach { update(it.uuid) }
            }
        } finally {
            startupUpdateRunning = false

            withContext(Dispatchers.Main) {
                design.setUpdateAllRunning(false)
            }
        }
    }

    /**
     * The profiles page settings dialog: dynamic colour and the
     * update-on-launch preference. Toggling dynamic colour recreates all
     * activities so the new palette takes effect immediately.
     */
    private fun showProfilesSettings() {
        val view = layoutInflater.inflate(R.layout.dialog_profiles_settings, null)

        val dynamicColorsSwitch = view.findViewById<MaterialSwitch>(R.id.switch_dynamic_colors)
        val updateOnLaunchSwitch = view.findViewById<MaterialSwitch>(R.id.switch_update_on_launch)

        dynamicColorsSwitch.isChecked = DynamicColorsStore(this).enabled
        updateOnLaunchSwitch.isChecked = ProfileUpdateStore(this).updateOnLaunch

        dynamicColorsSwitch.setOnCheckedChangeListener { _, checked ->
            DynamicColorsStore(this).enabled = checked

            ApplicationObserver.createdActivities.forEach { it.recreate() }
        }

        updateOnLaunchSwitch.setOnCheckedChangeListener { _, checked ->
            ProfileUpdateStore(this).updateOnLaunch = checked
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.profiles_settings)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onProfileUpdateCompleted(uuid: UUID?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_complete, name),
                ToastDuration.Long
            )
        }
    }
    override fun onProfileUpdateFailed(uuid: UUID?, reason: String?) {
        if(uuid == null)
            return;
        launch {
            var name: String? = null;
            withProfile {
                name = queryByUUID(uuid)?.name
            }
            design?.showToast(
                getString(R.string.toast_profile_updated_failed, name, reason),
                ToastDuration.Long
            ){
                setAction(R.string.edit) {
                    startActivity(PropertiesActivity::class.intent.setUUID(uuid))
                }
            }
        }
    }

    companion object {
        /**
         * `ProfileReceiver.scheduleNext` refuses to arm an alarm sooner than
         * this, so a launch-time refresh has to use the same floor or it would
         * fight the service's own scheduling.
         */
        private val MINIMUM_AUTO_UPDATE_INTERVAL = TimeUnit.MINUTES.toMillis(15)
    }
}
