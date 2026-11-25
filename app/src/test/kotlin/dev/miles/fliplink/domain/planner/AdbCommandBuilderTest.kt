package dev.miles.fliplink.domain.planner

import org.junit.Assert.assertEquals
import org.junit.Test

class AdbCommandBuilderTest {

    @Test
    fun formatCommand_toggles_adb_prefix_and_quotes_wildcards() {
        val cmds = AdbCommandBuilder.buildUserSelectionCommand(
            packageName = "app.morphe.android.youtube",
            enabled = true,
            domains = listOf("youtu.be", "*.youtube.com"),
        )
        assertEquals(1, cmds.size)

        assertEquals(
            "adb shell pm set-app-links-user-selection --user 0 --package app.morphe.android.youtube true youtu.be '*.youtube.com'",
            AdbCommandBuilder.formatCommand(cmds[0], includeAdbPrefix = true),
        )
        assertEquals(
            "pm set-app-links-user-selection --user 0 --package app.morphe.android.youtube true youtu.be '*.youtube.com'",
            AdbCommandBuilder.formatCommand(cmds[0], includeAdbPrefix = false),
        )
    }

    @Test
    fun convenience_builders_produce_valid_commands() {
        val allCmd = AdbCommandBuilder.buildUserSelectionAllCommand("com.example.app", enabled = false)
        assertEquals(
            "adb shell pm set-app-links-user-selection --user 0 --package com.example.app false all",
            AdbCommandBuilder.formatCommand(allCmd, includeAdbPrefix = true),
        )

        val allowedCmd = AdbCommandBuilder.buildLinkHandlingAllowedCommand("com.example.app", allowed = true)
        assertEquals(
            "adb shell pm set-app-links-allowed --user 0 --package com.example.app true",
            AdbCommandBuilder.formatCommand(allowedCmd, includeAdbPrefix = true),
        )
    }
}
