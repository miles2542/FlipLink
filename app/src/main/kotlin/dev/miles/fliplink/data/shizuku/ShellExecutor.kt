package dev.miles.fliplink.data.shizuku

import android.os.DeadObjectException
import android.os.RemoteException
import dev.miles.fliplink.domain.model.PmCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ShellExecutor {
    suspend fun execPm(command: PmCommand): ShellResult
}

class ShizukuShellExecutor(
    private val bridge: ShizukuBridge,
) : ShellExecutor {

    override suspend fun execPm(command: PmCommand): ShellResult = withContext(Dispatchers.IO) {
        val service = bridge.currentService()
            ?: return@withContext ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Shizuku shell service is not bound",
            )

        try {
            service.execPm(command.subcommandArgv.toTypedArray())
        } catch (e: DeadObjectException) {
            bridge.recheck()
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Shizuku service died",
            )
        } catch (e: RemoteException) {
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Shizuku IPC failed",
            )
        } catch (e: SecurityException) {
            bridge.recheck()
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Shizuku permission denied",
            )
        }
    }
}
