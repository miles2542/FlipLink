package dev.miles.fliplink.ui.appdetail

import dev.miles.fliplink.domain.model.DomainStatus
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.model.TakeoverUndoSnapshot

enum class DomainSectionGroup(val title: String) {
    CONFLICTS("Conflicts"),
    INACTIVE("Inactive / Denied"),
    ACTIVE("Active"),
}

data class DomainConflictUiModel(
    val primaryOwnerPackage: String,
    val primaryOwnerLabel: String,
    val primaryOwnerTierLabel: String,
    val additionalCompetitorCount: Int,
    val summaryText: String,
)

data class DomainRowUiModel(
    val domain: String,
    val status: DomainStatus,
    val isAutoVerify: Boolean,
    val userSelected: Boolean,
    val isEnabledForSwitch: Boolean,
    val conflict: DomainConflictUiModel?,
    val group: DomainSectionGroup,
    val isInFlight: Boolean,
)

data class DomainSectionUiModel(
    val group: DomainSectionGroup?,
    val rows: List<DomainRowUiModel>,
)

data class AdbSheetRequest(
    val title: String,
    val subtitle: String?,
    val commands: List<PmCommand>,
)

data class AppDetailUiState(
    val packageName: String,
    val displayLabel: String = packageName,
    val isFound: Boolean = false,
    val isReadOnly: Boolean = true,
    val linkHandlingAllowed: Boolean = true,
    val isMasterInFlight: Boolean = false,
    val totalDomains: Int = 0,
    val activeDomains: Int = 0,
    val conflictCount: Int = 0,
    val hasAnyDisabledOrDenied: Boolean = false,
    val hasAnyActive: Boolean = false,
    val sections: List<DomainSectionUiModel> = emptyList(),
    val adbSheetRequest: AdbSheetRequest? = null,
) {
    val summaryStatsText: String
        get() {
            val domainNoun = if (totalDomains == 1) "domain" else "domains"
            if (isReadOnly) {
                return "$totalDomains declared $domainNoun"
            }
            val base = "$totalDomains $domainNoun · $activeDomains active"
            return if (conflictCount > 0) {
                val conflictNoun = if (conflictCount == 1) "conflict" else "conflicts"
                "$base · $conflictCount $conflictNoun"
            } else {
                base
            }
        }
}

sealed interface AppDetailEvent {
    data class ShowUndoTakeover(
        val message: String,
        val snapshot: TakeoverUndoSnapshot,
    ) : AppDetailEvent

    data class ShowError(
        val message: String,
        val fallbackCommands: List<PmCommand>,
    ) : AppDetailEvent

    data class ShowInfo(
        val message: String,
    ) : AppDetailEvent
}
