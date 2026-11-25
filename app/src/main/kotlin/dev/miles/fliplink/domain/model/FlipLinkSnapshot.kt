package dev.miles.fliplink.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FlipLinkSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val packages: Map<String, PackageDesiredState> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class PackageDesiredState(
    val linkHandlingAllowed: Boolean,
    val selectedDomains: Set<String> = emptySet(),
    val deniedDomains: Set<String> = emptySet(),
    val approvedAutoVerifyDomains: Set<String> = emptySet(),
    val updatedAtEpochMs: Long,
)
