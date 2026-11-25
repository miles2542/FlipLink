package dev.miles.fliplink.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FlipLinkSnapshot(
    val schemaVersion: Int = 1,
    val packages: Map<String, PackageDesiredState> = emptyMap(),
)

@Serializable
data class PackageDesiredState(
    val linkHandlingAllowed: Boolean,
    val selectedDomains: Set<String> = emptySet(),
    val deniedDomains: Set<String> = emptySet(),
    val approvedAutoVerifyDomains: Set<String> = emptySet(),
    val updatedAtEpochMs: Long,
)
