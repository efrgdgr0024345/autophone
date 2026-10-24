package com.blackcat.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandSafetyTest {
    private val host = HostSession("host", 1, "Test")
    private class Fake(var now: HostSession?) : CommandTarget {
        val reports = mutableListOf<Pair<Int,Int>>()
        override fun current() = now
        override fun sendKey(session: HostSession, modifier: Int, usage: Int): Boolean {
            if (session != now) return false
            reports += modifier to usage
            return true
        }
    }

    @Test fun normalCommandAccepted() { assertNull(CommandPolicy.problem("sudo adduser alice")) }
    @Test fun controlsRejected() {
        for (s in listOf("id\nwhoami","id\r","id\t","\u001b[2J","echo 😀")) assertNotNull(CommandPolicy.problem(s))
    }
    @Test fun allPrintableAsciiMapsWithoutEnterOrTab() {
        for (code in 32..126) {
            val report = HidReports.char(code.toChar())!!
            assertNotEquals(40, report.first)
            assertNotEquals(43, report.first)
        }
    }
    @Test fun approvalDoesNotTypeByItself() {
        val target = Fake(host)
        CommandApproval("id", host)
        assertTrue(target.reports.isEmpty())
    }
    @Test fun senderTypesWithoutEnter() = runBlocking {
        val target = Fake(host)
        assertEquals(SendOutcome.REPORTS_ACCEPTED, ApprovedCommandSender.send(CommandApproval("id",host),target))
        assertEquals(listOf(0 to 12, 0 to 7, 0 to 0), target.reports)
    }
    @Test fun wrongSessionBlocked() = runBlocking {
        val target = Fake(host.copy(epoch=2))
        assertEquals(SendOutcome.HOST_CHANGED, ApprovedCommandSender.send(CommandApproval("id",host),target))
        assertTrue(target.reports.isEmpty())
    }
    @Test fun approvalCannotReplay() = runBlocking {
        val target = Fake(host)
        val approval = CommandApproval("id",host)
        ApprovedCommandSender.send(approval,target)
        val size = target.reports.size
        assertEquals(SendOutcome.ALREADY_USED, ApprovedCommandSender.send(approval,target))
        assertEquals(size,target.reports.size)
    }
    @Test fun invalidWholeCommandBlockedBeforeTyping() = runBlocking {
        val target = Fake(host)
        assertEquals(SendOutcome.INVALID, ApprovedCommandSender.send(CommandApproval("id\n",host),target))
        assertTrue(target.reports.isEmpty())
    }
}
