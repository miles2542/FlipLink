package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConflictPlannerTest {

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
    fun computeConflicts_detects_stock_verified_outranking_patched_selected() {
        val stock = AppLinkInfo(
            packageName = "dev.miles.fliplink.fixture.stock",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = true,
            domains = listOf(
                entry("test.fliplink.example", isAutoVerify = true, state = VerificationState.Verified, userSelected = false),
                entry("alt.fliplink.example", isAutoVerify = true, state = VerificationState.None, userSelected = false),
            ),
        )
        val patched = AppLinkInfo(
            packageName = "dev.miles.fliplink.fixture.patched",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = false,
            domains = listOf(
                entry("test.fliplink.example", isAutoVerify = false, state = VerificationState.None, userSelected = true),
                entry("alt.fliplink.example", isAutoVerify = false, state = VerificationState.None, userSelected = true),
            ),
        )

        val conflicts = ConflictPlanner.computeConflicts(
            targetPackage = patched.packageName,
            allPackages = listOf(stock, patched),
        )

        assertEquals(1, conflicts.size)
        val conflict = conflicts.getValue("test.fliplink.example")
        assertTrue(conflict.isOutranked)
        assertTrue(conflict.isContested)
        assertEquals(ApprovalLevel.SELECTION, conflict.targetApprovalLevel)
        assertEquals(1, conflict.competitors.size)
        assertEquals(stock.packageName, conflict.competitors[0].packageName)
        assertEquals(ApprovalLevel.VERIFIED, conflict.competitors[0].approvalLevel)
    }

    @Test
    fun planTakeover_demotes_multi_competitors_promotes_target_and_builds_exact_undo() {
        val stockVerified = AppLinkInfo(
            packageName = "com.stock.verified",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = true,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = true, state = VerificationState.Verified, userSelected = true),
            ),
        )
        val competitorApproved = AppLinkInfo(
            packageName = "com.other.approved",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = true,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = true, state = VerificationState.Approved, userSelected = false),
            ),
        )
        val competitorSelected = AppLinkInfo(
            packageName = "com.third.selected",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = false,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = false, state = VerificationState.None, userSelected = true),
            ),
        )
        val targetPatched = AppLinkInfo(
            packageName = "com.target.patched",
            linkHandlingAllowed = false,
            hasAutoVerifyBlock = true,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = true, state = VerificationState.Error(1024), userSelected = false, allowed = false),
            ),
        )

        val allApps = listOf(stockVerified, competitorApproved, competitorSelected, targetPatched)
        val plan = ConflictPlanner.planTakeover(
            targetPackage = targetPatched.packageName,
            domains = listOf("shared.example.com"),
            allPackages = allApps,
        )

        assertEquals(
            setOf("com.other.approved", "com.stock.verified", "com.third.selected", "com.target.patched"),
            plan.modifiedPackages,
        )

        val formatted = AdbCommandBuilder.formatCommands(plan.commands, includeAdbPrefix = false)
        val lines = formatted.lines()
        assertEquals(
            listOf(
                "pm set-app-links --package com.other.approved 3 shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.other.approved false shared.example.com",
                "pm set-app-links --package com.stock.verified 3 shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.stock.verified false shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.third.selected false shared.example.com",
                "pm set-app-links-allowed --user 0 --package com.target.patched true",
                "pm set-app-links-user-selection --user 0 --package com.target.patched true shared.example.com",
                "pm set-app-links --package com.target.patched 2 shared.example.com",
            ),
            lines,
        )

        // Verify exact Undo commands restore prior states:
        // target -> allowed false, code 0 (Error 1024), user-selection false
        // com.other.approved -> code 2, user-selection false
        // com.stock.verified -> code 1 (STATE_SUCCESS), user-selection true
        // com.third.selected -> user-selection true
        val undoLines = AdbCommandBuilder.formatCommands(
            plan.undoSnapshot.restoreCommands,
            includeAdbPrefix = false,
        ).lines()
        assertEquals(
            listOf(
                "pm set-app-links-allowed --user 0 --package com.target.patched false",
                "pm set-app-links --package com.target.patched 0 shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.target.patched false shared.example.com",
                "pm set-app-links --package com.other.approved 2 shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.other.approved false shared.example.com",
                "pm set-app-links --package com.stock.verified 1 shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.stock.verified true shared.example.com",
                "pm set-app-links-user-selection --user 0 --package com.third.selected true shared.example.com",
            ),
            undoLines,
        )
    }

    @Test
    fun planTakeover_chunks_domains_over_40_per_command() {
        val domains = (1..85).map { "d$it.example.com" }
        val stock = AppLinkInfo(
            packageName = "com.example.stock",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = true,
            domains = domains.map {
                entry(it, isAutoVerify = true, state = VerificationState.Verified, userSelected = false)
            },
        )
        val patched = AppLinkInfo(
            packageName = "com.example.patched",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = false,
            domains = domains.map {
                entry(it, isAutoVerify = false, state = VerificationState.None, userSelected = false)
            },
        )

        val plan = ConflictPlanner.planTakeover(
            targetPackage = patched.packageName,
            domains = domains,
            allPackages = listOf(stock, patched),
        )

        // 85 domains -> 3 chunks (40, 40, 5)
        // stock: 3 set-app-links (3) + 3 set-app-links-user-selection (false) = 6
        // patched: 3 set-app-links-user-selection (true) = 3
        assertEquals(9, plan.commands.size)
        for (cmd in plan.commands) {
            val domainCount = cmd.args.count { it.endsWith(".example.com") }
            assertTrue("Expected <= 40 domains, got $domainCount", domainCount in 1..40)
        }
        assertFalse(plan.undoSnapshot.restoreCommands.isEmpty())
    }

    @Test
    fun computeConflicts_detects_denied_competitor_with_user_selection() {
        val competitor = AppLinkInfo(
            packageName = "com.example.competitor",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = true,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = true, state = VerificationState.Denied, userSelected = true),
            ),
        )
        val target = AppLinkInfo(
            packageName = "com.example.target",
            linkHandlingAllowed = true,
            hasAutoVerifyBlock = false,
            domains = listOf(
                entry("shared.example.com", isAutoVerify = false, state = VerificationState.None, userSelected = false),
            ),
        )

        val conflicts = ConflictPlanner.computeConflicts(target.packageName, listOf(competitor, target))
        assertEquals(1, conflicts.size)
        val conflict = conflicts.getValue("shared.example.com")
        assertEquals(ApprovalLevel.SELECTION, conflict.competitors[0].approvalLevel)
    }
}
