package dev.miles.fliplink.domain.planner

import dev.miles.fliplink.domain.model.PmCommand

object InputValidator {
    const val MAX_DOMAINS_PER_COMMAND = 40

    val ALLOWED_SUBCOMMANDS: Set<String> = setOf(
        "get-app-links",
        "get-app-link-owners",
        "set-app-links-allowed",
        "set-app-links-user-selection",
        "set-app-links",
    )

    private val PACKAGE_NAME_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val DOMAIN_TOKEN_REGEX = Regex("^(\\*\\.)?([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9-]+$")
    private val VALID_PM_STATE_CODES = setOf("0", "1", "2", "3")

    fun isValidPackageName(packageName: String): Boolean {
        if (packageName.isEmpty() || hasUnsafeChars(packageName)) return false
        return PACKAGE_NAME_REGEX.matches(packageName)
    }

    fun requireValidPackageName(packageName: String): String {
        require(isValidPackageName(packageName)) {
            "Invalid package name: '$packageName'"
        }
        return packageName
    }

    fun isValidDomain(domain: String, allowAllLiteral: Boolean = false): Boolean {
        if (domain.isEmpty() || domain.startsWith("-") || hasUnsafeChars(domain)) return false
        if (allowAllLiteral && domain == "all") return true
        return DOMAIN_TOKEN_REGEX.matches(domain)
    }

    fun requireValidDomain(domain: String, allowAllLiteral: Boolean = false): String {
        require(isValidDomain(domain, allowAllLiteral)) {
            "Invalid domain token: '$domain'"
        }
        return domain
    }

    fun chunkDomains(
        domains: Collection<String>,
        chunkSize: Int = MAX_DOMAINS_PER_COMMAND,
    ): List<List<String>> {
        require(chunkSize in 1..MAX_DOMAINS_PER_COMMAND) {
            "chunkSize must be in 1..$MAX_DOMAINS_PER_COMMAND, got $chunkSize"
        }
        if (domains.isEmpty()) return emptyList()
        val uniqueValidated = LinkedHashSet<String>(domains.size)
        for (domain in domains) {
            uniqueValidated.add(requireValidDomain(domain, allowAllLiteral = false))
        }
        return uniqueValidated.toList().chunked(chunkSize)
    }

    fun validatePmCommand(subcommand: String, args: List<String>): PmCommand {
        require(subcommand in ALLOWED_SUBCOMMANDS) {
            "Disallowed pm subcommand: '$subcommand'"
        }
        for (arg in args) {
            require(arg.isNotEmpty() && !hasControlOrWhitespace(arg)) {
                "Invalid pm argument: '$arg'"
            }
        }
        when (subcommand) {
            "get-app-links" -> {
                // Allowed: [--user, 0] or [--user, 0, <pkg>]
                require(args.size == 2 || args.size == 3) {
                    "Unexpected args for get-app-links: $args"
                }
                require(args[0] == "--user" && args[1] == "0") {
                    "Only --user 0 is permitted for get-app-links"
                }
                if (args.size == 3) {
                    requireValidPackageName(args[2])
                }
            }

            "get-app-link-owners" -> {
                // Allowed: [--user, 0, <domain1>, ...]
                require(args.size >= 3 && args.size <= 2 + MAX_DOMAINS_PER_COMMAND) {
                    "Unexpected domain count for get-app-link-owners"
                }
                require(args[0] == "--user" && args[1] == "0") {
                    "Only --user 0 is permitted for get-app-link-owners"
                }
                for (i in 2 until args.size) {
                    requireValidDomain(args[i], allowAllLiteral = false)
                }
            }

            "set-app-links-allowed" -> {
                // Allowed: [--user, 0, --package, <pkg>, <true|false>]
                require(args.size == 5) {
                    "Unexpected args for set-app-links-allowed: $args"
                }
                require(args[0] == "--user" && args[1] == "0" && args[2] == "--package") {
                    "Invalid flags for set-app-links-allowed"
                }
                requireValidPackageName(args[3])
                require(args[4] == "true" || args[4] == "false") {
                    "Expected boolean for set-app-links-allowed, got '${args[4]}'"
                }
            }

            "set-app-links-user-selection" -> {
                // Allowed: [--user, 0, --package, <pkg>, <true|false>, <domains...|all>]
                require(args.size >= 6 && args.size <= 5 + MAX_DOMAINS_PER_COMMAND) {
                    "Unexpected arg count for set-app-links-user-selection"
                }
                require(args[0] == "--user" && args[1] == "0" && args[2] == "--package") {
                    "Invalid flags for set-app-links-user-selection"
                }
                requireValidPackageName(args[3])
                require(args[4] == "true" || args[4] == "false") {
                    "Expected boolean for set-app-links-user-selection, got '${args[4]}'"
                }
                validateDomainTail(args.subList(5, args.size))
            }

            "set-app-links" -> {
                // Allowed: [--package, <pkg>, <0|1|2|3>, <domains...|all>]
                require(args.size >= 4 && args.size <= 3 + MAX_DOMAINS_PER_COMMAND) {
                    "Unexpected arg count for set-app-links"
                }
                require(args[0] == "--package") {
                    "Invalid flags for set-app-links"
                }
                requireValidPackageName(args[1])
                require(args[2] in VALID_PM_STATE_CODES) {
                    "Invalid state code for set-app-links: '${args[2]}'"
                }
                validateDomainTail(args.subList(3, args.size))
            }
        }
        return PmCommand(subcommand = subcommand, args = args)
    }

    private fun validateDomainTail(tokens: List<String>) {
        if (tokens.size == 1 && tokens[0] == "all") {
            return
        }
        for (token in tokens) {
            requireValidDomain(token, allowAllLiteral = false)
        }
    }

    private fun hasUnsafeChars(value: String): Boolean {
        return value.any { ch -> ch.isWhitespace() || ch.code < 0x20 || ch.code == 0x7F }
    }

    private fun hasControlOrWhitespace(value: String): Boolean = hasUnsafeChars(value)
}
