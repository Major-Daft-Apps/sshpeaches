package com.majordaftapps.sshpeaches.app.data.ssh

data class HostSystemInfo(
    val osName: String? = null,
    val osVersion: String? = null,
    val kernel: String? = null,
    val architecture: String? = null,
    val hostname: String? = null,
    val cpu: String? = null,
    val cpuCores: String? = null,
    val memory: String? = null,
    val freeOutput: String? = null,
    val disk: String? = null,
    val dfOutput: String? = null,
    val uptime: String? = null,
    val error: String? = null,
    val collectedAtEpochMillis: Long = 0L
) {
    val hasLiveData: Boolean
        get() = listOf(
            osName,
            osVersion,
            kernel,
            cpu,
            memory,
            freeOutput,
            disk,
            dfOutput,
            uptime,
            hostname
        ).any { !it.isNullOrBlank() }
}

object HostSystemInfoCollector {
    private const val FREE_BEGIN = "BEGIN_FREE"
    private const val FREE_END = "END_FREE"
    private const val DF_BEGIN = "BEGIN_DF"
    private const val DF_END = "END_DF"

    val REMOTE_COMMAND: String = run {
        val d = "$"
        """
        sh -c 'printf "UNAME_S=%s\n" "${d}(uname -s 2>/dev/null)"; printf "UNAME_R=%s\n" "${d}(uname -r 2>/dev/null)"; printf "UNAME_M=%s\n" "${d}(uname -m 2>/dev/null)"; printf "HOSTNAME=%s\n" "${d}(uname -n 2>/dev/null)"; if [ -r /etc/os-release ]; then . /etc/os-release; printf "PRETTY_NAME=%s\n" "${d}PRETTY_NAME"; printf "VERSION_ID=%s\n" "${d}VERSION_ID"; printf "ID=%s\n" "${d}ID"; elif command -v sw_vers >/dev/null 2>&1; then printf "PRETTY_NAME=%s %s\n" "${d}(sw_vers -productName 2>/dev/null)" "${d}(sw_vers -productVersion 2>/dev/null)"; fi; if [ -r /proc/cpuinfo ]; then printf "CPU=%s\n" "${d}(awk -F: "/model name/{gsub(/^[ \t]+/,\"\",${d}2); print ${d}2; exit}" /proc/cpuinfo)"; printf "CPU_CORES=%s\n" "${d}(grep -c ^processor /proc/cpuinfo)"; elif command -v sysctl >/dev/null 2>&1; then printf "CPU=%s\n" "${d}(sysctl -n machdep.cpu.brand_string 2>/dev/null)"; printf "CPU_CORES=%s\n" "${d}(sysctl -n hw.ncpu 2>/dev/null)"; fi; if [ -r /proc/meminfo ]; then printf "MEM_TOTAL_KB=%s\n" "${d}(awk "/MemTotal/{print ${d}2}" /proc/meminfo)"; printf "MEM_AVAIL_KB=%s\n" "${d}(awk "/MemAvailable/{print ${d}2}" /proc/meminfo)"; elif command -v sysctl >/dev/null 2>&1; then printf "MEM_TOTAL_B=%s\n" "${d}(sysctl -n hw.memsize 2>/dev/null)"; fi; printf "DISK=%s\n" "${d}(df -k / 2>/dev/null | awk "NR==2{print ${d}2\" \"${d}3\" \"${d}4\" \"${d}5}")"; printf "UPTIME=%s\n" "${d}(uptime 2>/dev/null | sed "s/^ *//")"; printf "${FREE_BEGIN}\n"; free -h 2>/dev/null; printf "${FREE_END}\n"; printf "${DF_BEGIN}\n"; df -h -x tmpfs -x devtmpfs -x squashfs -x overlay -x iso9660 -x fuse.lxcfs -x fuse.gvfsd-fuse 2>/dev/null | awk "NR==1 || (${d}1 !~ /loop/ && ${d}1 !~ /^tmpfs/ && ${d}1 !~ /^devtmpfs/)"; printf "${DF_END}\n"'
        """.trimIndent()
    }

