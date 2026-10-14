package com.blackcat.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Exact read-only allowlist. Model risk labels never authorize unattended input. */
internal object AutoRunPolicy {
    private val readOnly = setOf(
        "id", "whoami", "uname -a", "uname -r", "cat /etc/os-release", "uptime",
        "df -h", "free -h", "lsblk -f", "ip -brief addr", "ip route", "ss -lntup",
        "systemctl --failed --no-pager", "systemctl list-unit-files --no-pager",
        "systemctl list-units --type=service --state=running --no-pager"
    )
    fun canRun(step: SuggestedCommand) = step.command in readOnly && step.risk == "low" &&
        !step.requiresAdmin && CommandPolicy.problem(step.command) == null

    /** Fail closed for shell programs, compound commands, destructive tools and interactive UIs. */
    fun canRunReviewed(step: SuggestedCommand): Boolean {
        if (CommandPolicy.problem(step.command) != null || step.command.any { it in ";&|`$<>\\\n\r" }) return false
        val parts = shellWords(step.command) ?: return false
        val words = if (parts.firstOrNull() == "sudo") parts.drop(1).dropWhile { it == "-n" } else parts
        val tool = words.firstOrNull() ?: return false
        if ('/' in tool || tool.startsWith('-')) return false
        return tool in setOf("id", "whoami", "uname", "cat", "uptime", "df", "free", "lsblk", "ip", "ss",
            "systemctl", "journalctl", "hostnamectl", "stat", "ls", "getent", "groups", "last", "lastlog",
            "apt", "apt-get", "dnf", "pacman", "zypper", "ufw", "firewall-cmd", "sshd", "sysctl",
            "chmod", "chown", "cp", "install", "grep", "test", "auditctl", "aa-status", "sestatus") &&
            words.drop(1).none { it in setOf("reboot", "poweroff", "halt", "rescue", "emergency", "--delete", "-delete", "-exec", "-execdir", "-fprint", "-fprintf", "-fls", "--purge") } &&
            !(tool in setOf("apt", "apt-get", "dnf", "pacman", "zypper") && words.any { it in setOf("remove", "purge", "autoremove", "-R", "--remove") }) &&
            !(tool == "systemctl" && words.any { it in setOf("stop", "disable", "mask", "isolate", "kill", "edit") }) &&
            !(tool in setOf("chmod", "chown") && words.any { it == "--recursive" || (it.startsWith('-') && 'R' in it) }) &&
            words.none { '*' in it || '?' in it || it in setOf("/dev/zero", "/dev/random", "/etc/shadow", "/etc/passwd") }
    }

    private fun shellWords(command: String): List<String>? {
        val result = mutableListOf<String>()
        val word = StringBuilder()
        var quote: Char? = null
        for (character in command) {
            when {
                quote == character -> quote = null
                quote == null && character in "\"'" -> quote = character
                quote == null && character == ' ' -> if (word.isNotEmpty()) { result.add(word.toString()); word.clear() }
                else -> word.append(character)
            }
        }
        if (quote != null) return null
        if (word.isNotEmpty()) result.add(word.toString())
        return result
    }
}

internal enum class AutoStage { REVIEW_STEP, TYPING, MANUAL_ENTER, WAITING, ANALYZING, REVIEW_RESULT, BLOCKED, FINISHED, STOPPED }

