package com.github.kr328.clash.design.preference

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import android.widget.LinearLayout.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
import kotlinx.coroutines.CoroutineScope

interface PreferenceScreen : CoroutineScope {
    val context: Context
    val root: ViewGroup
}

internal interface PreferenceScreenImpl : PreferenceScreen {
    var groupContainer: LinearLayout?
}

fun CoroutineScope.preferenceScreen(
    context: Context,
    configure: PreferenceScreen.() -> Unit
): PreferenceScreen {
    val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
    }

    val impl = object : PreferenceScreenImpl, CoroutineScope by this {
        override val context: Context
            get() = context
        override val root: ViewGroup
            get() = root
        override var groupContainer: LinearLayout? = null
    }

    impl.configure()

    return impl
}

fun PreferenceScreen.addElement(preference: Preference) {
    val target: ViewGroup = if (this is PreferenceScreenImpl)
        groupContainer ?: root
    else
        root

    target.addView(preference.view, LayoutParams(MATCH_PARENT, WRAP_CONTENT))
}
