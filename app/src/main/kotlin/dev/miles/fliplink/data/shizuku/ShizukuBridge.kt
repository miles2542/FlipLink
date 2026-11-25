package dev.miles.fliplink.data.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

class ShizukuBridge(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val lock = Any()

    private val _state = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var shellService: IShellService? = null

    @Volatile
    private var isBinding = false

    @Volatile
    private var lastError: String? = null

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        val isDebuggable = (appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        Shizuku.UserServiceArgs(ComponentName(appContext, ShellUserService::class.java))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(isDebuggable)
            .version(USER_SERVICE_VERSION)
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            synchronized(lock) {
                isBinding = false
                if (binder == null || !binder.pingBinder()) {
                    shellService = null
                    lastError = "Received null or dead Shizuku UserService binder"
                    updateStateLocked()
                    return
                }
                shellService = IShellService.Stub.asInterface(binder)
                lastError = null
                updateStateLocked()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(lock) {
                isBinding = false
                shellService = null
                updateStateLocked()
            }
        }

        override fun onBindingDied(name: ComponentName?) {
            synchronized(lock) {
                isBinding = false
                shellService = null
                lastError = "Shizuku UserService binding died"
                updateStateLocked()
            }
        }

        override fun onNullBinding(name: ComponentName?) {
            synchronized(lock) {
                isBinding = false
                shellService = null
                lastError = "Shizuku UserService returned null binding"
                updateStateLocked()
            }
        }
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        synchronized(lock) {
            lastError = null
            updateStateLocked()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        synchronized(lock) {
            isBinding = false
            shellService = null
            updateStateLocked()
        }
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode != REQUEST_CODE_PERMISSION) return@OnRequestPermissionResultListener
            synchronized(lock) {
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    lastError = null
                }
                updateStateLocked()
            }
        }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        recheck()
    }

    fun currentService(): IShellService? = shellService

    fun recheck() {
        synchronized(lock) {
            lastError = null
            updateStateLocked()
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            recheck()
            return
        }
        if (Shizuku.isPreV11()) {
            synchronized(lock) {
                lastError = "Shizuku version is too old (pre-v11)"
                updateStateLocked()
            }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            recheck()
            return
        }
        try {
            Shizuku.requestPermission(REQUEST_CODE_PERMISSION)
        } catch (e: Throwable) {
            synchronized(lock) {
                lastError = e.message ?: "Failed to request Shizuku permission"
                updateStateLocked()
            }
        }
    }

    fun release() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        synchronized(lock) {
            isBinding = false
            shellService = null
            try {
                if (Shizuku.pingBinder()) {
                    Shizuku.unbindUserService(userServiceArgs, serviceConnection, true)
                }
            } catch (_: Throwable) {
                // Ignore unbind errors during teardown
            }
        }
    }

    private fun updateStateLocked() {
        if (!Shizuku.pingBinder()) {
            isBinding = false
            shellService = null
            _state.value = if (isShizukuAppInstalled()) {
                ShizukuState.NotRunning
            } else {
                ShizukuState.NotInstalled
            }
            return
        }

        if (Shizuku.isPreV11()) {
            _state.value = ShizukuState.Error("Shizuku version is too old (pre-v11)")
            return
        }

        val granted = try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            _state.value = ShizukuState.Error(e.message ?: "Failed to check Shizuku permission")
            return
        }

        if (!granted) {
            shellService = null
            isBinding = false
            _state.value = ShizukuState.PermissionRequired
            return
        }

        val bound = shellService
        if (bound != null && bound.asBinder().pingBinder()) {
            _state.value = try {
                ShizukuState.Ready(
                    version = Shizuku.getVersion(),
                    uid = Shizuku.getUid(),
                )
            } catch (e: Throwable) {
                ShizukuState.Error(e.message ?: "Failed to query Shizuku metadata")
            }
            return
        }

        shellService = null
        val err = lastError
        if (err != null) {
            _state.value = ShizukuState.Error(err)
            return
        }

        _state.value = ShizukuState.Binding
        if (!isBinding) {
            isBinding = true
            try {
                Shizuku.bindUserService(userServiceArgs, serviceConnection)
            } catch (e: Throwable) {
                isBinding = false
                _state.value = ShizukuState.Error(e.message ?: "Failed to bind Shizuku UserService")
            }
        }
    }

    private fun isShizukuAppInstalled(): Boolean {
        return try {
            appContext.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE_PERMISSION = 1001
        private const val USER_SERVICE_VERSION = 1
    }
}
