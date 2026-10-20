package com.blackcat.remote

import java.util.Locale

/** Local retrieval, not a generative model. Queries and results live only in this panel's memory. */
internal class OfflineCommandSearch(private val data: OfflineLearningData) {
    enum class Kind { EXAMPLE, COMMAND, TOOL }
    data class Hit(val id: String, val kind: Kind, val title: String, val summary: String,
        val topic: String, val score: Int)
    private data class Document(val id: String, val kind: Kind, val title: String,
        val summary: String, val topic: String, val words: Set<String>, val name: String,
        val compatibility: String = "", val kali: Boolean = false, val general: Boolean = true,
        val risk: LinuxCommandRisk = LinuxCommandRisk.LOW) {
        val cleanTitle = normalize(title)
        val cleanName = normalize(name)
    }
    private data class Intent(val words: Set<String>, val related: Set<String>, val preferred: Set<String>)
    private val documents = buildList {
        data.commands.forEach { c -> add(Document(c.id, Kind.EXAMPLE, c.title, c.command,
            "${c.category} / ${c.topic}", words(listOf(c.title, c.topic, c.category,
                c.command, c.explanation, c.expectedResult, c.tags.joinToString(" ")).joinToString(" ")),
            c.command, c.compatibility, c.category == "Kali Linux", c.category != "Kali Linux", c.risk)) }
        data.entries.filter { it.id in data.workflows.commands }.forEach { c ->
            val owners = c.toolIds.mapNotNull { data.toolById[it] }
            val summary = c.summary.ifBlank { owners.joinToString(" · ") { it.summary } }
            add(Document(c.id, Kind.COMMAND, c.invocation, summary.take(300), topic(c.invocation, summary),
                words("${c.invocation} $summary ${owners.joinToString(" ") { it.name }}"),
                c.invocation, kali = c.id in data.workflows.kaliEntryIds,
                general = owners.any { "linux" in it.distributions }))
        }
        data.tools.forEach { c -> add(Document(c.id, Kind.TOOL, c.name, c.summary.take(300),
            topic(c.name, c.summary), words("${c.name} ${c.summary} ${c.packages.joinToString(" ")}"), c.name,
            kali = "kali" in c.distributions, general = "linux" in c.distributions)) }
    }

