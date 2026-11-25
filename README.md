# FlipLink

Android utility (`dev.miles.fliplink`) that restores per-domain **Open by default** link control on Android 12+ OEM ROMs where system settings hide per-domain toggles.

## Overview

- **Single-user (`--user 0`)**: Manages web link verification, user domain selections, and domain ownership conflicts.
- **Shizuku-first**: Executes `pm` commands directly via a Shizuku `UserService` (`ProcessBuilder`, no shell interpolation), with a read-only mode and copyable `adb shell` commands when Shizuku is unavailable.
- **Conflict resolution & drift detection**: Resolves competing domain owners per-domain without altering unrelated domains or master switches, and tracks local desired state (`fliplink_snapshot_v1.json`) to detect external system changes.

## Requirements

- Android 12+ (`minSdk 31`, `targetSdk 35`)
- [Shizuku](https://shizuku.rikka.app/) for in-app mutations (optional; read-only + `adb` fallback works without it)

## Building

```bash
./gradlew assembleDebug testDebugUnitTest
```

## License

MIT — see [LICENSE](LICENSE).
