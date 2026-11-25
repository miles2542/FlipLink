package dev.miles.fliplink.data.shizuku

sealed interface ShizukuState {
    data object NotInstalled : ShizukuState
    data object NotRunning : ShizukuState
    data object PermissionRequired : ShizukuState
    data object Binding : ShizukuState
    data class Ready(val version: Int, val uid: Int) : ShizukuState
    data class Error(val message: String) : ShizukuState

    val isReady: Boolean
        get() = this is Ready
}
