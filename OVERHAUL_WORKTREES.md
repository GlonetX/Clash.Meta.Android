> **Historical document:** The phases A–D contract below applied to the earlier XML-only overhaul. The current Compose migration explicitly supersedes its UI-only restrictions: backend, persistence, JNI and narrowly scoped core changes are in scope. Preserve the existing design assets and user data; coordinate shared API ownership and validate changes before removing legacy UI.

# Worktree Contract (phases A–D)

`feature/ui-overhaul` carries the frozen design system from phase 0
(`DESIGN_SYSTEM.md`). Four worktrees branch from it and run in parallel.

## The frozen layers

**This overhaul is UI-only.** The network and core layers are frozen:

| Layer | Path | Rule |
|---|---|---|
| Go core + JNI bridge | `core/**` | ❌ Never. No new native surface, no `native*` additions, no changes to `Clash.kt`/`Bridge.kt`. |
| Proxy/VPN runtime | `service/.../clash/**`, `TunService.kt`, `ClashService.kt`, `NetworkObserveModule.kt`, `ClashManager` | ❌ Never. |
| Service process plumbing | the rest of `service/**` | ⚠️ Additive only: new preference keys in the stores are allowed; behaviour changes are not. |

If a feature needs core or service wiring that does not exist (a new JNI
function, a new runtime module, a change to the VPN setup), build **only** the
UI and the persisted preference, and record the required wiring in your final
report. Do not implement it. Existing APIs — `queryXxx`, `patchOverride`,
`patchSelector`, the `ProfileReceiver` intents — may be *called* freely.

Rationale: the base system is stable and CI-green; the rewrite must not be
able to regress the tunnel, the VPN, or the core.

## The one rule that keeps them from colliding

> **Never edit a shared resource file. Add your own.**

| Shared file | Rule |
|---|---|
| `design/src/main/res/values/strings.xml` | ❌ Do not edit. Create `values/strings_dashboard.xml`, `values/strings_settings.xml`, `values/strings_proxy.xml`, `values/strings_profiles.xml` instead. Resource merging makes this equivalent, and it removes the top merge conflict. |
| `design/src/main/res/values/dimens.xml` | ❌ Do not edit. Use `values/tokens.xml` tokens; if a page genuinely needs a new one, add it to your own `values/dimens_*.xml`. |
| `design/src/main/res/values/colors.xml`, `themes.xml` | ❌ Do not edit, except worktree D which owns the DynamicColors overlay. |
| `gradle/libs.versions.toml` | ❌ Do not edit. If a phase needs a new dependency, report it instead of adding it. |

Every `design_*.xml`, `adapter_*.xml` and `*Design.kt` is owned by exactly one
worktree. The ownership table below is exhaustive — if a file is not listed,
you may create it but you may not assume another worktree will leave it alone.

## Ownership

### A — Dashboard
```
app/src/main/java/com/github/kr328/clash/MainActivity.kt
design/.../design/MainDesign.kt
design/src/main/res/layout/design_main.xml
core/src/main/golang/native/**                      (only A touches Go)
design/.../dashboard/**                             (new)
design/.../view/{DonutChartView,MemoryBarView,ServiceStatusView,MetricRingView,...}.kt  (new)
design/src/main/res/layout/adapter_dashboard_*.xml   (new)
design/src/main/res/values/strings_dashboard.xml     (new)
```

### B — Settings DSL → XML
```
design/.../design/preference/**                      (DELETE)
design/.../design/{SettingsDesign,AppSettingsDesign,NetworkSettingsDesign,
                   OverrideSettingsDesign,MetaFeatureSettingsDesign}.kt
design/src/main/res/layout/design_settings*.xml
design/src/main/res/layout/preference_*.xml
app/src/main/java/com/github/kr328/clash/{SettingsActivity,AppSettingsActivity,
                   NetworkSettingsActivity,OverrideSettingsActivity,
                   MetaFeatureSettingsActivity}.kt
design/.../settings/**                               (new)
design/src/main/res/layout/adapter_setting_*.xml     (new)
design/src/main/res/values/strings_settings.xml      (new)
```

### C — Proxy + Connections
```
design/.../design/{ProxyDesign,ConnectionsDesign}.kt
design/.../component/Proxy*.kt
design/.../view/ProxyView.kt
design/src/main/res/layout/{design_proxy,design_connections,adapter_connection}.xml
app/src/main/java/com/github/kr328/clash/{ProxyActivity,ConnectionsActivity}.kt
design/src/main/res/values/strings_proxy.xml         (new)
```

### D — Profiles + Logs + DynamicColors
```
design/.../design/{ProfilesDesign,LogsDesign,LogcatDesign}.kt
design/src/main/res/layout/{design_profiles,design_logs,design_logcat}.xml
app/src/main/java/com/github/kr328/clash/{ProfilesActivity,LogsActivity,
                   LogcatActivity,MainApplication}.kt
design/src/main/res/values/themes.xml                (D owns the dynamic colour overlay)
design/src/main/res/values/{values-night,values-v29}/themes.xml
design/src/main/res/values/strings_profiles.xml      (new)
```

## Merge order

`A` → `B` → `C` → `D`.

`A` lands first because it is the largest and the others may want to reuse its
adapter and card patterns. The ownership split means `B`, `C` and `D` can
merge in any order after `A` without conflict.

## Verification

Each worktree pushes a branch and opens a PR. The fork's existing
`.github/workflows/build-debug.yaml` runs `app:assembleAlphaRelease` on PRs,
which compiles Kotlin, DataBinding expressions and resource links, and builds
the Go core. That is the only compile gate — there is no local Android SDK.

**Read the CI result before opening the next worktree's PR.** A red build in
one worktree usually means a token or component contract violation that the
others will repeat.