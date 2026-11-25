# FlipLink

Android utility (`dev.miles.fliplink`) that restores native per-domain "Open by default" control on Android 12+ OEM ROMs where system settings hide per-domain toggles (built primarily for Honor MagicOS 11 / Android 17). Public solo repository (`miles2542/FlipLink`), personal-first and open-source as-is.

Scope is strictly single-user (`--user 0`) and Shizuku-first with a read-only + copyable `adb shell` fallback. Root (`su`), background install-watching services, multi-user/clone profiles, and Share-sheet or default-browser interception are out of scope.

## How It Works

- **Module & DI boundary:** Single production module (`:app`) wired via manual `AppContainer` DI (`FlipLinkApplication` → `AppContainer` → `MainActivity`; no Hilt, KSP, Room, or Coil), plus two manifest-only E2E fixtures (`:fixtures:stock-app` with `autoVerify="true"` and `:fixtures:patched-app` without `autoVerify`).
- **Pure-Kotlin domain layer:** `domain/` has zero `android.*` or `androidx.*` imports (enforced by `DomainPurityTest`) so parsing, conflict planning, drift detection, and `adb` command generation run on the JVM.
- **Parser resilience:** `PmAppLinksParser` handles all three `pm get-app-links --user 0` output forms across Android 12–17: Form A (standard `autoVerify` block with `Domain verification state:`), Form B (`<pkg> <uuid>:` without `autoVerify`, common on patched apps), and Form C (headerless single-package query starting at `User 0:`). Unknown states or numeric codes `>= 1024` map to `VerificationState.Error(code)` without throwing.
- **Privileged execution:** `ShellUserService` (`IShellService.aidl`) runs `pm` via `ProcessBuilder(argv)` (never `sh -c`) with `InputValidator` subcommand/package/domain allowlists, a 15s command timeout, and `<= 40`-domain batching.
- **Conflict resolution & state sync:** `ConflictPlanner` computes domain conflicts locally from one `pm get-app-links --user 0` call using AOSP approval tiers (`VERIFIED = 4`, `SELECTION = 3`, `NONE = 0`); `pm get-app-link-owners` is reserved for targeted verification and E2E checks. Per-domain takeover demotes each competitor without touching its master switch (`pm set-app-links --package <C> 3 <domains>` when `autoVerify` + `pm set-app-links-user-selection --user 0 --package <C> false <domains>`), promotes the target (`set-app-links-allowed true`, `set-app-links-user-selection true`, and `set-app-links 2` when `autoVerify`), and records demotions in `deniedDomains` so `DriftDetector` catches system re-verification.
- **Serialized mutations:** `AppLinksRepository` serializes every mutation and its mandatory post-mutation `pm get-app-links` re-read through one `Mutex`, keeping `StateFlow<RepositoryState>` aligned with live OS truth and persisting atomic JSON snapshots (`filesDir/fliplink_snapshot_v1.json`, plus SAF export/import).

## Where Code Lives

- `app/src/main/kotlin/dev/miles/fliplink/`
  - `AppContainer.kt`, `FlipLinkApplication.kt`, `MainActivity.kt` — manual DI graph, `NavHost` (`AppList` → `AppDetail`), modal sheets, and SAF config export/import launchers.
  - `domain/model/` (`AppLinkModels.kt`, `DriftModels.kt`, `FlipLinkSnapshot.kt`) — immutable app-link, conflict, drift, and snapshot models.
  - `domain/parser/PmAppLinksParser.kt` — Forms A/B/C parser for `pm get-app-links`.
  - `domain/planner/` (`ConflictPlanner.kt`, `DriftDetector.kt`, `AdbCommandBuilder.kt`, `InputValidator.kt`) — takeover/undo plans, snapshot-vs-OS diffing, sanitized `adb shell` commands, and allowlist validation.
  - `data/AppLinksRepository.kt` — `Mutex`-serialized repository, takeover/undo orchestration, and drift reapply/accept.
  - `data/pm/` (`PackageDiscovery.kt`, `AppIconCache.kt`) — `PackageManager` discovery (left-joined with `pm` output, filtering scheme-only browsers) and icon caching.
  - `data/shizuku/` (`ShizukuBridge.kt`, `ShellExecutor.kt`, `ShellUserService.kt`, `ShizukuState.kt`) — Shizuku `UserService` lifecycle (`dev.rikka.shizuku:{api,provider}:13.1.5`), permission flow, and `IShellService.aidl` execution.
  - `data/storage/` (`SnapshotStore.kt`, `PreferencesStore.kt`) — atomic JSON snapshot persistence and theme/filter preferences.
  - `ui/` — `applist/`, `appdetail/`, `sheets/` (`ShizukuStatusSheet`, `DriftReviewSheet`, `SettingsSheet`), `components/` (`AdbCommandSheet`, `StatusPill`, `CompactBanner`, `SearchField`, `FlipLinkIcons`), `theme/` (`Tokens.kt`, `Color.kt`, `Type.kt`, `Theme.kt`).
