package dev.miles.fliplink.data.storage

import dev.miles.fliplink.domain.model.FlipLinkSnapshot
import dev.miles.fliplink.domain.planner.InputValidator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SnapshotStore(
    private val filesDir: File,
    private val json: Json = DEFAULT_JSON,
) {
    private val snapshotFile: File
        get() = File(filesDir, SNAPSHOT_FILE_NAME)

    private val tempFile: File
        get() = File(filesDir, "$SNAPSHOT_FILE_NAME.tmp")

    fun load(): FlipLinkSnapshot {
        val file = snapshotFile
        if (!file.exists()) return FlipLinkSnapshot()
        return try {
            val content = file.readText(Charsets.UTF_8)
            if (content.isBlank()) FlipLinkSnapshot() else json.decodeFromString<FlipLinkSnapshot>(content)
        } catch (_: Throwable) {
            FlipLinkSnapshot()
        }
    }

    fun save(snapshot: FlipLinkSnapshot) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val encoded = json.encodeToString(snapshot)
        val tmp = tempFile
        tmp.writeText(encoded, Charsets.UTF_8)
        try {
            Files.move(
                tmp.toPath(),
                snapshotFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                tmp.toPath(),
                snapshotFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    fun clear() {
        val file = snapshotFile
        if (file.exists()) {
            file.delete()
        }
        val tmp = tempFile
        if (tmp.exists()) {
            tmp.delete()
        }
    }

    fun exportJson(snapshot: FlipLinkSnapshot): String = json.encodeToString(snapshot)

    fun importJson(rawJson: String): Result<FlipLinkSnapshot> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Config file is empty"))
        }
        val decoded = try {
            json.decodeFromString<FlipLinkSnapshot>(rawJson)
        } catch (_: Throwable) {
            return Result.failure(IllegalArgumentException("Invalid FlipLink config JSON"))
        }
        if (decoded.schemaVersion != FlipLinkSnapshot.CURRENT_SCHEMA_VERSION) {
            return Result.failure(
                IllegalArgumentException("Unsupported config schema version: ${decoded.schemaVersion}"),
            )
        }
        for ((packageName, desired) in decoded.packages) {
            if (!InputValidator.isValidPackageName(packageName)) {
                return Result.failure(
                    IllegalArgumentException("Invalid package name in config: $packageName"),
                )
            }
            val allDomains = desired.selectedDomains + desired.deniedDomains + desired.approvedAutoVerifyDomains
            for (domain in allDomains) {
                if (!InputValidator.isValidDomain(domain, allowAllLiteral = false)) {
                    return Result.failure(
                        IllegalArgumentException("Invalid domain in config: $domain"),
                    )
                }
            }
        }
        return Result.success(decoded)
    }

    companion object {
        const val SNAPSHOT_FILE_NAME = "fliplink_snapshot_v1.json"

        val DEFAULT_JSON: Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