    fun parse(raw: String, nowEpochMillis: Long = System.currentTimeMillis()): HostSystemInfo {
        val freeOutput = extractBlock(raw, FREE_BEGIN, FREE_END)
        val dfOutput = extractBlock(raw, DF_BEGIN, DF_END)?.let(::filterPhysicalDf)
        val scalarRaw = raw
            .replace(Regex("$FREE_BEGIN[\\s\\S]*?$FREE_END"), "")
            .replace(Regex("$DF_BEGIN[\\s\\S]*?$DF_END"), "")
        val values = linkedMapOf<String, String>()
        scalarRaw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            val index = trimmed.indexOf('=')
            if (index <= 0) return@forEach
            val key = trimmed.substring(0, index).trim()
            if (!key.matches(Regex("[A-Z0-9_]+"))) return@forEach
            val value = trimmed.substring(index + 1).trim()
            if (key.isNotBlank() && value.isNotBlank()) {
                values[key] = value
            }
        }
        val pretty = values["PRETTY_NAME"]
        val osName = pretty?.takeIf { it.isNotBlank() }
            ?: values["ID"]?.replaceFirstChar { it.uppercase() }
            ?: values["UNAME_S"]
        val memory = freeOutput
            ?: formatMemory(values["MEM_TOTAL_KB"], values["MEM_AVAIL_KB"], values["MEM_TOTAL_B"])
        val disk = dfOutput ?: formatDisk(values["DISK"])
        return HostSystemInfo(
            osName = osName,
            osVersion = values["VERSION_ID"],
            kernel = values["UNAME_R"],
            architecture = values["UNAME_M"],
            hostname = values["HOSTNAME"],
            cpu = values["CPU"],
            cpuCores = values["CPU_CORES"],
            memory = memory,
            freeOutput = freeOutput,
            disk = disk,
            dfOutput = dfOutput,
            uptime = values["UPTIME"],
            collectedAtEpochMillis = nowEpochMillis
        )
    }

    internal fun formatIecBytes(bytes: Long): String {
        if (bytes < 0L) return "0 B"
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit += 1
        }
        val formatted = if (unit == 0) value.toLong().toString() else String.format("%.1f", value)
        return "$formatted ${units[unit]}"
    }

    internal fun extractBlock(raw: String, begin: String, end: String): String? {
        val startToken = "\n$begin\n"
        val endToken = "\n$end"
        val normalized = if (raw.startsWith(begin)) "\n$raw" else raw
        val start = normalized.indexOf(startToken)
        if (start < 0) {
            // Handle begin at start of string without leading newline already covered;
            // also handle begin right after another line without double newline edge cases.
            val altStart = normalized.indexOf(begin)
            if (altStart < 0) return null
            val contentStart = altStart + begin.length
            val endIndex = normalized.indexOf(end, contentStart)
            if (endIndex < 0) return null
            return normalized.substring(contentStart, endIndex).trim().takeIf { it.isNotBlank() }
        }
        val contentStart = start + startToken.length
        val endIndex = normalized.indexOf(endToken, contentStart).takeIf { it >= 0 }
            ?: normalized.indexOf("\n$end\n", contentStart)
        if (endIndex < 0) return null
        return normalized.substring(contentStart, endIndex).trim().takeIf { it.isNotBlank() }
    }

    internal fun filterPhysicalDf(raw: String): String? {
        val lines = raw.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) return null
        val header = lines.first()
        val body = lines.drop(1).filter { line ->
            val source = line.split(Regex("\\s+")).firstOrNull().orEmpty()
            source.isNotBlank() &&
                !source.contains("loop", ignoreCase = true) &&
                !source.equals("tmpfs", ignoreCase = true) &&
                !source.equals("devtmpfs", ignoreCase = true) &&
                !source.equals("overlay", ignoreCase = true) &&
                !source.equals("squashfs", ignoreCase = true)
        }
        if (body.isEmpty()) return null
        return (listOf(header) + body).joinToString("\n")
    }

    private fun formatMemory(totalKb: String?, availKb: String?, totalBytes: String?): String? {
        val totalFromKb = totalKb?.toLongOrNull()?.times(1024L)
        val availFromKb = availKb?.toLongOrNull()?.times(1024L)
        val totalFromB = totalBytes?.toLongOrNull()
        val total = totalFromKb ?: totalFromB ?: return null
        return if (availFromKb != null) {
            "${formatIecBytes(availFromKb)} available / ${formatIecBytes(total)}"
        } else {
            formatIecBytes(total)
        }
    }

    private fun formatDisk(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val parts = raw.trim().split(Regex("\\s+"))
        if (parts.size < 3) return raw.trim()
        val totalKb = parts[0].toLongOrNull()
        val usedKb = parts[1].toLongOrNull()
        val percent = parts.getOrNull(3)
        if (totalKb == null || usedKb == null) return raw.trim()
        val used = formatIecBytes(usedKb * 1024L)
        val total = formatIecBytes(totalKb * 1024L)
        return if (percent.isNullOrBlank()) "$used used of $total" else "$used used of $total ($percent)"
    }
}
