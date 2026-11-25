package dev.miles.fliplink.domain.parser

import dev.miles.fliplink.domain.model.AppLinkInfo
import dev.miles.fliplink.domain.model.ApprovalLevel
import dev.miles.fliplink.domain.model.DomainEntry
import dev.miles.fliplink.domain.model.VerificationState

data class DomainOwner(
    val packageName: String,
    val approvalLevel: ApprovalLevel,
)

object PmAppLinksParser {
    private val PACKAGE_HEADER_WITH_UUID_REGEX = Regex("""^([a-zA-Z0-9_.]+)\s+([0-9a-fA-F-]+):$""")
    private val PACKAGE_HEADER_PLAIN_REGEX = Regex("""^([a-zA-Z0-9_.]+):$""")
    private val USER_HEADER_REGEX = Regex("""^User\s+(\d+):$""")
    private val OWNER_BRACKET_REGEX = Regex("""^\[\d+\]\s+([a-zA-Z0-9_.]+)\s+\(([A-Z]+)\)$""")
    private val OWNER_COLON_REGEX = Regex("""^([a-zA-Z0-9_.]+):\s+([A-Z0-9_]+)$""")
    private val OWNER_PACKAGE_REGEX = Regex("""^Package:\s*([a-zA-Z0-9_.]+),\s*approval:\s*([A-Z0-9_]+)$""")

    private enum class Section {
        NONE,
        SIGNATURES,
        DOMAIN_VERIFICATION_STATE,
        USER,
        SELECTION_ENABLED,
        SELECTION_DISABLED,
    }

    private class MutablePackageRecord(val packageName: String) {
        var id: String? = null
        val signatures = mutableListOf<String>()
        val verificationStates = LinkedHashMap<String, VerificationState>()
        var hasAutoVerifyBlock = false
        var linkHandlingAllowed = true
        val enabledDomains = LinkedHashSet<String>()
        val disabledDomains = LinkedHashSet<String>()

        fun toAppLinkInfo(): AppLinkInfo {
            val allDomains = LinkedHashSet<String>()
            allDomains.addAll(verificationStates.keys)
            allDomains.addAll(enabledDomains)
            allDomains.addAll(disabledDomains)

            val entries = allDomains.map { domain ->
                val isAutoVerify = domain in verificationStates
                val state = verificationStates[domain] ?: VerificationState.None
                val userSelected = domain in enabledDomains
                DomainEntry(
                    domain = domain,
                    isAutoVerify = isAutoVerify,
                    verificationState = state,
                    userSelected = userSelected,
                    status = DomainEntry.computeStatus(
                        verificationState = state,
                        userSelected = userSelected,
                        isReadOnly = false,
                    ),
                    approvalLevel = DomainEntry.computeApprovalLevel(
                        linkHandlingAllowed = linkHandlingAllowed,
                        verificationState = state,
                        userSelected = userSelected,
                    ),
                )
            }

            return AppLinkInfo(
                packageName = packageName,
                id = id,
                signatures = signatures.toList(),
                linkHandlingAllowed = linkHandlingAllowed,
                domains = entries,
                hasAutoVerifyBlock = hasAutoVerifyBlock,
                isReadOnly = false,
            )
        }
    }

    fun parseAll(output: String): List<AppLinkInfo> {
        return parseInternal(output, expectedPackageName = null)
    }

    fun parseSingle(output: String, expectedPackageName: String? = null): AppLinkInfo? {
        return parseInternal(output, expectedPackageName = expectedPackageName).firstOrNull()
    }

    private fun parseInternal(output: String, expectedPackageName: String?): List<AppLinkInfo> {
        if (output.isBlank()) return emptyList()

        val records = mutableListOf<MutablePackageRecord>()
        var current: MutablePackageRecord? = null
        var section = Section.NONE
        var basePackageIndent: Int? = null
        var selectionSubIndent: Int? = null

        for (rawLine in output.lineSequence()) {
            if (rawLine.isBlank()) continue
            val indent = rawLine.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) rawLine.length else it }
            val trimmed = rawLine.trim()

