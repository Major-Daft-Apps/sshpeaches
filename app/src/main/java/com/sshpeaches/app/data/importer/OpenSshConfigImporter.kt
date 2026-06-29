package com.majordaftapps.sshpeaches.app.data.importer

import com.majordaftapps.sshpeaches.app.data.model.AuthMethod
import com.majordaftapps.sshpeaches.app.data.model.ConnectionMode
import com.majordaftapps.sshpeaches.app.data.model.HostConnection
import com.majordaftapps.sshpeaches.app.data.model.PortForward
import com.majordaftapps.sshpeaches.app.data.model.PortForwardType
import java.util.UUID

data class OpenSshConfigImportResult(
    val hosts: List<OpenSshImportedHost>,
    val localForwards: List<OpenSshImportedForward>,
    val warnings: List<String>
)

data class OpenSshImportedHost(
    val host: HostConnection,
    val alias: String,
    val identityFiles: List<String>
)

data class OpenSshImportedForward(
    val forward: PortForward,
    val hostAlias: String
)

object OpenSshConfigImporter {

    fun parse(
        contents: String,
        existingHosts: List<HostConnection> = emptyList(),
        existingPortForwards: List<PortForward> = emptyList(),
        nowProvider: () -> Long = { System.currentTimeMillis() },
        idProvider: () -> String = { UUID.randomUUID().toString() }
    ): OpenSshConfigImportResult {
        val parsed = parseBlocks(contents)
        val warnings = parsed.warnings.toMutableList()
        val wildcardBlocks = parsed.hostBlocks.filter { block ->
            block.patterns.any { it == "*" }
        }
        val concreteBlocks = parsed.hostBlocks.filter { block ->
            block.patterns.any { !isPattern(it) }
        }

        val seenHostNames = existingHosts.map { it.name.lowercase() }.toMutableSet()
        val seenForwardKeys = existingPortForwards.map { it.forwardKey() }.toMutableSet()
        val importedHosts = mutableListOf<OpenSshImportedHost>()
        val importedForwards = mutableListOf<OpenSshImportedForward>()
        val now = nowProvider()

        for (block in concreteBlocks) {
            val options = mergedOptions(parsed.globalOptions, wildcardBlocks, block)
            for (rawAlias in block.patterns) {
                if (isPattern(rawAlias)) continue
                val alias = rawAlias.trim()
                if (alias.isBlank()) continue
                val hostName = options.first("hostname")
                    ?.substituteHostAlias(alias)
                    ?.takeIf { !containsUnresolvedToken(it) }
                    ?: alias
                val user = options.first("user")?.takeIf { it.isNotBlank() }
                if (user == null) {
                    warnings += "Skipped $alias: missing User."
                    continue
                }
                val normalizedName = alias.lowercase()
                if (!seenHostNames.add(normalizedName)) {
                    warnings += "Skipped $alias: host name already exists."
                    continue
                }
                val port = options.first("port")?.toIntOrNull()?.takeIf { it in 1..65_535 } ?: 22
                val identityFiles = options.all("identityfile").distinct()
                val hostId = idProvider()
                val noteLines = buildList {
                    add("Imported from OpenSSH config.")
                    if (identityFiles.isNotEmpty()) {
                        add("IdentityFile entries were not imported as private keys:")
                        identityFiles.forEach { add("- $it") }
                    }
                }
                val host = HostConnection(
                    id = hostId,
                    name = alias,
                    host = hostName,
                    port = port,
                    username = user,
                    preferredAuth = AuthMethod.PASSWORD,
                    group = "OpenSSH",
                    createdEpochMillis = now,
                    updatedEpochMillis = now,
                    notes = noteLines.joinToString("\n"),
                    defaultMode = ConnectionMode.SSH
                )
                importedHosts += OpenSshImportedHost(
                    host = host,
                    alias = alias,
                    identityFiles = identityFiles
                )

                options.all("localforward").forEachIndexed { index, value ->
                    val parsedForward = parseLocalForward(value)
                    if (parsedForward == null) {
                        warnings += "Skipped LocalForward for $alias: $value"
                        return@forEachIndexed
                    }
                    val forward = PortForward(
                        id = idProvider(),
                        label = "$alias local ${parsedForward.sourcePort}",
                        group = "OpenSSH",
                        type = PortForwardType.LOCAL,
                        sourceHost = parsedForward.sourceHost,
                        sourcePort = parsedForward.sourcePort,
                        destinationHost = parsedForward.destinationHost,
                        destinationPort = parsedForward.destinationPort,
                        associatedHosts = listOf(hostId),
                        enabled = false,
                        createdEpochMillis = now,
                        updatedEpochMillis = now
                    )
                    val key = forward.forwardKey()
                    if (!seenForwardKeys.add(key)) {
                        warnings += "Skipped duplicate LocalForward for $alias: ${index + 1}."
                        return@forEachIndexed
                    }
                    importedForwards += OpenSshImportedForward(forward = forward, hostAlias = alias)
                }
            }
        }

        if (parsed.hostBlocks.isEmpty()) {
            warnings += "No Host sections found."
        }

        return OpenSshConfigImportResult(
            hosts = importedHosts,
            localForwards = importedForwards,
            warnings = warnings.distinct()
        )
    }

