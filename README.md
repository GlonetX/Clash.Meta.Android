## GlonetX for Android

A rule-based tunnel for Android, built on the [Clash.Meta](https://github.com/MetaCubeX/Clash.Meta)
core with a redesigned interface.

### Requirement

- Android 5.0+ (minimum)
- Android 7.0+ (recommend)
- `armeabi-v7a` , `arm64-v8a`, `x86` or `x86_64` Architecture

### Download

Stable releases are published on the
[releases page](https://github.com/GlonetX/glonetx-android/releases/latest).
The app checks for updates itself; pick **Stable** or **Prerelease** under
Settings → App → Update Channel.

### Build

1. Update submodules

   ```bash
   git submodule update --init --recursive
   ```

2. Install **OpenJDK 11**, **Android SDK**, **CMake** and **Golang**

3. Create `local.properties` in project root with

   ```properties
   sdk.dir=/path/to/android-sdk
   ```

4. (Optional) Custom app package name. Add the following configuration to `local.properties`.

   ```properties
   # config your own applicationId, or it will be 'io.github.glonetx.android'
   custom.application.id=com.my.compile.clash
   # drop the '.alpha' suffix, so both flavors share one applicationId
   remove.suffix=true
   ```

5. Create `signing.properties` in project root with

   ```properties
   keystore.path=/path/to/keystore/file
   keystore.password=<key store password>
   key.alias=<key alias>
   key.password=<key password>
   ```

6. Build

   ```bash
   ./gradlew app:assembleAlphaRelease
   ```

### Automation

Stable (`meta`) builds ship as `io.github.glonetx.android`, alpha builds as
`io.github.glonetx.android.alpha`.

- Toggle the service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.glonetx.clash.action.TOGGLE_CLASH`
- Start the service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.glonetx.clash.action.START_CLASH`
- Stop the service
  - Send intent to activity `com.github.kr328.clash.ExternalControlActivity` with action `com.glonetx.clash.action.STOP_CLASH`
- Import a profile
  - URL Scheme `clash://install-config?url=<encoded URI>` or `clashmeta://install-config?url=<encoded URI>`

External control is off by default; enable it in Settings → App before relying
on these intents.

### Contribution and Project Maintenance

#### Meta Kernel

- CMFA uses the kernel from `android-real` branch under `MetaCubeX/Clash.Meta`, which is a merge of the main `Alpha` branch and `android-open`.
  - If you want to contribute to the kernel, make PRs to `Alpha` branch of the Meta kernel repository.
  - If you want to contribute Android-specific patches to the kernel, make PRs to  `android-open` branch of the Meta kernel repository.

#### Maintenance

- When `MetaCubeX/Clash.Meta` kernel is updated to a new version, the `Update Dependencies` actions in this repo will be triggered automatically.
  - It will pull the new version of the meta kernel, update all the golang dependencies, and create a PR without manual intervention.
  - If there is any compile error in PR, you need to fix it before merging. Alternatively, you may merge the PR directly.
- Manually triggering `Build Pre-Release` actions will compile and publish a `PreRelease` version.
- Manually triggering `Build Release` actions will compile, tag and publish a `Release` version.
  - You must fill the blank `Release Tag` with the tag you want to release in the format of `v1.2.3`.
  - `versionName` and `versionCode` in `build.gradle.kts` will be automatically bumped to the tag you filled above.