            // Form C: headerless single-package query starting directly at "User 0:"
            if (current == null && expectedPackageName != null && USER_HEADER_REGEX.matches(trimmed)) {
                val rec = MutablePackageRecord(expectedPackageName)
                records.add(rec)
                current = rec
                basePackageIndent = (indent - 2).coerceAtLeast(0)
                section = Section.USER
                continue
            }

            // Detect new package header at top/package indent level
            val isAtPackageIndent = basePackageIndent == null || indent <= basePackageIndent
            if (isAtPackageIndent && !USER_HEADER_REGEX.matches(trimmed)) {
                val uuidMatch = PACKAGE_HEADER_WITH_UUID_REGEX.matchEntire(trimmed)
                if (uuidMatch != null) {
                    val rec = MutablePackageRecord(uuidMatch.groupValues[1]).apply {
                        id = uuidMatch.groupValues[2]
                    }
                    records.add(rec)
                    current = rec
                    basePackageIndent = indent
                    section = Section.NONE
                    selectionSubIndent = null
                    continue
                }
                val plainMatch = PACKAGE_HEADER_PLAIN_REGEX.matchEntire(trimmed)
                if (plainMatch != null && !isKnownSectionHeader(trimmed)) {
                    val rec = MutablePackageRecord(plainMatch.groupValues[1])
                    records.add(rec)
                    current = rec
                    basePackageIndent = indent
                    section = Section.NONE
                    selectionSubIndent = null
                    continue
                }
            }

            val rec = current ?: continue

            if (USER_HEADER_REGEX.matches(trimmed)) {
                section = Section.USER
                selectionSubIndent = null
                continue
            }

            if (trimmed.startsWith("ID:")) {
                rec.id = trimmed.removePrefix("ID:").trim()
                section = Section.NONE
                continue
            }

            if (trimmed.startsWith("Signatures:")) {
                val inline = trimmed.removePrefix("Signatures:").trim()
                if (inline.isNotEmpty() && inline != "[]") {
                    parseSignatureTokens(inline, rec.signatures)
                }
                section = Section.SIGNATURES
                continue
            }

            if (trimmed == "Domain verification state:") {
                rec.hasAutoVerifyBlock = true
                section = Section.DOMAIN_VERIFICATION_STATE
                continue
            }

            if (trimmed.startsWith("Verification link handling allowed:")) {
                val value = trimmed.removePrefix("Verification link handling allowed:").trim()
                rec.linkHandlingAllowed = value.equals("true", ignoreCase = true)
                section = Section.USER
                continue
            }

            if (trimmed.startsWith("Link handling allowed:")) {
                val value = trimmed.removePrefix("Link handling allowed:").trim()
                rec.linkHandlingAllowed = value.equals("true", ignoreCase = true)
                section = Section.USER
                continue
            }

            if (trimmed == "Selection state:") {
                section = Section.USER
                selectionSubIndent = indent
                continue
            }

            if (trimmed == "Enabled:") {
                section = Section.SELECTION_ENABLED
                selectionSubIndent = indent
                continue
            }

            if (trimmed == "Disabled:") {
                section = Section.SELECTION_DISABLED
                selectionSubIndent = indent
                continue
            }