/** Foreground, in-memory scope. No approval survives a stop, host change or plan revision. */
internal class AutoRunSession(
    steps: List<SuggestedCommand>, val host: HostSession, revision: Long,
    val runReadOnly: Boolean, startIndex: Int, val runAllReviewed: Boolean = false
) {
    var steps = steps.toList()
        private set
    var revision = revision
        private set
    val automatic get() = runReadOnly || runAllReviewed
    fun canRun(step: SuggestedCommand) = if (runAllReviewed) AutoRunPolicy.canRunReviewed(step) else runReadOnly && AutoRunPolicy.canRun(step)
    private val originallyApproved = steps.map { it.command }.toSet()
    private val executed = mutableSetOf<String>()
    var revisions = 0
        private set
    var index = startIndex
        private set
    var stage = AutoStage.REVIEW_STEP
        private set
    var captures = 0
        private set
    private var generation = 0L
    private var alreadyApproved = false
    val current get() = steps.getOrNull(index)
    val active get() = stage !in setOf(AutoStage.STOPPED, AutoStage.FINISHED, AutoStage.BLOCKED)

    init { require(startIndex in steps.indices && steps.size <= PlanCodec.MAX_COMMANDS) }

    fun valid(currentHost: HostSession?, currentRevision: Long) = active &&
        currentHost == host && currentRevision == revision

    fun approve(currentHost: HostSession?, currentRevision: Long, human: Boolean): AutoStepApproval? {
        val step = current ?: return null
        if (!valid(currentHost, currentRevision) || stage != AutoStage.REVIEW_STEP || alreadyApproved) return null
        val enter = canRun(step)
        if (!human && !enter) return null
        alreadyApproved = true
        stage = AutoStage.TYPING
        return AutoStepApproval(CommandApproval(step.command, host), enter, generation, index)
    }

    fun accepts(approval: AutoStepApproval, currentHost: HostSession?, currentRevision: Long) =
        valid(currentHost, currentRevision) && stage == AutoStage.TYPING &&
        approval.generation == generation && approval.index == index && approval.text.host == host &&
        approval.text.command == current?.command && (!approval.enter || canRun(current!!))

    fun typed(approval: AutoStepApproval): Boolean {
        if (stage != AutoStage.TYPING || approval.generation != generation || approval.index != index) return false
        executed.add(approval.text.command)
        stage = if (approval.enter) AutoStage.WAITING else AutoStage.MANUAL_ENTER
        return true
    }

    fun userRanCommand(): Boolean {
        if (stage != AutoStage.MANUAL_ENTER) return false
        stage = AutoStage.WAITING
        return true
    }

    fun captured(): Long? {
        if (stage != AutoStage.WAITING || captures >= MAX_CAPTURES) { block(); return null }
        captures++
        stage = AutoStage.ANALYZING
        return generation
    }

    fun preflightCaptured() { require(captures == 0); captures++ }

    fun observed(token: Long): Boolean {
        if (token != generation || stage != AutoStage.ANALYZING) return false
        stage = AutoStage.REVIEW_RESULT
        return true
    }

    /** Vision is advisory; this progress is never copied into human-confirmed workspace results. */
    fun next(visualReady: Boolean, human: Boolean): Boolean {
        if (stage != AutoStage.REVIEW_RESULT || (!human && (!automatic || !visualReady))) return false
        index++
        generation++
        alreadyApproved = false
        stage = if (index >= steps.size) AutoStage.FINISHED else AutoStage.REVIEW_STEP
        return true
    }

    fun block() { generation++; stage = AutoStage.BLOCKED }
    fun stop() { generation++; stage = AutoStage.STOPPED }

    fun canAdopt(candidate: CommandPlan): Boolean = stage == AutoStage.REVIEW_RESULT && runAllReviewed && revisions < 3 &&
        candidate.questions.isEmpty() && candidate.commands.isNotEmpty() &&
        index + 1 + candidate.commands.size <= PlanCodec.MAX_COMMANDS && candidate.commands.all {
            (it.command in originallyApproved || AutoRunPolicy.canRun(it)) && canRun(it) &&
                (it.command !in executed || AutoRunPolicy.canRun(it))
        }

    fun adopt(candidate: CommandPlan, newRevision: Long): Boolean {
        if (!canAdopt(candidate) || stage != AutoStage.REVIEW_RESULT) return false
        steps = steps.take(index + 1) + candidate.commands
        revision = newRevision
        revisions++
        index++
        generation++
        alreadyApproved = false
        stage = AutoStage.REVIEW_STEP
        return true
    }
    companion object { const val MAX_CAPTURES = 41 }
}

internal class AutoStepApproval internal constructor(
    val text: CommandApproval, val enter: Boolean, val generation: Long, val index: Int
) {
    override fun toString() = "AutoStepApproval(redacted)"
}

/** Isolated UI12 exception. The ordinary reviewed sender still cannot append Enter. */
internal object AutoReadOnlySender {
    suspend fun send(approval: AutoStepApproval, session: AutoRunSession, target: CommandTarget,
                     revision: () -> Long): SendOutcome {
        if (!session.accepts(approval, target.current(), revision())) return SendOutcome.REJECTED
        val outcome = ApprovedCommandSender.send(approval.text, target)
        if (outcome != SendOutcome.REPORTS_ACCEPTED || !approval.enter) return outcome
        currentCoroutineContext().ensureActive()
        if (!session.accepts(approval, target.current(), revision())) return SendOutcome.HOST_CHANGED
        // Only an explicitly consented, supported reviewed step can reach Enter here.
        try {
            return if (target.sendKey(session.host, 0, 40)) SendOutcome.REPORTS_ACCEPTED else SendOutcome.REJECTED
        } finally { if (target.current() == session.host) target.sendKey(session.host, 0, 0) }
    }
}
