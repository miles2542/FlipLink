package dev.miles.fliplink.data.storage

import dev.miles.fliplink.domain.model.FlipLinkSnapshot
import dev.miles.fliplink.domain.model.PackageDesiredState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SnapshotStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun loadReturnsEmptySnapshotWhenFileMissing() {
        val store = SnapshotStore(tempFolder.root)
        assertEquals(FlipLinkSnapshot(), store.load())
    }

    @Test
    fun saveAndLoadRoundTripsAtomically() {
        val dir = File(tempFolder.root, "nested/files")
        val store = SnapshotStore(dir)
        val snapshot = FlipLinkSnapshot(
            schemaVersion = 1,
            packages = mapOf(
                "com.example.app" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("example.com", "alt.example.com"),
                    deniedDomains = setOf("denied.example.com"),
                    approvedAutoVerifyDomains = setOf("example.com"),
                    updatedAtEpochMs = 1_700_000_000_000L,
                ),
            ),
        )

        store.save(snapshot)

        assertTrue(File(dir, SnapshotStore.SNAPSHOT_FILE_NAME).exists())
        assertFalse(File(dir, "${SnapshotStore.SNAPSHOT_FILE_NAME}.tmp").exists())
        assertEquals(snapshot, store.load())
    }

    @Test
    fun loadReturnsEmptySnapshotOnCorruptJson() {
        val store = SnapshotStore(tempFolder.root)
        File(tempFolder.root, SnapshotStore.SNAPSHOT_FILE_NAME).writeText("{not valid json")

        assertEquals(FlipLinkSnapshot(), store.load())
    }

    @Test
    fun exportAndImportJsonRoundTripsValidSnapshot() {
        val store = SnapshotStore(tempFolder.root)
        val snapshot = FlipLinkSnapshot(
            schemaVersion = FlipLinkSnapshot.CURRENT_SCHEMA_VERSION,
            packages = mapOf(
                "dev.miles.fliplink.fixture.patched" to PackageDesiredState(
                    linkHandlingAllowed = true,
                    selectedDomains = setOf("test.fliplink.example", "alt.fliplink.example"),
                    deniedDomains = emptySet(),
                    approvedAutoVerifyDomains = emptySet(),
                    updatedAtEpochMs = 1_700_000_100_000L,
                ),
            ),
        )

        val exported = store.exportJson(snapshot)
        val imported = store.importJson(exported)

        assertTrue(imported.isSuccess)
        assertEquals(snapshot, imported.getOrThrow())
    }

    @Test
    fun importJsonRejectsBlankAndMalformedJson() {
        val store = SnapshotStore(tempFolder.root)

        assertTrue(store.importJson("   ").isFailure)
        assertTrue(store.importJson("{broken").isFailure)
    }

    @Test
    fun importJsonRejectsUnsupportedSchemaVersionAndInvalidTokens() {
        val store = SnapshotStore(tempFolder.root)

        val wrongVersion = """{"schemaVersion": 2, "packages": {}}"""
        val wrongVersionResult = store.importJson(wrongVersion)
        assertTrue(wrongVersionResult.isFailure)
        assertTrue(wrongVersionResult.exceptionOrNull()?.message?.contains("schema version") == true)

        val badPackage = """
            {
              "schemaVersion": 1,
              "packages": {
                "bad;pkg": {
                  "linkHandlingAllowed": true,
                  "selectedDomains": ["example.com"],
                  "updatedAtEpochMs": 100
                }
              }
            }
        """.trimIndent()
        assertTrue(store.importJson(badPackage).isFailure)

        val badDomain = """
            {
              "schemaVersion": 1,
              "packages": {
                "com.example.app": {
                  "linkHandlingAllowed": true,
                  "selectedDomains": ["--bad"],
                  "updatedAtEpochMs": 100
                }
              }
            }
        """.trimIndent()
        assertTrue(store.importJson(badDomain).isFailure)
    }
}
