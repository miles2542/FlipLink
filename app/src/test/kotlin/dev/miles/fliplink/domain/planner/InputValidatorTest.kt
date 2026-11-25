package dev.miles.fliplink.domain.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    @Test
    fun packageName_validation_accepts_valid_and_rejects_malicious() {
        assertTrue(InputValidator.isValidPackageName("dev.miles.fliplink"))
        assertTrue(InputValidator.isValidPackageName("app.morphe.android.youtube"))
        assertTrue(InputValidator.isValidPackageName("com.example.app_2"))

        assertFalse(InputValidator.isValidPackageName(""))
        assertFalse(InputValidator.isValidPackageName("singleword"))
        assertFalse(InputValidator.isValidPackageName("-com.example.app"))
        assertFalse(InputValidator.isValidPackageName("com.example.app;rm -rf /"))
        assertFalse(InputValidator.isValidPackageName("com.example.app\n"))
        assertFalse(InputValidator.isValidPackageName("com.example.app "))
    }

    @Test
    fun domain_validation_handles_wildcards_and_explicit_all_opt_in() {
        assertTrue(InputValidator.isValidDomain("youtu.be"))
        assertTrue(InputValidator.isValidDomain("*.youtube.com"))
        assertTrue(InputValidator.isValidDomain("sub-1.test.fliplink.example"))

        assertFalse(InputValidator.isValidDomain("all"))
        assertTrue(InputValidator.isValidDomain("all", allowAllLiteral = true))

        assertFalse(InputValidator.isValidDomain("-evil.com"))
        assertFalse(InputValidator.isValidDomain("example.com;id"))
        assertFalse(InputValidator.isValidDomain("example.com\$(whoami)"))
        assertFalse(InputValidator.isValidDomain("example.com\t"))
        assertFalse(InputValidator.isValidDomain("localhost"))
        assertFalse(InputValidator.isValidDomain("*.*.example.com"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validatePmCommand_rejects_disallowed_subcommand() {
        InputValidator.validatePmCommand("uninstall", listOf("com.example.app"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun validatePmCommand_rejects_shell_metacharacters_in_domain() {
        InputValidator.validatePmCommand(
            "set-app-links-user-selection",
            listOf("--user", "0", "--package", "com.example.app", "true", "example.com;id"),
        )
    }

    @Test
    fun chunkDomains_deduplicates_and_splits_at_40() {
        val input = (1..45).map { "d$it.example.com" } + listOf("d1.example.com")
        val chunks = InputValidator.chunkDomains(input)
        assertEquals(2, chunks.size)
        assertEquals(40, chunks[0].size)
        assertEquals(5, chunks[1].size)
    }
}
