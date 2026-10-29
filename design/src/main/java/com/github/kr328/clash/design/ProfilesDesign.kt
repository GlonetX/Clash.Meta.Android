package com.github.kr328.clash.design

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import com.github.kr328.clash.design.adapter.ProfileAdapter
import com.github.kr328.clash.design.databinding.DesignProfilesBinding
import com.github.kr328.clash.design.databinding.DialogProfilesMenuBinding
import com.github.kr328.clash.design.databinding.DialogProfileQrCodeBinding
import com.github.kr328.clash.design.dialog.AppBottomSheetDialog
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.*
import com.github.kr328.clash.service.model.Profile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class ProfilesDesign(context: Context) : Design<ProfilesDesign.Request>(context) {
    sealed class Request {
        object UpdateAll : Request()
        object Create : Request()
        object Configure : Request()
        data class Active(val profile: Profile) : Request()
        data class Update(val profile: Profile) : Request()
        data class Edit(val profile: Profile) : Request()
        data class Duplicate(val profile: Profile) : Request()
        data class ShareQrCode(val profile: Profile) : Request()
        data class Delete(val profile: Profile) : Request()
    }

    private val binding = DesignProfilesBinding
        .inflate(context.layoutInflater, context.root, false)
    private val adapter = ProfileAdapter(context, this::requestActive, this::showMenu) {
        requests.trySend(Request.Update(it))
    }

    private var allUpdating: Boolean
        get() = adapter.states.allUpdating;
        set(value) {
            adapter.states.allUpdating = value
        }
    private val rotateAnimation : Animation = AnimationUtils.loadAnimation(context, R.anim.rotate_infinite)

    override val root: View
        get() = binding.root

    suspend fun patchProfiles(profiles: List<Profile>) {
        adapter.apply {
            patchDataSet(this::profiles, profiles, id = { it.uuid })
        }

        val updatable = withContext(Dispatchers.Default) {
            profiles.any { it.imported && it.type != Profile.Type.File }
        }

        withContext(Dispatchers.Main) {
            binding.updateView.visibility = if (updatable) View.VISIBLE else View.GONE
            binding.emptyView.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    suspend fun requestSave(profile: Profile) {
        showToast(R.string.active_unsaved_tips, ToastDuration.Long) {
            setAction(R.string.edit) {
                requests.trySend(Request.Edit(profile))
            }
        }
    }

    fun updateElapsed() {
        adapter.updateElapsed()
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.mainList.recyclerList.also {
            it.bindAppBarElevation(binding.activityBarLayout)
            it.applyLinearAdapter(context, adapter)
        }
    }

    private fun showMenu(profile: Profile) {
        val dialog = AppBottomSheetDialog(context)

        val binding = DialogProfilesMenuBinding
            .inflate(context.layoutInflater, dialog.window?.decorView as ViewGroup?, false)

        binding.master = this
        binding.self = dialog
        binding.profile = profile

        dialog.setContentView(binding.root)
        dialog.show()
    }

    fun requestUpdateAll() {
        allUpdating = true;
        changeUpdateAllButtonStatus()
        requests.trySend(Request.UpdateAll)
    }

    /**
     * Spins the refresh glyph without issuing [Request.UpdateAll]. Used by the
     * launch-time refresh, which has already decided what to update.
     */
    fun setUpdateAllRunning(running: Boolean) {
        allUpdating = running;
        changeUpdateAllButtonStatus()
    }

    fun finishUpdateAll() {
        allUpdating = false;
        changeUpdateAllButtonStatus()
    }

    fun requestCreate() {
        requests.trySend(Request.Create)
    }

    fun requestConfigure() {
        requests.trySend(Request.Configure)
    }

    private fun requestActive(profile: Profile) {
        requests.trySend(Request.Active(profile))
    }

    fun requestUpdate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Update(profile))

        dialog.dismiss()
    }

    fun requestEdit(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Edit(profile))

        dialog.dismiss()
    }

    fun requestDuplicate(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Duplicate(profile))

        dialog.dismiss()
    }

    fun requestDelete(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.Delete(profile))

        dialog.dismiss()
    }

    fun requestShareQrCode(dialog: Dialog, profile: Profile) {
        requests.trySend(Request.ShareQrCode(profile))

        dialog.dismiss()
    }

    suspend fun showProfileQrCode(profile: Profile) {
        if (profile.type != Profile.Type.Url || profile.source.isBlank()) {
            showToast(R.string.profiles_share_qr_unavailable, ToastDuration.Long)
            return
        }

        val bitmap = try {
            withContext(Dispatchers.Default) {
                val matrix = QRCodeWriter().encode(
                    profile.source, BarcodeFormat.QR_CODE, 768, 768,
                    mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
                )
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) Color.BLACK else Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }
        } catch (_: WriterException) {
            showToast(R.string.qr_code_generation_failed, ToastDuration.Long)
            return
        }

        withContext(Dispatchers.Main) {
            val binding = DialogProfileQrCodeBinding.inflate(context.layoutInflater)
            binding.qrCode.setImageBitmap(bitmap)

            // Sharing the URL rather than the bitmap on purpose: the target app
            // gets something it can actually use, and this avoids dragging a
            // FileProvider authority (and its grant flags) into a dialog that
            // only wanted to hand over a string.
            binding.shareRow.setOnClickListener {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, profile.name)
                    putExtra(Intent.EXTRA_TEXT, profile.source)
                }

                context.startActivity(
                    Intent.createChooser(intent, context.getString(R.string.profiles_share_via))
                )
            }

            binding.copyRow.setOnClickListener {
                context.getSystemService<ClipboardManager>()?.setPrimaryClip(
                    ClipData.newPlainText(profile.name, profile.source)
                )

                launch {
                    showToast(R.string.profiles_link_copied, ToastDuration.Short)
                }
            }

            suspendCancellableCoroutine<Unit> { continuation ->
                val dialog = MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.share_qr_code)
                    .setView(binding.root)
                    .setPositiveButton(R.string.share) { _, _ ->
                        launch {
                            shareQrCodeBitmap(bitmap)
                        }
                    }
                    .setNegativeButton(R.string.close) { _, _ -> }
                    .setOnDismissListener {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    .show()

                continuation.invokeOnCancellation { dialog.dismiss() }
            }
        }
    }

    /**
     * Shares the QR code bitmap via the existing FileProvider
     * (`${packageName}.update.provider`, whose cache-path covers the whole
     * cacheDir). The dialog stays open so the user can share again or
     * close it manually.
     */
    private fun shareQrCodeBitmap(bitmap: Bitmap) {
        launch(Dispatchers.Default) {
            try {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(dir, "qr_code_${System.currentTimeMillis()}.png")

                FileOutputStream(file).use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.update.provider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    clipData = ClipData.newRawUri(null, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                withContext(Dispatchers.Main) {
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.share_qr_code))
                    )
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(R.string.share_failed, ToastDuration.Long)
                }
            } finally {
                // no need to keep the temp file around forever
                File(context.cacheDir, "shared")
                    .takeIf { it.isDirectory }
                    ?.listFiles()
                    ?.forEach { if (it.lastModified() < System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)) it.delete() }
            }
        }
    }

    private fun changeUpdateAllButtonStatus() {
        if (allUpdating) {
            binding.updateView.startAnimation(rotateAnimation)
        } else {
            binding.updateView.clearAnimation()
        }
    }
}
