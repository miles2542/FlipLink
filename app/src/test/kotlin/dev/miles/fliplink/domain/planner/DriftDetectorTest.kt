package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.DriftItem
import dev.miles.fliplink.domain.model.FlipLinkSnapshot
import dev.miles.fliplink.domain.model.PackageDesiredState
import dev.miles.fliplink.domain.model.VerificationState
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftDetectorTest {

    private fun entry(
        domain: String,
        isAutoVerify: Boolean,
        state: VerificationState,
        userSelected: Boolean,
        allowed: Boolean = true,
    ) = DomainEntry(
        domain = domain,
        isAutoVerify = isAutoVerify,
        verificationState = state,
        userSelected = userSelected,
        status = DomainEntry.computeStatus(state, userSelected),
        approvalLevel = DomainEntry.computeApprovalLevel(allowed, state, userSelected),
    )

    @Test
    fun computeDrift_emits_all_four_drift_types_and_ignores_uninstalled_packages() {
        val desired = FlipLinkSnapshot(
            schemaVersion = 1,
            packages = mapOf(
                "com.example.target" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("a.example.com", "removed.example.com"),
                    approvedAutoVerifyDomains = setOf("b.example.com"),
                    updatedAtEpochMs = 1000L,
                ),
                "com.example.competitor" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    deniedDomains = setOf("a.example.com", "nonav.example.com"),
                    updatedAtEpochMs = 1000L,
                ),
                "com.example.uninstalled" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("ghost.example.com"),
                    updatedAtEpochMs = 1000L,
                ),
            ),
        )

        val live = mapOf(
            "com.example.target" to AppLinkInfo(
                packageName = "com.example.target",
                linkHandlingAllowed = false,
                hasAutoVerifyBlock = true,
                domains = listOf(
                    entry("a.example.com", isAutoVerify = false, state = VerificationState.None, userSelected = false, allowed = false),
                    entry("b.example.com", isAutoVerify = true, state = VerificationState.None, userSelected = true, allowed = false),
                ),
            ),
            "com.example.competitor" to AppLinkInfo(
                packageName = "com.example.competitor",
                linkHandlingAllowed = true,
                hasAutoVerifyBlock = true,
                domains = listOf(
                    entry("a.example.com", isAutoVerify = true, state = VerificationState.Verified, userSelected = false),
                    entry("nonav.example.com", isAutoVerify = false, state = VerificationState.None, userSelected = false),
                ),
            ),
        )

        val drifts = DriftDetector.computeDrift(desired, live)
        assertEquals(2, drifts.size)

        val compDrift = drifts[0]
        assertEquals("com.example.competitor", compDrift.packageName)
        assertEquals(listOf(DriftItem.CompetitorReclaimed(setOf("a.example.com"))), compDrift.items)

        val targetDrift = drifts[1]
        assertEquals("com.example.target", targetDrift.packageName)
        assertEquals(
            listOf(
                DriftItem.LinkHandlingDisabled,
                DriftItem.SelectionLost(setOf("a.example.com")),
                DriftItem.VerificationLost(setOf("b.example.com")),
            ),
            targetDrift.items,
        )

        val reapplyLines = AdbCommandBuilder.formatCommands(
            DriftDetector.buildReapplyPlan(drifts),
            includeAdbPrefix = false,
        ).lines()
        assertEquals(
            listOf(
                "pm set-app-links --package com.example.competitor 3 a.example.com",
                "pm set-app-links-user-selection --user 0 --package com.example.competitor false a.example.com",
                "pm set-app-links-allowed --user 0 --package com.example.target true",
                "pm set-app-links-user-selection --user 0 --package com.example.target true a.example.com",
                "pm set-app-links --package com.example.target 2 b.example.com",
            ),
            reapplyLines,
        )
    }

    @Test
    fun computeDrift_returns_empty_when_live_matches_desired() {
        val desired = FlipLinkSnapshot(
            packages = mapOf(
                "com.example.app" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("example.com"),
                    approvedAutoVerifyDomains = setOf("verify.example.com"),
                    deniedDomains = setOf("denied.example.com"),
                    updatedAtEpochMs = 42L,
                ),
            ),
        )
        val live = mapOf(
            "com.example.app" to AppLinkInfo(
                packageName = "com.example.app",
                linkHandlingAllowed = true,
                hasAutoVerifyBlock = true,
                domains = listOf(
                    entry("example.com", isAutoVerify = false, state = VerificationState.None, userSelected = true),
                    entry("verify.example.com", isAutoVerify = true, state = VerificationState.Approved, userSelected = true),
                    entry("denied.example.com", isAutoVerify = true, state = VerificationState.Denied, userSelected = false),
                ),
            ),
        )

        assertTrue(DriftDetector.computeDrift(desired, live).isEmpty())
    }

    @Test
    fun flipLinkSnapshot_roundtrips_with_kotlinx_serialization() {
        val snapshot = FlipLinkSnapshot(
            schemaVersion = 1,
            packages = mapOf(
                "dev.miles.fliplink.fixture.patched" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("test.fliplink.example", "*.fliplink.example"),
                    deniedDomains = emptySet(),
                    approvedAutoVerifyDomains = emptySet(),
                    updatedAtEpochMs = 1732512345678L,
                ),
            ),
        )

        val encoded = Json.encodeToString(snapshot)
        val decoded = Json.decodeFromString<FlipLinkSnapshot>(encoded)
        assertEquals(snapshot, decoded)
    }
}
