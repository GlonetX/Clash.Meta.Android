package com.github.kr328.clash.design.component

import android.content.Context
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.PopupMenu
import com.github.kr328.clash.core.model.ProxySort
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.R
import com.github.kr328.clash.design.model.ProxyDensity
import com.github.kr328.clash.design.model.ProxyIconStyle
import com.github.kr328.clash.design.store.UiStore
import kotlinx.coroutines.channels.Channel

/**
 * The proxy page overflow menu.
 *
 * Two kinds of item live here and they must be treated differently:
 *   * items that change *what the core returns* (the node list and its order)
 *     send [ProxyDesign.Request.ReloadAll];
 *   * items that change *how a card is drawn* (column count, card density,
 *     node icon) only re-run [updateConfig], because the node data is already
 *     on screen and reloading would make the grid flicker.
 *
 * Items inside a `checkableBehavior="single"` group are never toggled by hand:
 * the group already enforces exactly one checked item, and flipping the
 * clicked item afterwards would leave nothing checked at all.
 */
class ProxyMenu(
    context: Context,
    menuView: View,
    mode: TunnelState.Mode?,
    private val uiStore: UiStore,
    private val requests: Channel<ProxyDesign.Request>,
    private val updateConfig: () -> Unit,
) : PopupMenu.OnMenuItemClickListener {
    private val menu = PopupMenu(context, menuView)

    fun show() {
        menu.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.not_selectable -> {
                uiStore.proxyExcludeNotSelectable = !item.isChecked

                item.isChecked = !item.isChecked

                requests.trySend(ProxyDesign.Request.ReLaunch)
            }
            R.id.single -> {
                uiStore.proxyLine = 1

                updateConfig()

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.doubles -> {
                uiStore.proxyLine = 2

                updateConfig()

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.multiple -> {
                uiStore.proxyLine = 3

                updateConfig()

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.quadruple -> {
                uiStore.proxyLine = 4

                updateConfig()

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.density_tight -> {
                uiStore.proxyDensity = ProxyDensity.Tight

                updateConfig()
            }
            R.id.density_standard -> {
                uiStore.proxyDensity = ProxyDensity.Standard

                updateConfig()
            }
            R.id.density_loose -> {
                uiStore.proxyDensity = ProxyDensity.Loose

                updateConfig()
            }
            R.id.icon_none -> {
                uiStore.proxyIconStyle = ProxyIconStyle.None

                updateConfig()
            }
            R.id.icon_flag -> {
                uiStore.proxyIconStyle = ProxyIconStyle.Flag

                updateConfig()
            }
            R.id.default_ -> {
                uiStore.proxySort = ProxySort.Default

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.name -> {
                uiStore.proxySort = ProxySort.Title

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.delay -> {
                uiStore.proxySort = ProxySort.Delay

                requests.trySend(ProxyDesign.Request.ReloadAll)
            }
            R.id.dont_modify -> {
                requests.trySend(ProxyDesign.Request.PatchMode(null))
            }
            R.id.direct_mode -> {
                requests.trySend(ProxyDesign.Request.PatchMode(TunnelState.Mode.Direct))
            }
            R.id.global_mode -> {
                requests.trySend(ProxyDesign.Request.PatchMode(TunnelState.Mode.Global))
            }
            R.id.rule_mode -> {
                requests.trySend(ProxyDesign.Request.PatchMode(TunnelState.Mode.Rule))
            }
            else -> return false
        }

        return true
    }

    init {
        menu.menuInflater.inflate(R.menu.menu_proxy, menu.menu)

        menu.menu.apply {
            findItem(R.id.not_selectable).isChecked = uiStore.proxyExcludeNotSelectable

            when (uiStore.proxyLine) {
                1 -> findItem(R.id.single).isChecked = true
                2 -> findItem(R.id.doubles).isChecked = true
                3 -> findItem(R.id.multiple).isChecked = true
                4 -> findItem(R.id.quadruple).isChecked = true
            }

            when (uiStore.proxyDensity) {
                ProxyDensity.Tight -> findItem(R.id.density_tight).isChecked = true
                ProxyDensity.Standard -> findItem(R.id.density_standard).isChecked = true
                ProxyDensity.Loose -> findItem(R.id.density_loose).isChecked = true
            }

            when (uiStore.proxyIconStyle) {
                ProxyIconStyle.None -> findItem(R.id.icon_none).isChecked = true
                ProxyIconStyle.Flag -> findItem(R.id.icon_flag).isChecked = true
            }

            when (uiStore.proxySort) {
                ProxySort.Default -> findItem(R.id.default_).isChecked = true
                ProxySort.Title -> findItem(R.id.name).isChecked = true
                ProxySort.Delay -> findItem(R.id.delay).isChecked = true
            }

            when (mode) {
                null -> findItem(R.id.dont_modify).isChecked = true
                TunnelState.Mode.Direct -> findItem(R.id.direct_mode).isChecked = true
                TunnelState.Mode.Global -> findItem(R.id.global_mode).isChecked = true
                TunnelState.Mode.Rule -> findItem(R.id.rule_mode).isChecked = true
                else -> {}
            }
        }

        menu.setOnMenuItemClickListener(this)
    }
}