    /** All matching topics/results are retained; the UI paginates instead of silently truncating. */
    fun search(query: String, os: String): List<Hit> {
        val clean = normalize(query.take(200))
        val terms = words(clean) - STOP
        val raw = query.trim().lowercase(Locale.ROOT)
        if (raw.isEmpty() || (terms.isEmpty() && documents.none { it.name == raw })) return emptyList()
        val intents = INTENTS.filter { intent -> terms.any { it in intent.words } }
        val expanded = terms + intents.flatMap { it.related }
        val preferred = intents.flatMap { it.preferred }.toSet()
        val inspect = terms.any { it in setOf("check", "show", "list", "view", "inspect", "see", "find", "connections") }
        return documents.asSequence().filter { compatible(it, os) }.mapNotNull { d ->
            val exact = if (clean.isEmpty()) d.name == raw else d.cleanTitle == clean || d.cleanName == clean
            val matched = terms.count { it in d.words || it == d.cleanName }
            val related = expanded.count { it in d.words }
            val first = d.name.substringBefore(' ')
            val preferredMatch = first in preferred
            if (!exact && matched == 0 && !preferredMatch && related < 2) return@mapNotNull null
            var score = matched * 35 + related * 4
            if (exact) score += 1000
            if (preferredMatch) score += 160
            if (terms.all { it in d.words }) score += 45
            if (d.kind == Kind.EXAMPLE) score += 35
            if (inspect && d.risk != LinuxCommandRisk.LOW) score -= 100
            Hit(d.id, d.kind, d.title, d.summary, d.topic, score)
        }.filter { it.score > 0 }.sortedWith(compareByDescending<Hit> { it.score }
            .thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }).toList()
    }

    private fun compatible(d: Document, os: String): Boolean {
        val target = os.lowercase(Locale.ROOT)
        if (listOf("windows", "macos", "freebsd", "powershell").any { it in target }) return false
        val kali = "kali" in target
        if (!kali && d.kali && !d.general) return false
        val binary = d.name.removePrefix("sudo -n ").removePrefix("sudo ").substringBefore(' ')
        val debian = listOf("debian", "ubuntu", "mint", "kali").any { it in target }
        val rpm = listOf("fedora", "red hat", "rocky", "alma", "rhel", "suse").any { it in target }
        if (binary in setOf("apt", "apt-get", "apt-cache", "dpkg", "dpkg-query") && !debian) return false
        if (binary in setOf("dnf", "yum") && (!rpm || "suse" in target)) return false
        if (binary == "rpm" && !rpm) return false
        if (binary == "pacman" && "arch" !in target) return false
        if (binary == "zypper" && "suse" !in target) return false
        if (binary == "apk" && "alpine" !in target) return false
        val c = d.compatibility.lowercase(Locale.ROOT)
        if ("kali" in c && !kali) return false
        if (("debian" in c || "ubuntu" in c) && !listOf("debian", "ubuntu", "mint", "kali").any { it in target }) return false
        if (("fedora" in c || "rpm-based" in c) && !listOf("fedora", "red hat", "rocky", "alma", "rhel", "suse").any { it in target }) return false
        if ("arch linux" in c && "arch" !in target) return false
        if ("suse" in c && "suse" !in target) return false
        if ("alpine" in c && "alpine" !in target) return false
        if (("bash shell" in c || "systemd" in c) && ("alpine" in target || "ash" in target)) return false
        return true
    }

    companion object {
        private val separators = Regex("[^\\p{L}\\p{N}_.+/-]+")
        @Volatile private var cached: Pair<OfflineLearningData, OfflineCommandSearch>? = null
        fun forData(data: OfflineLearningData): OfflineCommandSearch = cached?.takeIf { it.first === data }?.second
            ?: synchronized(this) {
                cached?.takeIf { it.first === data }?.second ?: OfflineCommandSearch(data).also { cached = data to it }
            }
        val SYSTEMS = listOf("Kali Linux", "Ubuntu Linux", "Debian Linux", "Linux Mint", "Fedora Linux",
            "Red Hat Enterprise Linux", "Rocky Linux", "AlmaLinux", "openSUSE Linux", "Arch Linux", "Alpine Linux")
        fun initialSystem(target: String): String = SYSTEMS.firstOrNull { target.contains(it, true) } ?: target
        private fun normalize(value: String) = separators.replace(value.lowercase(Locale.ROOT), " ").trim()
        private fun words(value: String) = normalize(value).split(' ').filter { it.length >= 2 }.map {
            when (it) { "connections", "connected", "connection" -> "connections"; "files" -> "file";
                "processes" -> "process"; "ports" -> "port"; "services" -> "service"; "errors" -> "error"; else -> it }
        }.toSet()
        private val STOP = setOf("i", "want", "to", "the", "all", "my", "me", "a", "an", "is", "are", "how", "can", "please", "do", "of", "on", "with", "and", "for", "what", "this", "computer", "system")
        private fun intent(trigger: String, related: String, preferred: String) = Intent(words(trigger), words(related), preferred.split(' ').toSet())
        private val INTENTS = listOf(
            intent("internet connections network networking sockets port", "network connections socket port listening tcp udp interface address route", "ss ip netstat lsof nmcli"),
            intent("wifi wi-fi wireless", "wireless wifi radio network interface", "iw nmcli rfkill ip"),
            intent("dns domain hostname resolve lookup", "dns domain name resolver address", "dig resolvectl nslookup host"),
            intent("disk storage space full drive filesystem", "disk filesystem space storage mount usage", "df du lsblk findmnt"),
            intent("memory ram swap", "memory ram swap usage", "free vmstat swapon"),
            intent("cpu slow performance load", "cpu process performance load usage", "uptime top ps vmstat"),
            intent("process running programs kill", "process running program pid", "ps pgrep top"),
            intent("service startup boot failed", "service boot failed status systemd", "systemctl journalctl"),
            intent("logs error troubleshoot", "log journal error diagnostics", "journalctl dmesg tail"),
            intent("permissions access owner ownership", "permissions owner access file", "stat ls getfacl id"),
            intent("firewall hackers lockdown secure security", "security firewall rules listening port audit", "ss nft ufw firewall-cmd"),
            intent("package install installed software update", "package installed version software", "dpkg-query apt rpm dnf pacman zypper apk"),
            intent("file folder directory locate search", "file directory path find search", "find locate ls rg"),
            intent("battery power laptop", "battery power supply laptop", "upower acpi"),
            intent("download url website http", "transfer url http download", "curl wget"),
            intent("git repository changes commit", "git repository revision changes", "git"))
        private fun topic(name: String, summary: String): String {
            val text = words("$name $summary")
            return when {
                name.substringBefore(' ') in setOf("ss", "ip", "netstat", "lsof", "nmcli", "iw", "dig", "host", "nslookup", "curl", "wget") -> "Command workflows / Network and internet"
                text.any { it in setOf("network", "tcp", "udp", "socket", "dns", "wireless") } -> "Command workflows / Network and internet"
                text.any { it in setOf("disk", "filesystem", "partition", "mount", "storage") } -> "Command workflows / Disks and filesystems"
                text.any { it in setOf("password", "vulnerability", "security", "exploit", "forensic", "pentesting") } -> "Command workflows / Security and assessment"
                text.any { it in setOf("file", "directory", "text", "archive") } -> "Command workflows / Files and text"
                text.any { it in setOf("process", "memory", "cpu", "service", "systemd", "package") } -> "Command workflows / System and services"
                name.startsWith("git") -> "Command workflows / Git and development"
                else -> "Command workflows / Tools and applications"
            }
        }
    }
}
