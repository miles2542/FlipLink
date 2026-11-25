package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.PmCommand

object AdbCommandBuilder {
    private val UNQUOTED_TOKEN_REGEX = Regex("^[a-zA-Z0-9._:/=-]+$")

    fun formatCommand(
        command: PmCommand,
        includeAdbPrefix: Boolean = true,
    ): String {
        val tokens = if (includeAdbPrefix) {
            listOf("adb", "shell") + command.pmArgv
        } else {
            command.pmArgv
        }
        return tokens.joinToString(" ") { quoteIfNeeded(it) }
    }

    fun formatCommands(
        commands: List<PmCommand>,
        includeAdbPrefix: Boolean = true,
        separator: String = "\n",
    ): String {
        return commands.joinToString(separator) { formatCommand(it, includeAdbPrefix) }
    }

    fun buildUserSelectionCommand(
        packageName: String,
        enabled: Boolean,
        domains: Collection<String>,
    ): List<PmCommand> {
        InputValidator.requireValidPackageName(packageName)
        return InputValidator.chunkDomains(domains).map { chunk ->
            InputValidator.validatePmCommand(
                "set-app-links-user-selection",
                listOf("--user", "0", "--package", packageName, enabled.toString()) + chunk,
            )
        }
    }

    fun buildUserSelectionAllCommand(
        packageName: String,
        enabled: Boolean,
    ): PmCommand {
        InputValidator.requireValidPackageName(packageName)
        return InputValidator.validatePmCommand(
            "set-app-links-user-selection",
            listOf("--user", "0", "--package", packageName, enabled.toString(), "all"),
        )
    }

    fun buildLinkHandlingAllowedCommand(
        packageName: String,
        allowed: Boolean,
    ): PmCommand {
        InputValidator.requireValidPackageName(packageName)
        return InputValidator.validatePmCommand(
            "set-app-links-allowed",
            listOf("--user", "0", "--package", packageName, allowed.toString()),
        )
    }

    private fun quoteIfNeeded(token: String): String {
        if (UNQUOTED_TOKEN_REGEX.matches(token)) return token
        val escaped = token.replace("'", "'\\''")
        return "'$escaped'"
    }
}
