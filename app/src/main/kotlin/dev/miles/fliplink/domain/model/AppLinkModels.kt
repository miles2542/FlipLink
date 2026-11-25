package dev.miles.fliplink.domain.model

sealed interface VerificationState {
    val isApprovedOrVerified: Boolean
        get() = when (this) {
            Verified,
            Approved,
            Migrated,
            Restored,
            SystemConfigured,
            PreVerified,
            -> true

            None,
            Denied,
            LegacyFailure,
            is Error,
            -> false
        }

    fun toPmStateCode(): Int = when (this) {
        None, LegacyFailure, is Error -> PM_STATE_NO_RESPONSE
        Verified -> PM_STATE_SUCCESS
        Approved, Migrated, Restored, SystemConfigured, PreVerified -> PM_STATE_APPROVED
        Denied -> PM_STATE_DENIED
    }

    data object None : VerificationState
    data object Verified : VerificationState
    data object Approved : VerificationState
    data object Denied : VerificationState
    data object Migrated : VerificationState
    data object Restored : VerificationState
    data object LegacyFailure : VerificationState
    data object SystemConfigured : VerificationState
    data object PreVerified : VerificationState
    data class Error(val code: Int, val rawLabel: String? = null) : VerificationState

    companion object {
        const val PM_STATE_NO_RESPONSE = 0
        const val PM_STATE_SUCCESS = 1
        const val PM_STATE_APPROVED = 2
        const val PM_STATE_DENIED = 3

        fun parse(raw: String): VerificationState {
            val trimmed = raw.trim()
            return when (trimmed.lowercase()) {
                "none" -> None
                "verified" -> Verified
                "approved" -> Approved
                "denied" -> Denied
                "migrated" -> Migrated
                "restored" -> Restored
                "legacy_failure" -> LegacyFailure
                "system_configured" -> SystemConfigured
                "pre_verified" -> PreVerified
                else -> {
                    val numeric = trimmed.toIntOrNull()
                    if (numeric != null) {
                        Error(code = numeric, rawLabel = trimmed)
                    } else {
                        Error(code = -1, rawLabel = trimmed)
                    }
                }
            }
        }
    }
}

enum class DomainStatus {
    Verified,
    Approved,
    Selected,
    Disabled,
    Denied,
    Declared,
}

enum class ApprovalLevel(val tier: Int) {
    NONE(0),
    SELECTION(3),
    VERIFIED(4),
}

data class DomainEntry(
    val domain: String,
    val isAutoVerify: Boolean,
    val verificationState: VerificationState,
    val userSelected: Boolean,
    val status: DomainStatus,
    val approvalLevel: ApprovalLevel,
) {
    companion object {
        fun computeStatus(
            verificationState: VerificationState,
            userSelected: Boolean,
            isReadOnly: Boolean = false,
        ): DomainStatus {
            if (isReadOnly) return DomainStatus.Declared
            return when {
                verificationState == VerificationState.Denied -> DomainStatus.Denied
                verificationState == VerificationState.Verified -> DomainStatus.Verified
                verificationState.isApprovedOrVerified -> DomainStatus.Approved
                userSelected -> DomainStatus.Selected
                else -> DomainStatus.Disabled
            }
        }

        fun computeApprovalLevel(
            linkHandlingAllowed: Boolean,
            verificationState: VerificationState,
            userSelected: Boolean,
        ): ApprovalLevel {
            if (!linkHandlingAllowed) return ApprovalLevel.NONE
            if (verificationState.isApprovedOrVerified) return ApprovalLevel.VERIFIED
            if (userSelected) return ApprovalLevel.SELECTION
            return ApprovalLevel.NONE
        }
    }
}

data class AppLinkInfo(
    val packageName: String,
    val id: String? = null,
    val signatures: List<String> = emptyList(),
    val linkHandlingAllowed: Boolean = true,
    val domains: List<DomainEntry> = emptyList(),
    val hasAutoVerifyBlock: Boolean = false,
    val isReadOnly: Boolean = false,
)

data class PmCommand(
    val subcommand: String,
    val args: List<String>,
) {
    val pmArgv: List<String>
        get() = listOf("pm", subcommand) + args

    val subcommandArgv: List<String>
        get() = listOf(subcommand) + args
}

data class CompetitorInfo(
    val packageName: String,
    val approvalLevel: ApprovalLevel,
    val isAutoVerify: Boolean,
    val verificationState: VerificationState,
    val userSelected: Boolean,
)

data class ConflictInfo(
    val domain: String,
    val targetPackage: String,
    val targetApprovalLevel: ApprovalLevel,
    val competitors: List<CompetitorInfo>,
) {
    val isOutranked: Boolean
        get() = competitors.any { it.approvalLevel.tier > targetApprovalLevel.tier }

    val isContested: Boolean
        get() = competitors.isNotEmpty()
}

data class DomainPriorState(
    val domain: String,
    val isAutoVerify: Boolean,
    val verificationState: VerificationState,
    val userSelected: Boolean,
)

data class PackagePriorState(
    val packageName: String,
    val linkHandlingAllowed: Boolean,
    val domainStates: Map<String, DomainPriorState>,
)

data class TakeoverUndoSnapshot(
    val targetPackage: String,
    val targetPriorState: PackagePriorState,
    val competitorPriorStates: Map<String, PackagePriorState>,
    val restoreCommands: List<PmCommand>,
)

data class TakeoverPlan(
    val targetPackage: String,
    val domains: List<String>,
    val commands: List<PmCommand>,
    val undoSnapshot: TakeoverUndoSnapshot,
    val modifiedPackages: Set<String>,
)
