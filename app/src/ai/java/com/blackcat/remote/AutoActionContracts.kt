package com.blackcat.remote

/** Positive argument grammars for the diagnostic release. A familiar executable is not authority. */
internal class AutoActionContract(val id: String, val purpose: String, val evidence: String)

internal object AutoActionContracts {
    private fun name(value: String) = value.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.@:-]{0,127}"))
    private fun count(value: String) = value.toIntOrNull() in 1..200
    private fun path(value: String) = value.startsWith('/') && value.length <= 180 &&
        value.split('/').none { it == ".." } && value.matches(Regex("/[A-Za-z0-9_./:@+-]*"))
    private fun flags(args: List<String>, permitted: Set<String>) = args.all { it in permitted }
    private fun contract(id: String, purpose: String, evidence: String, allowed: Boolean) =
        if (allowed) AutoActionContract(id, purpose, evidence) else null

    fun match(words: List<String>): AutoActionContract? {
        val tool = words.firstOrNull() ?: return null
        val a = words.drop(1)
        return when (tool) {
            "id", "whoami", "groups" -> contract("identity", "I'm checking which account we're working with and what access it has.",
                "The account or group information must be visible.",
                if (tool == "whoami") a.isEmpty() else a.size <= 2 && a.all { it in setOf("-u", "-un", "-g", "-G", "-Gn", "-gn") || name(it) })
            "uname" -> contract("kernel", "I'm checking the Linux version so the next steps suit this computer.",
                "The requested kernel information must be visible.", flags(a, setOf("-a", "-r", "-s", "-m")))
            "cat" -> contract("read-file", "I'm reading the diagnostic information so we can choose the next step from the evidence.",
                "Collect the complete requested file or report that it could not be read.", a.size == 1 && path(a[0]) &&
                    (a[0] in setOf("/etc/os-release", "/etc/debian_version", "/etc/redhat-release", "/etc/lsb-release") ||
                        a[0].startsWith("/var/log/") || a[0].startsWith("/tmp/bc-") || a[0].startsWith("/tmp/blackcat-")))
            "uptime" -> contract("load", "I'm checking how long the computer has been running and how busy it is.",
                "Uptime and load must be visible.", flags(a, setOf("-p", "-s")))
            "df", "free", "lsblk" -> contract("capacity", when (tool) {
                "df" -> "I'm checking available disk space. A full disk can explain several kinds of failure."
                "free" -> "I'm checking available memory to see whether resource pressure could be involved."
                else -> "I'm checking the disks and filesystems so we understand this computer's storage."
            }, "The requested capacity information must be visible.", flags(a, when (tool) {
                "df" -> setOf("-h", "-T", "-i"); "free" -> setOf("-h", "-m", "-g"); else -> setOf("-f", "-a", "-J", "--json")
            }))
            "ip" -> contract("network", "I'm checking the network configuration without changing any connections.",
                "The requested addresses, routes or links must be visible.", a in listOf(
                    listOf("-brief", "addr"), listOf("-br", "addr"), listOf("addr", "show"), listOf("address", "show"),
                    listOf("route"), listOf("route", "show"), listOf("link", "show")))
            "ss" -> contract("sockets", "I'm checking network sockets to see which connections or listening services are present.",
                "The socket listing or an explicit empty result must be visible.", a.isNotEmpty() && a.all {
                    it.startsWith('-') && it.length > 1 && it.drop(1).all { c -> c in "altnupx46Hreo" }
                })
            "systemctl" -> contract("services", "I'm checking the service manager so we can see the relevant service state before taking action.",
                "The requested service state or failure must be visible.", services(a))
            "journalctl" -> contract("logs", "I'm checking a bounded section of the system log for clues about the problem.",
                "Collect the requested log section, including any error or permission failure.", logs(a))
            "hostnamectl" -> contract("host", "I'm checking the computer's operating system information.",
                "The host information must be visible.", a.isEmpty() || a == listOf("status"))
            "ls", "stat" -> contract("file-metadata", "I'm checking file information without changing permissions or contents.",
                "The requested file information or error must be visible.", a.isNotEmpty() && a.all {
                    it in setOf("-l", "-a", "-la", "-lh", "-ld", "-ldh", "-h", "--") || path(it)
                } && a.any { path(it) })
            "getent" -> contract("account-lookup", "I'm checking the system's account or name records so we can use the actual configuration.",
                "The requested records or lookup failure must be visible.", a.size in 1..2 && a[0] in setOf("passwd", "group", "hosts") &&
                    (a.size == 1 || name(a[1])))
            "ufw" -> contract("firewall-status", "I'm checking the firewall's current rules without changing access.",
                "The firewall status and requested rules must be visible.", a in listOf(listOf("status"), listOf("status", "verbose"), listOf("status", "numbered")))
            "firewall-cmd" -> contract("firewall-status", "I'm checking the active firewall configuration without changing it.",
                "The requested firewall state must be visible.", a.size == 1 && a[0] in setOf("--state", "--list-all", "--get-active-zones", "--list-services", "--list-ports"))
            "sshd" -> contract("ssh-check", "I'm checking the SSH configuration without changing or restarting the service.",
                "The configuration check result must be visible; silence alone is insufficient.", a == listOf("-t") || a == listOf("-T"))
            "sysctl" -> contract("kernel-settings", "I'm reading the current kernel settings without changing them.",
                "The requested settings or error must be visible.", a.isNotEmpty() && a.all { it == "-a" || name(it) && '.' in it })
            "apt", "apt-get", "dnf", "pacman", "zypper" -> contract("packages", "I'm checking package information before deciding whether any change is needed.",
                "The requested package information must be visible.", packages(tool, a))
            "aa-status", "sestatus" -> contract("security-status", "I'm checking which security controls are active on this computer.",
                "The security status or availability error must be visible.", a.isEmpty())
            else -> null
        }
    }

    private fun services(a: List<String>): Boolean {
        val words = a.filterNot { it in setOf("--no-pager", "--plain", "--full") }
        if (words.firstOrNull() == "--failed" && words.drop(1).all { it == "--type=service" }) return true
        if (words.firstOrNull() in setOf("list-units", "list-unit-files")) return words.drop(1).all {
            it in setOf("--type=service", "--state=running", "--state=failed", "--failed")
        }
        return words.size in 2..5 && words[0] in setOf("status", "show", "is-active", "is-enabled") && words.drop(1).all(::name)
    }

    private fun logs(a: List<String>): Boolean {
        if (a.isEmpty()) return false
        var bounded = false
        var i = 0
        while (i < a.size) {
            when (val v = a[i++]) {
                "--no-pager", "--boot", "-b", "--utc", "--quiet" -> Unit
                "-n", "--lines" -> { if (i >= a.size || !count(a[i++])) return false; bounded = true }
                "-u", "--unit" -> if (i >= a.size || !name(a[i++])) return false
                "-p", "--priority" -> if (i >= a.size || a[i++].toIntOrNull() !in 0..7) return false
                else -> if (v.startsWith("--lines=") && count(v.substringAfter('='))) bounded = true
                    else if (!(v.startsWith("--unit=") && name(v.substringAfter('=')))) return false
            }
        }
        return bounded
    }

    private fun packages(tool: String, a: List<String>): Boolean = when (tool) {
        "apt" -> a in listOf(listOf("list", "--installed"), listOf("list", "--upgradable")) ||
            a.size == 2 && a[0] == "show" && name(a[1])
        "dnf" -> a in listOf(listOf("list", "installed"), listOf("list", "updates")) || a.size == 2 && a[0] == "info" && name(a[1])
        "pacman" -> a == listOf("-Q") || a.size == 2 && a[0] in setOf("-Qi", "-Qe") && name(a[1])
        "zypper" -> a == listOf("packages", "--installed-only") || a.size == 2 && a[0] == "info" && name(a[1])
        else -> false
    }
}
