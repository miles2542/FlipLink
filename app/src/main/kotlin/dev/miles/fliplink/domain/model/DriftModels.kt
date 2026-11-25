package dev.miles.fliplink.domain.model

sealed interface DriftItem {
    data object LinkHandlingDisabled : DriftItem
    data class SelectionLost(val domains: Set<String>) : DriftItem
    data class VerificationLost(val domains: Set<String>) : DriftItem
    data class CompetitorReclaimed(val domains: Set<String>) : DriftItem
}

data class AppDrift(
    val packageName: String,
    val items: List<DriftItem>,
    val reapplyCommands: List<PmCommand> = emptyList(),
)