            when (section) {
                Section.SIGNATURES -> {
                    if (trimmed.startsWith("[") || trimmed.contains(":")) {
                        parseSignatureTokens(trimmed, rec.signatures)
                    }
                }

                Section.DOMAIN_VERIFICATION_STATE -> {
                    val colonIdx = trimmed.indexOf(':')
                    if (colonIdx > 0) {
                        val domain = trimmed.substring(0, colonIdx).trim()
                        val rawState = trimmed.substring(colonIdx + 1).trim()
                        if (domain.isNotEmpty() && rawState.isNotEmpty()) {
                            rec.verificationStates[domain] = VerificationState.parse(rawState)
                        }
                    }
                }

                Section.SELECTION_ENABLED -> {
                    val minIndent = selectionSubIndent ?: 0
                    if (indent > minIndent) {
                        rec.enabledDomains.add(trimmed)
                    }
                }

                Section.SELECTION_DISABLED -> {
                    val minIndent = selectionSubIndent ?: 0
                    if (indent > minIndent) {
                        rec.disabledDomains.add(trimmed)
                    }
                }

                Section.NONE, Section.USER -> Unit
            }
        }

        return records.map { it.toAppLinkInfo() }
    }

    fun parseAppLinkOwners(output: String): Map<String, List<DomainOwner>> {
        if (output.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, MutableList<DomainOwner>>()
        var currentDomain: String? = null
        var currentTier: ApprovalLevel? = null

        for (rawLine in output.lineSequence()) {
            if (rawLine.isBlank()) continue
            val trimmed = rawLine.trim()

            if (trimmed.startsWith("Domain:")) {
                currentDomain = trimmed.removePrefix("Domain:").trim().trimEnd(':')
                currentTier = null
                result.getOrPut(currentDomain) { mutableListOf() }
                continue
            }

            if (trimmed.startsWith("ID:")) continue

            val tierHeader = parseApprovalLevel(trimmed.trimEnd(':'))
            if (trimmed.endsWith(":") && tierHeader != null && currentDomain != null) {
                currentTier = tierHeader
                continue
            }

            if (trimmed.endsWith(":") && !trimmed.contains(" ")) {
                val candidate = trimmed.removeSuffix(":")
                if (candidate.contains(".") || candidate.startsWith("*.")) {
                    currentDomain = candidate
                    currentTier = null
                    result.getOrPut(currentDomain) { mutableListOf() }
                    continue
                }
            }

            val domain = currentDomain ?: continue
            val owners = result.getOrPut(domain) { mutableListOf() }

            val bracketMatch = OWNER_BRACKET_REGEX.matchEntire(trimmed)
            if (bracketMatch != null) {
                val pkg = bracketMatch.groupValues[1]
                val level = parseApprovalLevel(bracketMatch.groupValues[2]) ?: ApprovalLevel.NONE
                owners.add(DomainOwner(pkg, level))
                continue
            }

            val packageMatch = OWNER_PACKAGE_REGEX.matchEntire(trimmed)
            if (packageMatch != null) {
                val pkg = packageMatch.groupValues[1]
                val level = parseApprovalLevel(packageMatch.groupValues[2]) ?: ApprovalLevel.NONE
                owners.add(DomainOwner(pkg, level))
                continue
            }

            val colonMatch = OWNER_COLON_REGEX.matchEntire(trimmed)
            if (colonMatch != null) {
                val pkg = colonMatch.groupValues[1]
                val level = parseApprovalLevel(colonMatch.groupValues[2]) ?: ApprovalLevel.NONE
                owners.add(DomainOwner(pkg, level))
                continue
            }

            if (currentTier != null && !trimmed.contains(" ")) {
                owners.add(DomainOwner(trimmed, currentTier))
            }
        }

        return result
    }

    private fun parseApprovalLevel(token: String): ApprovalLevel? {
        return when (token.trim().uppercase()) {
            "VERIFIED", "4" -> ApprovalLevel.VERIFIED
            "SELECTION", "3" -> ApprovalLevel.SELECTION
            "NONE", "0" -> ApprovalLevel.NONE
            else -> null
        }
    }

    private fun isKnownSectionHeader(trimmed: String): Boolean {
        return trimmed == "Domain verification state:" ||
            trimmed == "Selection state:" ||
            trimmed == "Enabled:" ||
            trimmed == "Disabled:" ||
            trimmed == "Signatures:"
    }

    private fun parseSignatureTokens(raw: String, out: MutableList<String>) {
        val cleaned = raw.removePrefix("[").removeSuffix("]").trim()
        if (cleaned.isEmpty()) return
        for (part in cleaned.split(',')) {
            val token = part.trim()
            if (token.isNotEmpty()) {
                out.add(token)
            }
        }
    }
}
