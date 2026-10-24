package com.blackcat.remote

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** A UI-reviewed destination. Epoch changes whenever the HID session changes. */
data class HostSession(val id: String, val epoch: Long, val label: String)

interface CommandTarget {
    fun current(): HostSession?
    /** Called on the main thread, never by the API client. */
    fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean
}

object CommandPolicy {
    const val MAX_LENGTH = 512
    fun problem(command: String): String? = when {
        command.isBlank() -> "Command is empty."
        command.length > MAX_LENGTH -> "Command exceeds 512 characters."
        command.any { it.code !in 32..126 } ->
            "Only single-line printable US-ASCII commands can be sent. No Enter, tab, escape or Unicode controls."
        command.any { HidReports.char(it) == null } -> "Unsupported keyboard character."
        else -> null
    }
}

/** One use only. Created by the confirmation button, never by the model parser. */
class CommandApproval(val command: String, val host: HostSession) {
    private var consumed = false
    @Synchronized fun consume(): Boolean {
        if (consumed) return false
        consumed = true
        return true
    }
    override fun toString() = "CommandApproval(redacted)"
}

enum class SendOutcome { REPORTS_ACCEPTED, REJECTED, HOST_CHANGED, INVALID, ALREADY_USED }

object ApprovedCommandSender {
    suspend fun send(approval: CommandApproval, target: CommandTarget): SendOutcome {
        if (!approval.consume()) return SendOutcome.ALREADY_USED
        if (CommandPolicy.problem(approval.command) != null) return SendOutcome.INVALID
        try {
            for (character in approval.command) {
                currentCoroutineContext().ensureActive()
                if (target.current() != approval.host) return SendOutcome.HOST_CHANGED
                val (usage, modifier) = HidReports.char(character) ?: return SendOutcome.INVALID
                // Policy admits printable characters only; no Enter or Tab can reach HID.
                if (!target.sendKey(approval.host, modifier, usage)) return SendOutcome.REJECTED
                delay(20)
            }
            if (target.current() != approval.host) return SendOutcome.HOST_CHANGED
            return SendOutcome.REPORTS_ACCEPTED
        } finally {
            // Never send a release to a different/reconnected host. No queue survives cancellation.
            if (target.current() == approval.host) target.sendKey(approval.host, 0, 0)
        }
    }
}
