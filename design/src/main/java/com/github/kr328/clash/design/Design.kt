package com.github.kr328.clash.design

import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.github.kr328.clash.design.ui.Surface
import com.github.kr328.clash.design.ui.ToastDuration
import com.github.kr328.clash.design.util.setOnInsertsChangedListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext

abstract class Design<R>(val context: Context) :
    CoroutineScope by CoroutineScope(Dispatchers.Unconfined) {
    abstract val root: View

    val surface = Surface()
    val requests: Channel<R> = Channel(Channel.UNLIMITED)

    companion object {
        // multiple designs can be attached to one activity (e.g. embedded tab
        // pages); onApplyWindowInsets allows a single listener per view, so
        // window inset updates are broadcast to every active surface instead
        // of only the design that registered last.
        private val activeSurfaces = mutableListOf<Surface>()
    }

    suspend fun showToast(
        resId: Int,
        duration: ToastDuration,
        configure: Snackbar.() -> Unit = {}
    ) {
        return showToast(context.getString(resId), duration, configure)
    }

    suspend fun showToast(
        message: CharSequence,
        duration: ToastDuration,
        configure: Snackbar.() -> Unit = {}
    ) {
        withContext(Dispatchers.Main) {
            Snackbar.make(
                root,
                message,
                when (duration) {
                    ToastDuration.Short -> Snackbar.LENGTH_SHORT
                    ToastDuration.Long -> Snackbar.LENGTH_LONG
                    ToastDuration.Indefinite -> Snackbar.LENGTH_INDEFINITE
                }
            ).apply(configure).show()
        }
    }

    init {
        when (context) {
            is AppCompatActivity -> {
                synchronized(activeSurfaces) {
                    activeSurfaces.add(surface)
                }

                context.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        synchronized(activeSurfaces) {
                            activeSurfaces.remove(surface)
                        }
                    }
                })

                context.window.decorView.setOnInsertsChangedListener {
                    synchronized(activeSurfaces) {
                        activeSurfaces.forEach { s ->
                            if (s.insets != it)
                                s.insets = it
                        }
                    }

                    it
                }
            }
        }
    }
}