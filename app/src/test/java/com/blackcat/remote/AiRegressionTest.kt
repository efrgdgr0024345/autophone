package com.blackcat.remote

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AiRegressionTest {
    private val host = HostSession("test-host", 10, "Local test")
    private class Target(var session: HostSession?) : CommandTarget {
        val reports = mutableListOf<Pair<Int, Int>>()
        var afterReport: (() -> Unit)? = null
        var accept = true
        override fun current() = session
        override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
            if (this.session != session) return false
            reports.add(modifier to usage)
            afterReport?.invoke()
            return accept
        }
    }

    @Test fun allPrintableCharactersNeverProduceEnter() = runBlocking {
        val target = Target(host)
        val text = (32..126).map { it.toChar() }.joinToString("")
        assertEquals(SendOutcome.REPORTS_ACCEPTED, ApprovedCommandSender.send(CommandApproval(text, host), target))
        assertEquals(96, target.reports.size)
        assertTrue(target.reports.none { it.second == 40 || it.second == 43 })
    }
    @Test fun disconnectStopsRemainingCharacters() = runBlocking {
        val target = Target(host)
        target.afterReport = { if (target.reports.size == 2) target.session = null }
        assertEquals(SendOutcome.HOST_CHANGED, ApprovedCommandSender.send(CommandApproval("whoami", host), target))
        assertEquals(2, target.reports.size)
    }
    @Test fun reconnectToSameAddressStillInvalidatesApproval() = runBlocking {
        val target = Target(host)
        target.afterReport = { target.session = host.copy(epoch = 11) }
        assertEquals(SendOutcome.HOST_CHANGED, ApprovedCommandSender.send(CommandApproval("whoami", host), target))
        assertEquals(1, target.reports.size)
    }
    @Test fun cancelledSequenceHasNoReplay() = runBlocking {
        val target = Target(host)
        lateinit var sending: Job
        target.afterReport = { if (target.reports.size == 1) sending.cancel() }
        sending = launch(start = CoroutineStart.LAZY) { ApprovedCommandSender.send(CommandApproval("whoami", host), target) }
        sending.start()
        sending.join()
        assertTrue(sending.isCancelled)
        assertEquals(2, target.reports.size)
        assertEquals(0 to 0, target.reports.last())
    }
    @Test fun rejectedReportDoesNotContinue() = runBlocking {
        val target = Target(host).apply { accept = false }
        assertEquals(SendOutcome.REJECTED, ApprovedCommandSender.send(CommandApproval("whoami", host), target))
        assertEquals(2, target.reports.size) // attempted key and neutral release only
    }
    @Test fun oversizedOrInvisibleTextRejectedBeforeAnySend() = runBlocking {
        for (text in listOf("x".repeat(513), "id\u202e", "id\u0000", "id\u001b")) {
            val target = Target(host)
            assertEquals(SendOutcome.INVALID, ApprovedCommandSender.send(CommandApproval(text, host), target))
            assertTrue(target.reports.isEmpty())
        }
    }
}
