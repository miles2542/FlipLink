package dev.miles.fliplink

import android.content.Context
import dev.miles.fliplink.data.AppLinksRepository
import dev.miles.fliplink.data.pm.PackageDiscovery
import dev.miles.fliplink.data.shizuku.ShellExecutor
import dev.miles.fliplink.data.shizuku.ShizukuBridge
import dev.miles.fliplink.data.shizuku.ShizukuShellExecutor
import dev.miles.fliplink.data.storage.PreferencesStore
import dev.miles.fliplink.data.storage.SnapshotStore

class AppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext

    val shizukuBridge: ShizukuBridge by lazy {
        ShizukuBridge(applicationContext)
    }

    val shellExecutor: ShellExecutor by lazy {
        ShizukuShellExecutor(shizukuBridge)
    }

    val packageDiscovery: PackageDiscovery by lazy {
        PackageDiscovery(applicationContext)
    }

    val snapshotStore: SnapshotStore by lazy {
        SnapshotStore(applicationContext.filesDir)
    }

    val preferencesStore: PreferencesStore by lazy {
        PreferencesStore(applicationContext)
    }

    val repository: AppLinksRepository by lazy {
        AppLinksRepository(
            packageDiscovery = packageDiscovery,
            shellExecutor = shellExecutor,
            snapshotStore = snapshotStore,
            shizukuState = shizukuBridge.state,
        )
    }
}
