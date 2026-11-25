# FlipLink

Android utility (`dev.miles.fliplink`) that restores native per-domain "Open by default" control on Android 12+ OEM ROMs where system settings hide per-domain toggles (built primarily for Honor MagicOS 11 / Android 17). Public solo repository (`miles2542/FlipLink`), personal-first and open-source as-is.

Scope is strictly single-user (`--user 0`) and Shizuku-first with a read-only + copyable `adb shell` fallback. Root (`su`), background install notifications, multi-user/clone profiles, and Share-sheet or default-browser interception are out of scope.

## Architecture & Invariants

- Single production module (`:app`) with manual `AppContainer` DI (no Hilt, KSP, Room, or Coil), plus two minimal manifest-only E2E fixture modules (`fixtures/stock-app` with `autoVerify="true"` and `fixtures/patched-app` without `autoVerify`).
- `dev.miles.fliplink/domain/{model,parser,planner}` stays pure Kotlin with zero `android.*` imports so parser, conflict planner, drift detector, and ADB command builder run fast on the JVM.
- `dev.miles.fliplink/data/{shizuku,pm,storage}` + `AppLinksRepository.kt` own the Shizuku `UserService` boundary (`dev.rikka.shizuku:{api,provider}:13.1.5`), `PackageDiscovery` (left-joined with `pm` output, filtering out scheme-only browsers), and atomic JSON `SnapshotStore` (`filesDir/fliplink_snapshot_v1.json`, plus SAF export/import).
- `dev.miles.fliplink/ui/{theme,components,applist,appdetail,sheets}` implements a 2-screen navigation stack (`AppListScreen` -> `AppDetailScreen`) and 4 modal sheets (`ShizukuStatusSheet`, `DriftReviewSheet`, `AdbCommandSheet`, `SettingsSheet`).

### Domain Verification & Shizuku Rules

- Execute `pm` inside `ShellUserService` (`IShellService.aidl`) via `ProcessBuilder(argv)` with strict subcommand, package, and domain allowlists, 15s command timeout, and `<= 40`-domain batching; never invoke `sh -c`.
- `PmAppLinksParser` must handle all three `pm get-app-links --user 0` output forms observed across Android 12–17: Form A (standard `autoVerify` block with `Domain verification state:`), Form B (`<pkg> <uuid>:` without `autoVerify` block, common on patched apps), and Form C (headerless single-package query starting at `User 0:`). Map unknown states or numeric codes `>= 1024` to `VerificationState.Error(code)` without throwing.
- Compute domain conflicts locally from a single `pm get-app-links --user 0` call using AOSP approval tiers (`VERIFIED = 4`, `SELECTION = 3`, `NONE = 0`); reserve `pm get-app-link-owners` for targeted verification and E2E checks.
- Resolve patched-vs-stock conflicts per-domain without touching a competitor's master switch: demote each competitor via `pm set-app-links --package <C> 3 <domains>` (when `autoVerify`) + `pm set-app-links-user-selection --user 0 --package <C> false <domains>`, then promote the target via `set-app-links-allowed true`, `set-app-links-user-selection true`, and `set-app-links 2` (when `autoVerify`). Record competitor demotions in `deniedDomains` so drift detection catches system re-verification.
- Serialize every mutation and its mandatory post-mutation `pm get-app-links` re-read through a single `Mutex` in `AppLinksRepository` so UI state always reflects live OS truth.

### UI & Visual Craft Guardrails

- Design as a flat, high-density system instrument: rows sit directly on the canvas with `0.5.dp` hairline dividers (`60.dp` app rows, `56.dp` domain rows, `>= 48.dp` touch targets), `56.dp` fixed top bar, and `>= 10` visible app rows above the fold on a Pixel 8 viewport (`>= 8` with a banner).
- Use system sans-serif for chrome/labels and monospace with tabular numerals (`tnum`) strictly for domains, package names, counts, and shell commands.
- Support `System`, `Light`, `Dark`, and `AMOLED Black` (`#000000`) neutral surfaces with Material You Dynamic Color opt-in (off by default).
- Map OS states to the 6-pill grammar (`Verified`, `Approved`, `Selected`, `Disabled`, `Denied`, `Declared`); never surface raw numeric codes like `1024` in pills, and never label read-only domains as `Disabled`.
- Keep chrome minimal: avoid per-row cards, collapsible large top bars, bottom navigation, FABs, decorative hero banners, and UI emojis.

## Build, Test & Verification

- Toolchain: JDK 21 (`jvmToolchain(21)`), Gradle Wrapper `8.11.1`, AGP `8.9.1`, Kotlin `2.1.20`, Compose BOM `2025.02.00`, `minSdk = 31`, `compileSdk = 36`, `targetSdk = 35`.
- Build & JVM unit tests: `./gradlew assembleDebug testDebugUnitTest` (PowerShell: `.\gradlew.bat assembleDebug testDebugUnitTest`).
- Live emulator E2E (`Pixel_8_API_35`):
  1. Install `:app` debug APK, `fixtures/stock-app`, `fixtures/patched-app`, and the official Shizuku APK (cached under gitignored `temp/tools/shizuku.apk`).
  2. Start Shizuku deterministically by extracting `lib/x86_64/libshizuku.so` to `/data/local/tmp/libshizuku.so` and running it via `adb shell`, then grant FlipLink Shizuku permission via UI interaction.
  3. Seed a stock `verified` conflict with `adb shell pm set-app-links --package dev.miles.fliplink.fixture.stock 1 test.fliplink.example`.
  4. Verify read-only mode (`Declared` pills + copyable ADB command execution), privileged toggles, one-tap `Take over` + `Undo` (asserted against `pm get-app-link-owners` and `pm get-app-links` on both fixtures), external-`adb` drift detection + `Re-apply all`, and visual layout across Light/Dark/AMOLED and `font_scale = 1.3`.
