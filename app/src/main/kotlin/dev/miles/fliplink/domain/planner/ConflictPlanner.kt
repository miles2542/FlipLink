package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.CompetitorInfo
import dev.miles.fliplink.domain.model.ConflictInfo
import dev.miles.fliplink.domain.model.DomainPriorState
import dev.miles.fliplink.domain.model.PackagePriorState
import dev.miles.fliplink.domain.model.PmCommand
import dev.miles.fliplink.domain.model.TakeoverPlan
import dev.miles.fliplink.domain.model.TakeoverUndoSnapshot
import dev.miles.fliplink.domain.model.VerificationState

object ConflictPlanner {

    fun computeConflicts(
        targetPackage: String,
        allPackages: List<AppLinkInfo>,
    ): Map<String, ConflictInfo> {
        InputValidator.requireValidPackageName(targetPackage)
        val target = allPackages.firstOrNull { it.packageName == targetPackage } ?: return emptyMap()
        if (target.domains.isEmpty()) return emptyMap()

        val targetDomains = target.domains.associateBy { it.domain }
        val competitorsByDomain = LinkedHashMap<String, MutableList<CompetitorInfo>>()

        for (other in allPackages) {
            if (other.packageName == targetPackage) continue
            for (entry in other.domains) {
                if (entry.domain !in targetDomains) continue
                if (entry.approvalLevel.tier > ApprovalLevel.NONE.tier) {
                    competitorsByDomain.getOrPut(entry.domain) { mutableListOf() }.add(
                        CompetitorInfo(
                            packageName = other.packageName,
                            approvalLevel = entry.approvalLevel,
                            isAutoVerify = entry.isAutoVerify,
                            verificationState = entry.verificationState,
                            userSelected = entry.userSelected,
                        ),
                    )
                }
            }
        }

        val result = LinkedHashMap<String, ConflictInfo>()
        for ((domain, competitors) in competitorsByDomain) {
            val targetEntry = targetDomains.getValue(domain)
            val sortedCompetitors = competitors.sortedWith(
                compareByDescending<CompetitorInfo> { it.approvalLevel.tier }
                    .thenBy { it.packageName },
            )
            result[domain] = ConflictInfo(
                domain = domain,
                targetPackage = targetPackage,
                targetApprovalLevel = targetEntry.approvalLevel,
                competitors = sortedCompetitors,
            )
        }
        return result
    }

    fun planTakeover(
        targetPackage: String,
        domains: Collection<String>,
        allPackages: List<AppLinkInfo>,
    ): TakeoverPlan {
        InputValidator.requireValidPackageName(targetPackage)
        val requestedDomains = InputValidator.chunkDomains(domains)
            .flatten()
        require(requestedDomains.isNotEmpty()) { "Takeover requires at least one domain" }

        val packagesByName = allPackages.associateBy { it.packageName }
        val targetInfo = requireNotNull(packagesByName[targetPackage]) {
            "Target package '$targetPackage' not found in live state"
        }
        val targetDomainMap = targetInfo.domains.associateBy { it.domain }
        val conflicts = computeConflicts(targetPackage, allPackages)

        val commands = mutableListOf<PmCommand>()
        val modifiedPackages = linkedSetOf<String>()
        val competitorPriorStates = LinkedHashMap<String, PackagePriorState>()
        val competitorDomainsToDemote = LinkedHashMap<String, MutableList<DomainPriorState>>()

        // Group competitor demotions by competitor package for deterministic batched commands
        for (domain in requestedDomains) {
            val conflict = conflicts[domain] ?: continue
            for (competitor in conflict.competitors) {
                val prior = DomainPriorState(
                    domain = domain,
                    isAutoVerify = competitor.isAutoVerify,
                    verificationState = competitor.verificationState,
                    userSelected = competitor.userSelected,
                )
                competitorDomainsToDemote.getOrPut(competitor.packageName) { mutableListOf() }.add(prior)
            }
        }

        // 1. Demote each competitor without touching its master switch or unrelated domains
        for (competitorPkg in competitorDomainsToDemote.keys.sorted()) {
            val competitorInfo = packagesByName.getValue(competitorPkg)
            val priorList = competitorDomainsToDemote.getValue(competitorPkg)
            modifiedPackages.add(competitorPkg)
            competitorPriorStates[competitorPkg] = PackagePriorState(
                packageName = competitorPkg,
                linkHandlingAllowed = competitorInfo.linkHandlingAllowed,
                domainStates = priorList.associateBy { it.domain },
            )

            val autoVerifyDomains = priorList.filter { it.isAutoVerify }.map { it.domain }
            if (autoVerifyDomains.isNotEmpty()) {
                for (chunk in InputValidator.chunkDomains(autoVerifyDomains)) {
                    commands.add(
                        InputValidator.validatePmCommand(
                            "set-app-links",
                            listOf("--package", competitorPkg, VerificationState.PM_STATE_DENIED.toString()) + chunk,
                        ),
                    )
                }
            }

            val allCompDomains = priorList.map { it.domain }
            for (chunk in InputValidator.chunkDomains(allCompDomains)) {
                commands.add(
                    InputValidator.validatePmCommand(
                        "set-app-links-user-selection",
                        listOf("--user", "0", "--package", competitorPkg, "false") + chunk,
                    ),
                )
            }
        }

        // 2. Promote target package
        modifiedPackages.add(targetPackage)
        if (!targetInfo.linkHandlingAllowed) {
            commands.add(
                InputValidator.validatePmCommand(
                    "set-app-links-allowed",
                    listOf("--user", "0", "--package", targetPackage, "true"),
                ),
            )
        }

        for (chunk in InputValidator.chunkDomains(requestedDomains)) {
            commands.add(
                InputValidator.validatePmCommand(
                    "set-app-links-user-selection",
                    listOf("--user", "0", "--package", targetPackage, "true") + chunk,
                ),
            )
        }

        val targetAutoVerifyDomains = requestedDomains.filter { domain ->
            targetDomainMap[domain]?.isAutoVerify == true
        }
        if (targetAutoVerifyDomains.isNotEmpty()) {
            for (chunk in InputValidator.chunkDomains(targetAutoVerifyDomains)) {
                commands.add(
                    InputValidator.validatePmCommand(
                        "set-app-links",
                        listOf("--package", targetPackage, VerificationState.PM_STATE_APPROVED.toString()) + chunk,
                    ),
                )
            }
        }

        // 3. Build exact Undo snapshot and restore commands
        val targetPriorDomains = requestedDomains.associateWith { domain ->
            val existing = targetDomainMap[domain]
            DomainPriorState(
                domain = domain,
                isAutoVerify = existing?.isAutoVerify == true,
                verificationState = existing?.verificationState ?: VerificationState.None,
                userSelected = existing?.userSelected == true,
            )
        }
        val targetPriorState = PackagePriorState(
            packageName = targetPackage,
            linkHandlingAllowed = targetInfo.linkHandlingAllowed,
            domainStates = targetPriorDomains,
        )

        val restoreCommands = buildUndoCommands(
            targetPriorState = targetPriorState,
            competitorPriorStates = competitorPriorStates,
        )

        val undoSnapshot = TakeoverUndoSnapshot(
            targetPackage = targetPackage,
            targetPriorState = targetPriorState,
            competitorPriorStates = competitorPriorStates,
            restoreCommands = restoreCommands,
        )

        return TakeoverPlan(
            targetPackage = targetPackage,
            domains = requestedDomains,
            commands = commands,
            undoSnapshot = undoSnapshot,
            modifiedPackages = modifiedPackages,
        )
    }

