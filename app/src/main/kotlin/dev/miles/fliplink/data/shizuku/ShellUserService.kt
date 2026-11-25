package dev.miles.fliplink.data.shizuku

import dev.miles.fliplink.domain.planner.InputValidator
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class ShellUserService : IShellService.Stub() {

    override fun execPm(args: Array<String>?): ShellResult {
        if (args == null || args.isEmpty()) {
            return ShellResult(exitCode = -1, stdout = "", stderr = "Empty pm arguments")
        }

        val validated = try {
            InputValidator.validatePmCommand(
                subcommand = args[0],
                args = args.drop(1),
            )
        } catch (e: IllegalArgumentException) {
            return ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Invalid pm arguments",
            )
        }

        val argv = listOf("pm") + validated.subcommandArgv
        return try {
            val process = ProcessBuilder(argv)
                .redirectErrorStream(false)
                .start()

            var stdout = ""
            var stderr = ""
            val outThread = thread(start = true, name = "pm-stdout") {
                stdout = readFully(process.inputStream)
            }
            val errThread = thread(start = true, name = "pm-stderr") {
                stderr = readFully(process.errorStream)
            }

            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(1_000)
                errThread.join(1_000)
                return ShellResult(
                    exitCode = -1,
                    stdout = stdout,
                    stderr = "Command timed out after ${COMMAND_TIMEOUT_SECONDS}s",
                )
            }

            outThread.join(1_000)
            errThread.join(1_000)
            ShellResult(
                exitCode = process.exitValue(),
                stdout = stdout,
                stderr = stderr,
            )
        } catch (e: IOException) {
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Failed to execute pm",
            )
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            ShellResult(
                exitCode = -1,
                stdout = "",
                stderr = "Command execution interrupted",
            )
        }
    }

    override fun destroy() {
        exitProcess(0)
    }

    private fun readFully(stream: InputStream): String {
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private companion object {
        const val COMMAND_TIMEOUT_SECONDS = 15L
    }
}