    private fun parseBlocks(contents: String): ParsedConfig {
        val globalOptions = OptionSet()
        val hostBlocks = mutableListOf<HostBlock>()
        val warnings = mutableListOf<String>()
        var currentBlock: HostBlock? = null
        var inMatchBlock = false

        contents.lineSequence().forEachIndexed { index, rawLine ->
            val line = stripComment(rawLine).trim()
            if (line.isBlank()) return@forEachIndexed
            val directive = parseDirective(line)
            if (directive == null) {
                warnings += "Ignored line ${index + 1}: unable to parse directive."
                return@forEachIndexed
            }
            val keyword = directive.keyword.lowercase()
            when (keyword) {
                "host" -> {
                    val patterns = splitWords(directive.value)
                    if (patterns.isEmpty()) {
                        warnings += "Ignored line ${index + 1}: Host has no patterns."
                        currentBlock = null
                    } else {
                        currentBlock = HostBlock(patterns = patterns, options = OptionSet())
                        hostBlocks += currentBlock!!
                    }
                    inMatchBlock = false
                }
                "match" -> {
                    currentBlock = null
                    inMatchBlock = true
                }
                else -> {
                    when {
                        currentBlock != null -> currentBlock!!.options.add(keyword, directive.value)
                        !inMatchBlock -> globalOptions.add(keyword, directive.value)
                    }
                }
            }
        }

        return ParsedConfig(
            globalOptions = globalOptions,
            hostBlocks = hostBlocks,
            warnings = warnings
        )
    }

    private fun mergedOptions(global: OptionSet, wildcardBlocks: List<HostBlock>, block: HostBlock): OptionSet =
        OptionSet().apply {
            merge(global)
            wildcardBlocks.forEach { merge(it.options) }
            merge(block.options)
        }

    private fun parseDirective(line: String): Directive? {
        var separatorIndex = -1
        var inQuote = false
        var quoteChar = '\u0000'
        for (index in line.indices) {
            val ch = line[index]
            when {
                inQuote && ch == quoteChar -> inQuote = false
                !inQuote && (ch == '"' || ch == '\'') -> {
                    inQuote = true
                    quoteChar = ch
                }
                !inQuote && (ch == '=' || ch.isWhitespace()) -> {
                    separatorIndex = index
                    break
                }
            }
        }
        if (separatorIndex <= 0) return null
        val keyword = line.substring(0, separatorIndex).trim()
        val value = line.substring(separatorIndex + 1).trim().removePrefix("=").trim()
        if (keyword.isBlank() || value.isBlank()) return null
        return Directive(keyword, value)
    }

    private fun stripComment(line: String): String {
        var inQuote = false
        var quoteChar = '\u0000'
        for (index in line.indices) {
            val ch = line[index]
            when {
                inQuote && ch == quoteChar -> inQuote = false
                !inQuote && (ch == '"' || ch == '\'') -> {
                    inQuote = true
                    quoteChar = ch
                }
                !inQuote && ch == '#' -> return line.substring(0, index)
            }
        }
        return line
    }