    fun buildUndoCommands(
        targetPriorState: PackagePriorState,
        competitorPriorStates: Map<String, PackagePriorState>,
    ): List<PmCommand> {
        val commands = mutableListOf<PmCommand>()

        // Restore target's prior state first
        val targetPkg = targetPriorState.packageName
        if (!targetPriorState.linkHandlingAllowed) {
            commands.add(
                InputValidator.validatePmCommand(
                    "set-app-links-allowed",
                    listOf("--user", "0", "--package", targetPkg, "false"),
                ),
            )
        }

        appendDomainRestoreCommands(
            packageName = targetPkg,
            priorDomains = targetPriorState.domainStates.values,
            commands = commands,
        )

        // Restore each competitor's prior state
        for (competitorPkg in competitorPriorStates.keys.sorted()) {
            val compPrior = competitorPriorStates.getValue(competitorPkg)
            appendDomainRestoreCommands(
                packageName = competitorPkg,
                priorDomains = compPrior.domainStates.values,
                commands = commands,
            )
        }

        return commands
    }

    private fun appendDomainRestoreCommands(
        packageName: String,
        priorDomains: Collection<DomainPriorState>,
        commands: MutableList<PmCommand>,
    ) {
        // Restore autoVerify verification states grouped by target PM state code
        val autoVerifyByCode = linkedMapOf<Int, MutableList<String>>()
        val selectedTrue = mutableListOf<String>()
        val selectedFalse = mutableListOf<String>()

        for (prior in priorDomains) {
            if (prior.isAutoVerify) {
                val code = prior.verificationState.toPmStateCode()
                autoVerifyByCode.getOrPut(code) { mutableListOf() }.add(prior.domain)
            }
            if (prior.userSelected) {
                selectedTrue.add(prior.domain)
            } else {
                selectedFalse.add(prior.domain)
            }
        }

        for ((code, domainList) in autoVerifyByCode) {
            for (chunk in InputValidator.chunkDomains(domainList)) {
                commands.add(
                    InputValidator.validatePmCommand(
                        "set-app-links",
                        listOf("--package", packageName, code.toString()) + chunk,
                    ),
                )
            }
        }

        if (selectedTrue.isNotEmpty()) {
            for (chunk in InputValidator.chunkDomains(selectedTrue)) {
                commands.add(
                    InputValidator.validatePmCommand(
                        "set-app-links-user-selection",
                        listOf("--user", "0", "--package", packageName, "true") + chunk,
                    ),
                )
            }
        }

        if (selectedFalse.isNotEmpty()) {
            for (chunk in InputValidator.chunkDomains(selectedFalse)) {
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