- `app/src/main/aidl/dev/miles/fliplink/data/shizuku/` — `IShellService.aidl`, `ShellResult.aidl`.
- `app/src/test/kotlin/dev/miles/fliplink/` — JVM unit tests (`DomainPurityTest`, parser, planner, repository, storage, ViewModel, and theme token suites).
- `fixtures/stock-app/`, `fixtures/patched-app/` — minimal manifest-only E2E fixture apps.

## UI & Visual Craft Guardrails

- Flat, high-density system instrument: rows sit directly on the canvas with `0.5.dp` hairline dividers (`60.dp` app rows, `56.dp` domain rows, `>= 48.dp` touch targets), `56.dp` fixed top bar, and `>= 10` visible app rows above the fold on a Pixel 8 viewport (`>= 8` with a banner). Avoid per-row cards, collapsible large top bars, bottom navigation, FABs, decorative hero banners, and UI emojis.
- System sans-serif for chrome/labels; monospace with tabular numerals (`tnum`) strictly for domains, package names, counts, and shell commands.
- `System`, `Light`, `Dark`, and `AMOLED Black` (`#000000`) neutral surfaces with Material You Dynamic Color opt-in (off by default).
- Map OS states to the 6-pill grammar (`Verified`, `Approved`, `Selected`, `Disabled`, `Denied`, `Declared`); never surface raw numeric codes like `1024` in pills, and never label read-only domains as `Disabled`.

## Build, Test & Verification

- **Toolchain:** JDK 21 (`jvmToolchain(21)`), Gradle Wrapper `8.11.1`, AGP `8.9.1`, Kotlin `2.1.20`, Compose BOM `2025.02.00`, `minSdk = 31`, `compileSdk = 36`, `targetSdk = 35`.
- **Windows AIDL quirk:** `app/build.gradle.kts` rewrites `\u` to `/u` in `AidlCompile` outputs so `javac` does not fail on `TEMP` paths containing `\Users`.
- **Release signing:** `assembleRelease` reads gitignored `signing.properties` (referencing `temp/signing/fliplink-release.jks`) and falls back to debug signing when absent.
- **Build & JVM unit tests:** `.\gradlew.bat testDebugUnitTest assembleDebug` (`./gradlew testDebugUnitTest assembleDebug` on Unix).
- **Live emulator E2E (`Pixel_8_API_35`):**
  1. Install `:app` debug APK, `:fixtures:stock-app`, `:fixtures:patched-app`, and the official Shizuku APK (cached under gitignored `temp/tools/shizuku.apk` when testing).
  2. Start Shizuku deterministically by extracting `lib/x86_64/libshizuku.so` to `/data/local/tmp/libshizuku.so` and running it via `adb shell`, then grant FlipLink Shizuku permission via UI interaction.
  3. Seed a stock `verified` conflict with `adb shell pm set-app-links --package dev.miles.fliplink.fixture.stock 1 test.fliplink.example`.
  4. Verify read-only mode (`Declared` pills + copyable ADB command execution), privileged toggles, one-tap `Take over` + `Undo` (asserted against `pm get-app-link-owners` and `pm get-app-links` on both fixtures), external-`adb` drift detection + `Re-apply all`, and visual layout across Light/Dark/AMOLED and `font_scale = 1.3`.
