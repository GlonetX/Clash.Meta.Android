package com.github.kr328.clash.design.model

/**
 * How much breathing room a proxy node card gets.
 *
 * [com.github.kr328.clash.design.store.UiStore.proxyLine] already controls how
 * many columns the grid has; this controls how tall and how padded each card
 * is *within* its column. The two are independent: "3 columns + tight" is a
 * useful combination for groups with hundreds of nodes.
 *
 * Every value maps onto the 4dp spacing grid and the app_radius_* tokens — see
 * `values/dimens_proxy.xml`.
 */
enum class ProxyDensity {
    Tight,
    Standard,
    Loose,
}

/**
 * Leading glyph drawn on a proxy node card.
 *
 * The proxy page had no icon concept at all before this; every glyph that
 * exists in the page therefore has exactly one meaning, and [None] is the
 * default so nothing is implied where nothing is known.
 */
enum class ProxyIconStyle {
    /** No leading glyph. */
    None,

    /** Regional-indicator flag emoji derived from the leading token of the
     *  node name, e.g. `HK 01` or `US-2` renders as the 🇭🇰 / 🇺🇸 flag. */
    Flag,
}