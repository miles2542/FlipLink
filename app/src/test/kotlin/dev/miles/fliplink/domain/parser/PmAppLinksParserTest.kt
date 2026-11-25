package dev.miles.fliplink.domain.parser

import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.VerificationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PmAppLinksParserTest {

    @Test
    fun parseAll_handles_formA_and_formB_across_android15_and_magicos17() {
        val output = """
              com.google.android.youtube:
                ID: 12345678-1234-1234-1234-1234567890ab
                Signatures: [AB:CD:EF:01]
                Domain verification state:
                  youtu.be: verified
                  www.youtube.com: 1024
                  m.youtube.com: denied
                  tv.youtube.com: approved
                  studio.youtube.com: migrated
                  kids.youtube.com: restored
                  legacy.youtube.com: legacy_failure
                  sys.youtube.com: system_configured
                  pre.youtube.com: pre_verified
                  custom.youtube.com: unknown_oem_state
                User 0:
                  Verification link handling allowed: true
                  Selection state:
                    Enabled:
                      music.youtube.com
                      *.youtube.com
                    Disabled:
                      youtube.com
              app.morphe.android.youtube aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee:
                User 0:
                  Verification link handling allowed: false
                  Selection state:
                    Enabled:
                      www.youtube.com
                      *.discord.com
                    Disabled:
                      youtu.be
        """.trimIndent()

        val apps = PmAppLinksParser.parseAll(output)
        assertEquals(2, apps.size)

        val stock = apps[0]
        assertEquals("com.google.android.youtube", stock.packageName)
        assertEquals("12345678-1234-1234-1234-1234567890ab", stock.id)
        assertEquals(listOf("AB:CD:EF:01"), stock.signatures)
        assertTrue(stock.hasAutoVerifyBlock)
        assertTrue(stock.linkHandlingAllowed)

        val stockDomains = stock.domains.associateBy { it.domain }
        assertEquals(VerificationState.Verified, stockDomains.getValue("youtu.be").verificationState)
        assertEquals(DomainStatus.Verified, stockDomains.getValue("youtu.be").status)
        assertEquals(ApprovalLevel.VERIFIED, stockDomains.getValue("youtu.be").approvalLevel)

        val err1024 = stockDomains.getValue("www.youtube.com")
        assertEquals(VerificationState.Error(1024, "1024"), err1024.verificationState)
        assertEquals(DomainStatus.Disabled, err1024.status)
        assertEquals(ApprovalLevel.NONE, err1024.approvalLevel)

        val denied = stockDomains.getValue("m.youtube.com")
        assertEquals(VerificationState.Denied, denied.verificationState)
        assertEquals(DomainStatus.Denied, denied.status)
        assertEquals(ApprovalLevel.NONE, denied.approvalLevel)

        assertEquals(DomainStatus.Approved, stockDomains.getValue("tv.youtube.com").status)
        assertEquals(DomainStatus.Approved, stockDomains.getValue("studio.youtube.com").status)
        assertEquals(DomainStatus.Approved, stockDomains.getValue("kids.youtube.com").status)
        assertEquals(DomainStatus.Approved, stockDomains.getValue("sys.youtube.com").status)
        assertEquals(DomainStatus.Approved, stockDomains.getValue("pre.youtube.com").status)
        assertEquals(DomainStatus.Disabled, stockDomains.getValue("legacy.youtube.com").status)
        assertEquals(
            VerificationState.Error(-1, "unknown_oem_state"),
            stockDomains.getValue("custom.youtube.com").verificationState,
        )
        assertEquals(DomainStatus.Disabled, stockDomains.getValue("custom.youtube.com").status)

        val wildcard = stockDomains.getValue("*.youtube.com")
        assertFalse(wildcard.isAutoVerify)
        assertTrue(wildcard.userSelected)
        assertEquals(DomainStatus.Selected, wildcard.status)
        assertEquals(ApprovalLevel.SELECTION, wildcard.approvalLevel)

        val disabledSel = stockDomains.getValue("youtube.com")
        assertFalse(disabledSel.userSelected)
        assertEquals(DomainStatus.Disabled, disabledSel.status)

        val patched = apps[1]
        assertEquals("app.morphe.android.youtube", patched.packageName)
        assertEquals("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", patched.id)
        assertFalse(patched.hasAutoVerifyBlock)
        assertFalse(patched.linkHandlingAllowed)

        val patchedDomains = patched.domains.associateBy { it.domain }
        assertEquals(DomainStatus.Selected, patchedDomains.getValue("www.youtube.com").status)
        assertEquals(ApprovalLevel.NONE, patchedDomains.getValue("www.youtube.com").approvalLevel)
        assertEquals(DomainStatus.Selected, patchedDomains.getValue("*.discord.com").status)
        assertEquals(DomainStatus.Disabled, patchedDomains.getValue("youtu.be").status)
    }

    @Test
    fun parseSingle_handles_formC_headerless_output() {
        val output = """
                User 0:
                  Verification link handling allowed: true
                  Selection state:
                    Enabled:
                      test.fliplink.example
                      *.fliplink.example
                    Disabled:
                      alt.fliplink.example
        """.trimIndent()

        val parsed = PmAppLinksParser.parseSingle(output, expectedPackageName = "dev.miles.fliplink.fixture.patched")
        assertNotNull(parsed)
        requireNotNull(parsed)
        assertEquals("dev.miles.fliplink.fixture.patched", parsed.packageName)
        assertTrue(parsed.linkHandlingAllowed)
        assertFalse(parsed.hasAutoVerifyBlock)

        val byDomain = parsed.domains.associateBy { it.domain }
        assertEquals(3, byDomain.size)
        assertTrue(byDomain.getValue("test.fliplink.example").userSelected)
        assertEquals(DomainStatus.Selected, byDomain.getValue("*.fliplink.example").status)
        assertFalse(byDomain.getValue("alt.fliplink.example").userSelected)
        assertEquals(DomainStatus.Disabled, byDomain.getValue("alt.fliplink.example").status)
    }

    @Test
    fun parseAll_returns_empty_on_blank_input() {
        assertTrue(PmAppLinksParser.parseAll("").isEmpty())
        assertTrue(PmAppLinksParser.parseAll("   \n  ").isEmpty())
        assertNull(PmAppLinksParser.parseSingle("", "com.example.app"))
    }

    @Test
    fun parseAppLinkOwners_parses_multiple_owner_formats() {
        val output = """
            Domain: youtu.be
              [0] com.google.android.youtube (VERIFIED)
              [1] app.morphe.android.youtube (SELECTION)
              [2] com.example.other (NONE)
            Domain: example.com
              VERIFIED:
                com.example.stock
              SELECTION:
                com.example.patched
        """.trimIndent()

        val owners = PmAppLinksParser.parseAppLinkOwners(output)
        assertEquals(
            listOf(
                DomainOwner("com.google.android.youtube", ApprovalLevel.VERIFIED),
                DomainOwner("app.morphe.android.youtube", ApprovalLevel.SELECTION),
                DomainOwner("com.example.other", ApprovalLevel.NONE),
            ),
            owners["youtu.be"],
        )
        assertEquals(
            listOf(
                DomainOwner("com.example.stock", ApprovalLevel.VERIFIED),
                DomainOwner("com.example.patched", ApprovalLevel.SELECTION),
            ),
            owners["example.com"],
        )
    }
}