    private fun splitWords(value: String): List<String> {
        val words = mutableListOf<String>()
        val current = StringBuilder()
        var inQuote = false
        var quoteChar = '\u0000'
        for (ch in value) {
            when {
                inQuote && ch == quoteChar -> inQuote = false
                !inQuote && (ch == '"' || ch == '\'') -> {
                    inQuote = true
                    quoteChar = ch
                }
                !inQuote && ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        words += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) words += current.toString()
        return words
    }

    private fun parseLocalForward(value: String): ParsedLocalForward? {
        val words = splitWords(value)
        if (words.size < 2) return null
        val source = parseSourceEndpoint(words[0]) ?: return null
        val destination = parseDestinationEndpoint(words[1]) ?: return null
        return ParsedLocalForward(
            sourceHost = source.first,
            sourcePort = source.second,
            destinationHost = destination.first,
            destinationPort = destination.second
        )
    }

    private fun parseSourceEndpoint(value: String): Pair<String, Int>? {
        if (value.contains('/')) return null
        val endpoint = parseHostPort(value, defaultHost = "127.0.0.1") ?: return null
        return endpoint.first.ifBlank { "127.0.0.1" }.normalizeBindHost() to endpoint.second
    }

    private fun parseDestinationEndpoint(value: String): Pair<String, Int>? {
        if (value.contains('/')) return null
        return parseHostPort(value, defaultHost = null)
    }

    private fun parseHostPort(value: String, defaultHost: String?): Pair<String, Int>? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("[")) {
            val closing = trimmed.indexOf(']')
            if (closing <= 1 || closing + 2 > trimmed.length || trimmed.getOrNull(closing + 1) != ':') return null
            val host = trimmed.substring(1, closing)
            val port = trimmed.substring(closing + 2).toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
            return host to port
        }
        val separator = trimmed.lastIndexOf(':')
        if (separator < 0) {
            val port = trimmed.toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
            return (defaultHost ?: return null) to port
        }
        val host = trimmed.substring(0, separator).ifBlank { defaultHost ?: return null }
        val port = trimmed.substring(separator + 1).toIntOrNull()?.takeIf { it in 1..65_535 } ?: return null
        return host to port
    }

    private fun isPattern(value: String): Boolean =
        value.startsWith("!") || value.any { it == '*' || it == '?' }

    private fun String.substituteHostAlias(alias: String): String =
        replace("%h", alias).replace("%n", alias)

    private fun containsUnresolvedToken(value: String): Boolean =
        Regex("%[A-Za-z%]").containsMatchIn(value)

    private fun String.normalizeBindHost(): String =
        if (this == "*") "0.0.0.0" else this

    private fun PortForward.forwardKey(): String =
        listOf(type.name, sourceHost, sourcePort.toString(), destinationHost, destinationPort.toString())
            .joinToString("|")

    private data class Directive(val keyword: String, val value: String)

    private data class ParsedConfig(
        val globalOptions: OptionSet,
        val hostBlocks: List<HostBlock>,
        val warnings: List<String>
    )

    private data class HostBlock(
        val patterns: List<String>,
        val options: OptionSet
    )

    private data class ParsedLocalForward(
        val sourceHost: String,
        val sourcePort: Int,
        val destinationHost: String,
        val destinationPort: Int
    )

    private class OptionSet {
        private val values = linkedMapOf<String, MutableList<String>>()

        fun add(keyword: String, value: String) {
            values.getOrPut(keyword.lowercase()) { mutableListOf() } += value
        }

        fun merge(other: OptionSet) {
            other.values.forEach { (keyword, incoming) ->
                values.getOrPut(keyword) { mutableListOf() } += incoming
            }
        }

        fun first(keyword: String): String? = values[keyword.lowercase()]?.lastOrNull()

        fun all(keyword: String): List<String> = values[keyword.lowercase()].orEmpty()
    }
}
