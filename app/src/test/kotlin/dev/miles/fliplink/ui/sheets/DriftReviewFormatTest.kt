package dev.miles.fliplink.ui.sheets

import dev.miles.fliplink.domain.model.AppDrift
import dev.miles.fliplink.domain.model.DriftItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftReviewFormatTest {

    @Test
    fun formatDriftDomainsSortsAndTruncatesBeyondThree() {
        assertEquals(
            "a.com, b.com, c.com",
            formatDriftDomains(setOf("c.com", "a.com", "b.com")),
        )
        assertEquals(
            "a.com, b.com, c.com +2 more",
            formatDriftDomains(setOf("e.com", "a.com", "d.com", "c.com", "b.com")),
        )
    }

    @Test
    fun formatDriftItemLineFormatsAllVariantsAndFlagsCompetitorReclaimed() {
        val master = formatDriftItemLine(DriftItem.LinkHandlingDisabled)
        assertEquals("Master switch turned off", master.text)
        assertFalse(master.isWarning)

        val selection = formatDriftItemLine(DriftItem.SelectionLost(setOf("alt.fliplink.example")))
        assertEquals("Unselected: alt.fliplink.example", selection.text)
        assertFalse(selection.isWarning)

        val verification = formatDriftItemLine(DriftItem.VerificationLost(setOf("a.example", "b.example")))
        assertEquals("Approval lost: a.example, b.example", verification.text)
        assertFalse(verification.isWarning)

        val reclaimed = formatDriftItemLine(DriftItem.CompetitorReclaimed(setOf("test.fliplink.example")))
        assertEquals("Reclaimed: test.fliplink.example (re-deny)", reclaimed.text)
        assertTrue(reclaimed.isWarning)
    }

    @Test
    fun formatDriftBannerSummaryCountsDistinctDomainsAndApps() {
        assertNull(formatDriftBannerSummary(emptyList()))

        val singleMasterOnly = listOf(
            AppDrift(
                packageName = "com.example.one",
                items = listOf(DriftItem.LinkHandlingDisabled),
            ),
        )
        assertEquals("Link settings changed in 1 app", formatDriftBannerSummary(singleMasterOnly))

        val multi = listOf(
            AppDrift(
                packageName = "dev.miles.fliplink.fixture.patched",
                items = listOf(
                    DriftItem.SelectionLost(setOf("alt.fliplink.example")),
                ),
            ),
            AppDrift(
                packageName = "dev.miles.fliplink.fixture.stock",
                items = listOf(
                    DriftItem.CompetitorReclaimed(setOf("test.fliplink.example")),
                ),
            ),
        )
        assertEquals("2 domains changed in 2 apps", formatDriftBannerSummary(multi))
    }
}
