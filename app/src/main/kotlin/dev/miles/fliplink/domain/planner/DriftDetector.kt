package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.AppDrift
import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.DriftItem
import dev.miles.fliplink.domain.model.FlipLinkSnapshot
import dev.miles.fliplink.domain.model.PackageDesiredState
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.model.VerificationState

object DriftDetector {

    fun computeDrift(
        desired: FlipLinkSnapshot,
        live: Map<String, AppLinkInfo>,
    ): List<AppDrift> {
        if (desired.packages.isEmpty() || live.isEmpty()) return emptyList()

        val drifts = mutableListOf<AppDrift>()
        for (packageName in desired.packages.keys.sorted()) {
            val liveApp = live[packageName] ?: continue
            if (!InputValidator.isValidPackageName(packageName)) continue

            val desiredState = desired.packages.getValue(packageName)
            val liveDomains = liveApp.domains.associateBy { it.domain }
            val items = mutableListOf<DriftItem>()

            if (desiredState.linkHandlingAllowed && !liveApp.linkHandlingAllowed) {
                items.add(DriftItem.LinkHandlingDisabled)
            }

            val lostSelections = desiredState.selectedDomains
                .filter { domain ->
                    val entry = liveDomains[domain]
                    entry != null && !entry.userSelected
                }
                .toSortedSet()
            if (lostSelections.isNotEmpty()) {
                items.add(DriftItem.SelectionLost(lostSelections))
            }

            val lostVerifications = desiredState.approvedAutoVerifyDomains
                .filter { domain ->
                    val entry = liveDomains[domain]
                    entry != null && entry.isAutoVerify && !entry.verificationState.isApprovedOrVerified
                }
                .toSortedSet()
            if (lostVerifications.isNotEmpty()) {
                items.add(DriftItem.VerificationLost(lostVerifications))
            }

            val reclaimedDomains = desiredState.deniedDomains
                .filter { domain ->
                    val entry = liveDomains[domain] ?: return@filter false
                    if (entry.isAutoVerify) {
                        entry.verificationState != VerificationState.Denied || entry.userSelected
                    } else {
                        entry.userSelected
                    }
                }
                .toSortedSet()
            if (reclaimedDomains.isNotEmpty()) {
                items.add(DriftItem.CompetitorReclaimed(reclaimedDomains))
            }

            if (items.isNotEmpty()) {
                val reapplyCommands = buildReapplyCommands(
                    packageName = packageName,
                    desiredState = desiredState,
                    liveApp = liveApp,
                    items = items,
                )
                drifts.add(
                    AppDrift(
                        packageName = packageName,
                        items = items,
                        reapplyCommands = reapplyCommands,
                    ),
                )
            }
        }
        return drifts
    }

    fun buildReapplyPlan(drifts: List<AppDrift>): List<PmCommand> {
        return drifts.flatMap { it.reapplyCommands }
    }

    private fun buildReapplyCommands(
        packageName: String,
        desiredState: PackageDesiredState,
        liveApp: AppLinkInfo,
        items: List<DriftItem>,
    ): List<PmCommand> {
        val commands = mutableListOf<PmCommand>()
        val liveDomains = liveApp.domains.associateBy { it.domain }

        for (item in items) {
            when (item) {
                DriftItem.LinkHandlingDisabled -> {
                    commands.add(
                        InputValidator.validatePmCommand(
                            "set-app-links-allowed",
                            listOf("--user", "0", "--package", packageName, desiredState.linkHandlingAllowed.toString()),
                        ),
                    )
                }

                is DriftItem.SelectionLost -> {
                    for (chunk in InputValidator.chunkDomains(item.domains)) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links-user-selection",
                                listOf("--user", "0", "--package", packageName, "true") + chunk,
                            ),
                        )
                    }
                }

                is DriftItem.VerificationLost -> {
                    for (chunk in InputValidator.chunkDomains(item.domains)) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links",
                                listOf("--package", packageName, VerificationState.PM_STATE_APPROVED.toString()) + chunk,
                            ),
                        )
                    }
                }

                is DriftItem.CompetitorReclaimed -> {
                    val autoVerifyReclaimed = item.domains.filter { liveDomains[it]?.isAutoVerify == true }
                    if (autoVerifyReclaimed.isNotEmpty()) {
                        for (chunk in InputValidator.chunkDomains(autoVerifyReclaimed)) {
                            commands.add(
                                InputValidator.validatePmCommand(
                                    "set-app-links",
                                    listOf("--package", packageName, VerificationState.PM_STATE_DENIED.toString()) + chunk,
                                ),
                            )
                        }
                    }
                    for (chunk in InputValidator.chunkDomains(item.domains)) {
                        commands.add(
                            InputValidator.validatePmCommand(
                                "set-app-links-user-selection",
                                listOf("--user", "0", "--package", packageName, "false") + chunk,
                            ),
                        )
                    }
                }
            }
        }
        return commands
    }
}
