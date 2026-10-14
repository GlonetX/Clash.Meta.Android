package com.github.kr328.clash.design.settings

import android.content.Context
import android.content.res.Configuration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.util.bindAppBarElevation
import com.github.kr328.clash.design.view.ActivityBarLayout
import java.util.Locale

/**
 * Wires a settings page's `AppRecyclerView` to a [SettingAdapter].
 *
 * Every settings page builds one of these in `init` and then calls [render]
 * whenever a persisted value changes.
 */
class SettingPage(
    recyclerView: RecyclerView,
    activityBar: ActivityBarLayout,
) {
    private val adapter = SettingAdapter(recyclerView.context)

    init {
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = adapter

        recyclerView.bindAppBarElevation(activityBar)
    }

    fun render(items: List<SettingItem>) {
        adapter.submitItems(items)
    }
}

/** One subscription, as far as the settings pages are concerned. */
data class SubscriptionInfo(
    val uuid: String,
    val name: String,
)

/** One entry of `android:localeConfig`, plus the "follow the system" option. */
data class AppLocale(
    val tag: String,
    val label: String,
)

/**
 * The languages the app ships translations for, in the order
 * `res/xml/locales_config.xml` declares them. The empty tag means "follow the
 * system locale".
 */
fun appLocales(context: Context): List<AppLocale> = listOf(
    AppLocale("", context.getString(R.string.settings_language_system)),
    AppLocale("en", "English"),
    AppLocale("ja", "日本語"),
    AppLocale("ko", "한국어"),
    AppLocale("ru", "Русский"),
    AppLocale("vi", "Tiếng Việt"),
    AppLocale("zh", "简体中文"),
    AppLocale("zh-HK", "繁體中文 (香港)"),
    AppLocale("zh-TW", "繁體中文 (台灣)"),
)

/**
 * Applies the in-app language choice and the text scale to [base].
 *
 * Called from `BaseActivity.attachBaseContext` so every activity picks the
 * setting up on the next launch without needing a new dependency
 * (`AppCompatDelegate.setApplicationLocales` needs appcompat 1.6, this project
 * is on 1.4).
 */
fun applyLocaleAndTextScale(base: Context): Context {
    val store = SettingsStore(base)

    val configuration = Configuration(base.resources.configuration)

    val language = store.appLanguage

    if (language.isNotEmpty()) {
        val locale = Locale.forLanguageTag(language)

        Locale.setDefault(locale)

        configuration.setLocale(locale)
    }

    val scale = store.textScalePercent

    if (scale != 100) {
        configuration.fontScale = scale / 100f
    }

    return base.createConfigurationContext(configuration)
}
