package com.blackcat.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.IdentityHashMap

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
        return AutoActionContracts.match(words) != null && tool in setOf("id", "whoami", "uname", "cat", "uptime", "df", "free", "lsblk", "ip", "ss",
            "systemctl", "journalctl", "hostnamectl", "stat", "ls", "getent", "groups", "last", "lastlog",
            "apt", "apt-get", "dnf", "pacman", "zypper", "ufw", "firewall-cmd", "sshd", "sysctl",
            "chmod", "chown", "cp", "install", "grep", "test", "auditctl", "aa-status", "sestatus") &&
            words.drop(1).none { it in setOf("reboot", "poweroff", "halt", "rescue", "emergency", "--delete", "-delete", "-exec", "-execdir", "-fprint", "-fprintf", "-fls", "--purge") } &&
            !(tool in setOf("apt", "apt-get", "dnf", "pacman", "zypper") && words.any { it in setOf("remove", "purge", "autoremove", "-R", "--remove") }) &&
            !(tool == "systemctl" && words.any { it in setOf("stop", "disable", "mask", "isolate", "kill", "edit") }) &&
            !(tool in setOf("chmod", "chown") && words.any { it == "--recursive" || (it.startsWith('-') && 'R' in it) }) &&
            words.none { '*' in it || '?' in it || it in setOf("/dev/zero", "/dev/random", "/etc/shadow", "/etc/passwd") }
    }

    fun isReadOnly(step: SuggestedCommand): Boolean {
        // This milestone admits diagnostic contracts only. Keep classification tied to
        // that positive grammar rather than guessing from flags or a model risk label.
        return canRunReviewed(step)
    }

    fun nonInteractive(command: String): String {
        val words = shellWords(command) ?: return command
        if (words.firstOrNull() != "sudo") return command
        return "sudo -n " + words.drop(1).dropWhile { it == "-n" }.joinToString(" ") { "'" + it.replace("'", "'\"'\"'") + "'" }
    }

    fun contract(step: SuggestedCommand): AutoActionContract? {
        if (!canRunReviewed(step)) return null
        val words = shellWords(step.command) ?: return null
        return AutoActionContracts.match(if (words.firstOrNull() == "sudo") words.drop(1).dropWhile { it == "-n" } else words)
    }

    fun identity(command: String): String {
        val words = shellWords(command) ?: return command
        return (if (words.firstOrNull() == "sudo") listOf("sudo") + words.drop(1).filter { it != "-n" } else words).joinToString("\u0000")
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

/** Task-level handoff only: generated collection shell never survives as a runnable plan. */
internal class AutoTaskHandoff(val remaining: List<SuggestedCommand>, val lastAttempted: SuggestedCommand?,
                               val attemptedCount: Int, val finished: Boolean)

/** Foreground, in-memory scope. No approval survives a stop, host change or plan revision. */
internal class AutoRunSession(
    steps: List<SuggestedCommand>, val host: HostSession, revision: Long,
    val runReadOnly: Boolean, startIndex: Int, val runAllReviewed: Boolean = false,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    var steps = steps.toList()
        private set
    var revision = revision
        private set
    val automatic get() = runReadOnly || runAllReviewed
    fun canRun(step: SuggestedCommand) = if (runAllReviewed) collection.owns(step.command) || AutoRunPolicy.canRunReviewed(step) else runReadOnly && AutoRunPolicy.canRun(step)
    val collection = AutoOutputCollection()
    val history = AutoTaskHistory(steps, startIndex)
    private val taskSources = IdentityHashMap<SuggestedCommand, SuggestedCommand>()
    private val startedAt = clockMillis()
    val expired get() = clockMillis() - startedAt >= MAX_DURATION_MS
    private fun task(step: SuggestedCommand) = taskSources[step] ?: collection.operation(step.command)?.ticket?.original ?: step
    fun originalTask(step: SuggestedCommand) = task(step)
    private fun note(state: AutoTaskState) { current?.let { history.note(task(it), state) } }
    val transmitted: List<String> get() = executed.toList()
    private val executed = mutableSetOf<String>()
    private val startedIndexes = mutableSetOf<Int>()
    private val startedTickets = mutableSetOf<String>()
    private val attemptedTasks = mutableListOf<SuggestedCommand>()
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

    init { require(startIndex in steps.indices && steps.size <= MAX_STEPS) }

    fun valid(currentHost: HostSession?, currentRevision: Long) = active &&
        currentHost == host && currentRevision == revision && !expired

    fun approve(currentHost: HostSession?, currentRevision: Long, human: Boolean): AutoStepApproval? {
        val step = current ?: return null
        if (!valid(currentHost, currentRevision) || stage != AutoStage.REVIEW_STEP || alreadyApproved) return null
        val enter = canRun(step)
        if (!human && !enter) return null
        val operation = collection.operation(step.command)
        val source = operation?.ticket?.original ?: step
        if (!human && (operation == null || operation.page == 0) && !AutoRunPolicy.isReadOnly(source) &&
            executed.any { AutoRunPolicy.identity(it) == AutoRunPolicy.identity(source.command) }) return null
        alreadyApproved = true
        note(AutoTaskState.PREPARING)
        stage = AutoStage.TYPING
        return AutoStepApproval(CommandApproval(step.command, host), enter, generation, index)
    }

    fun accepts(approval: AutoStepApproval, currentHost: HostSession?, currentRevision: Long) =
        valid(currentHost, currentRevision) && stage == AutoStage.TYPING &&
        approval.generation == generation && approval.index == index && approval.text.host == host &&
        approval.text.command == current?.command && (!approval.enter || canRun(current!!))

    fun typed(approval: AutoStepApproval): Boolean {
        if (stage != AutoStage.TYPING || approval.generation != generation || approval.index != index) return false
        transmissionStarting()
        executed.add(collection.original(approval.text.command))
        note(AutoTaskState.WAITING)
        stage = if (approval.enter) AutoStage.WAITING else AutoStage.MANUAL_ENTER
        return true
    }

    /** Called at the final send boundary, including transmissions interrupted halfway. */
    fun transmissionStarting() {
        check(stage == AutoStage.TYPING)
        if (!startedIndexes.add(index)) return
        note(AutoTaskState.SENDING)
        val step = current!!
        val operation = collection.operation(step.command)
        if (operation == null) attemptedTasks.add(step)
        else if (startedTickets.add(operation.ticket.token)) attemptedTasks.add(operation.ticket.original)
    }

    fun handoff(): AutoTaskHandoff {
        val seenTickets = mutableSetOf<String>()
        val pending = steps.drop(index).mapIndexedNotNull { offset, step ->
            val operation = collection.operation(step.command)
            if (operation != null) {
                if (operation.ticket.token in startedTickets || !seenTickets.add(operation.ticket.token)) null
                else operation.ticket.original
            } else if (index + offset in startedIndexes ||
                (!AutoRunPolicy.isReadOnly(step) && attemptedTasks.any { AutoRunPolicy.identity(it.command) == AutoRunPolicy.identity(step.command) })) null
            else step
        }
        return AutoTaskHandoff(pending, attemptedTasks.lastOrNull(), attemptedTasks.size, stage == AutoStage.FINISHED)
    }

    fun userRanCommand(): Boolean {
        if (stage != AutoStage.MANUAL_ENTER) return false
        stage = AutoStage.WAITING
        return true
    }

    fun captured(): Long? {
        if (stage != AutoStage.WAITING || captures >= MAX_CAPTURES) { block(); return null }
        captures++
        note(AutoTaskState.CHECKING)
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

    fun waitAgain(): Boolean {
        if (stage != AutoStage.REVIEW_RESULT) return false
        generation++
        stage = AutoStage.WAITING
        return true
    }

    fun prepareCurrent(newRevision: (List<SuggestedCommand>) -> Long) {
        if (!runAllReviewed || stage != AutoStage.REVIEW_STEP) return
        val step = current ?: return
        val prepared = collection.prepare(step)
        if (prepared === step) return
        taskSources[prepared] = task(step)
        val updated = steps.toMutableList().apply { set(index, prepared) }
        revision = newRevision(updated)
        steps = updated
        generation++
    }

    fun insertCollection(step: SuggestedCommand, newRevision: Long): Boolean {
        if (stage != AutoStage.REVIEW_RESULT || !runAllReviewed || !collection.owns(step.command) || steps.size >= MAX_STEPS) return false
        taskSources[step] = task(step)
        steps = steps.take(index + 1) + step + steps.drop(index + 1)
        revision = newRevision
        index++
        generation++
        alreadyApproved = false
        stage = AutoStage.REVIEW_STEP
        return true
    }

    fun block() { generation++; history.interrupt(); stage = AutoStage.BLOCKED }
    fun stop() { generation++; history.interrupt(); stage = AutoStage.STOPPED }

    /** Only a final collected task result, not a capture/page/cleanup success, can complete a task. */
    fun recordEvidence(value: AutoObservation): Boolean {
        if (stage != AutoStage.REVIEW_RESULT) return false
        val step = current ?: return false
        val op = collection.operation(step.command)
        if (op != null && op.page >= 0) return false
        val result = if (op == null) value else op.ticket.conclusion ?: return false
        val complete = if (op == null) value.allowsNext(step.command) else
            value.collectionReady(op) && !op.ticket.truncated && op.ticket.exitCode == "0" &&
                result.pageState == "done" && result.outcome == "appears_complete" &&
                !result.revisionNeeded && op.ticket.pages.isNotEmpty()
        note(if (complete) AutoTaskState.OBSERVED else if (result.outcome == "error" ||
            op?.ticket?.exitCode?.let { it != "0" } == true) AutoTaskState.FAILED else AutoTaskState.UNCERTAIN)
        return complete
    }

    fun canAdopt(candidate: CommandPlan): Boolean = stage == AutoStage.REVIEW_RESULT && runAllReviewed && revisions < MAX_REVISIONS &&
        candidate.questions.isEmpty() &&
        index + 1 + candidate.commands.size <= MAX_STEPS && candidate.commands.all {
            canRun(it) &&
                (executed.none { previous -> AutoRunPolicy.identity(previous) == AutoRunPolicy.identity(collection.original(it.command)) } || AutoRunPolicy.isReadOnly(it))
        }

    fun adopt(candidate: CommandPlan, newRevision: Long): Boolean {
        if (!canAdopt(candidate) || stage != AutoStage.REVIEW_RESULT) return false
        history.replaceRemaining(steps.drop(index + 1).map(::task), candidate.commands)
        steps = steps.take(index + 1) + candidate.commands
        revision = newRevision
        revisions++
        index++
        generation++
        alreadyApproved = false
        stage = if (index >= steps.size) AutoStage.FINISHED else AutoStage.REVIEW_STEP
        return true
    }
    companion object {
        const val MAX_CAPTURES = 120; const val MAX_REVISIONS = 12; const val MAX_STEPS = 240
        const val MAX_DURATION_MS = 45 * 60 * 1000L
    }
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
        session.transmissionStarting()
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
