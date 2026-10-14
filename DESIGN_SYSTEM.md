# Design System Contract

Frozen on `feature/ui-overhaul`. Every page migration in this overhaul must
follow this document. If a change seems to need a new token or a different
metric, add it here first — do not invent one inline.

---

## 1. Tokens — `design/src/main/res/values/tokens.xml`

### Radius

| Token | Value | Use for |
|---|---|---|
| `app_radius_xs` | 8dp | chips, badges, tiny pills |
| `app_radius_sm` | 12dp | icon containers, small surfaces |
| `app_radius_md` | 20dp | **cards and groups — the default** |
| `app_radius_lg` | 28dp | navigation dock, bottom sheets |

> These replace the previous ad-hoc 5dp / 8dp / 12dp / 20dp / 28dp mix.
> `large_action_card_radius` (8dp) and `proxy_card_radius` (5dp) are legacy;
> do not use them in new code.

### Spacing — 4dp grid

| Token | Value |
|---|---|
| `app_space_xs` | 4dp |
| `app_space_sm` | 8dp |
| `app_space_md` | 12dp |
| `app_space_lg` | 16dp |
| `app_space_xl` | 24dp |

### Elevation

| Token | Value | Use for |
|---|---|---|
| `app_elevation_flat` | 0dp | inline surfaces, list rows |
| `app_elevation_card` | 1dp | **cards — the default** |
| `app_elevation_raised` | 3dp | sticky headers, FAB |
| `app_elevation_dock` | 8dp | the floating navigation dock |

### Iconography

| Token | Value |
|---|---|
| `app_icon_container_size` | 40dp |
| `app_icon_glyph_size` | 24dp |
| `app_icon_glyph_size_small` | 20dp |
| `app_row_min_height` | 56dp |

---

## 2. Typography — six semantic roles

**Never reference `TextAppearance.Material3.*` directly.** Pick the role by
what the text *is*, not by how big it currently looks.

| Role | Size | Use for |
|---|---|---|
| `TextAppearance.App.ScreenTitle` | 28sp bold | page-level screen title |
| `TextAppearance.App.CardTitle` | 16sp | the title row inside a card |
| `TextAppearance.App.Metric` | 24sp | a large value — speed, memory, IP |
| `TextAppearance.App.MetricSmall` | 16sp | a compact value — uptime, count |
| `TextAppearance.App.Label` | 12sp | a caption under a metric |
| `TextAppearance.App.Body` | 14sp | supporting prose, row primary text |
| `TextAppearance.App.NavLabel` | 12sp | bottom navigation labels |

`Metric`, `MetricSmall` and `NavLabel` set `android:fontFeatureSettings="tnum"`
so ticking values do not shift neighbouring digits.

> This exists to fix a real bug: `TextAppearance.Material3.HeadlineSmall`
> (a 24sp *screen-level* size) was used as a *card* title in 5 places,
> including the app name, while `TitleMedium` was used for both speed values
> and a bolded IP address.

---

## 3. Components

### `AppCard` — the one card surface

`com.github.kr328.clash.design.view.AppCard`

A `MaterialCardView` preconfigured with `app_radius_md`,
`app_elevation_card`, zero stroke and `colorSurface`. It is not clickable or
focusable by default, so the clickable content goes *inside* it.

```xml
<com.github.kr328.clash.design.view.AppCard
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout android:orientation="vertical" ...>
        ...
    </LinearLayout>
</com.github.kr328.clash.design.view.AppCard>
```

### `AppRow` — the one row

`com.github.kr328.clash.design.view.AppRow`

Icon container + primary line + optional secondary line + optional trailing
glyph. Replaces `ActionLabel`, `LargeActionLabel` and the DSL-injected
preference rows.

```xml
<com.github.kr328.clash.design.view.AppRow
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:onClick="@{() -> self.request(Request.Open)}"
    app:icon="@drawable/ic_baseline_settings"
    app:trailingIcon="@drawable/ic_baseline_chevron_right"
    app:text="@string/app"
    app:subtext="@string/app_summary" />
```

| Attribute | Meaning |
|---|---|
| `app:text` | primary line (`TextAppearance.App.Body`) |
| `app:subtext` | secondary line (`TextAppearance.App.Label`), hides when empty |
| `app:icon` | leading glyph, tinted `colorOnSurfaceVariant` |
| `app:trailingIcon` | trailing glyph, tinted `colorOutline`, hides when unset |
| `app:hideIconContainer` | collapse the rounded icon container |

---

## 4. Rules

1. **Icons are `ImageView` with a theme tint, never `<View
   android:background="@drawable/ic_*"/>`.** The `<View>` form cannot be
   tinted, animated or size-constrained, and the app currently has 9 of them
   (5 in `design_main.xml`). All vectors bake in
   `android:tint="?attr/colorControlNormal"`; prefer letting `AppRow` /
   `ImageViewCompat` supply the tint so one drawable works on-surface and
   on-primary alike.

2. **Cards never nest.** A card contains content, not another card.

3. **One card, one subject.** If a card needs a heading it uses
   `TextAppearance.App.CardTitle`; a value it uses `Metric` or `MetricSmall`.
   Do not use `HeadlineSmall` for a card title.

4. **Data flows through one state object, not imperative setters.** A screen
   that shows more than about six independently-updating values must expose a
   single immutable state object and bind it through an adapter
   (`ListAdapter` + `DiffUtil`). The existing pattern of one
   `suspend fun setXxx()` per field does not survive view recycling — see
   the `MainDesign` analysis in the overhaul notes.

5. **Keep `Design<R>` intact.** The `Channel<R>` request mechanism and the
   `BaseActivity` `select {}` loop are unchanged. Page migrations must not
   add a second event mechanism.

---

## 5. Migration order

| Phase | Scope | Depends on |
|---|---|---|
| 0 | this document + tokens + `AppCard` + `AppRow` | — |
| A | Dashboard grid + widgets | Phase 0 |
| B | Settings DSL → XML + new settings | Phase 0 |
| C | Proxy + Connections pages | Phase 0 |
| D | Profiles + Logs + DynamicColors | Phase 0 |

Phases A–D are independent of each other and may be worked in parallel, but
they must not edit each other's files. The shared files listed in
`OVERHAUL_WORKTREES.md` are the only contention points.