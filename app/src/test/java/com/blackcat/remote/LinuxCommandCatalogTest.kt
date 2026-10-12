package com.blackcat.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxCommandCatalogTest {
    private val sample = listOf(
        "id\tcategory\ttopic\ttitle\tcommand\texplanation\texpected\trisk\tadmin\tcompatibility\ttags",
        "net-status\tNetwork\tInterfaces\tShow network status\tip -brief address\tShows interfaces and addresses.\tOne line per interface.\tlow\tfalse\tMost Linux distributions\tnetwork,internet,ip,status",
        "mem-free\tPerformance\tMemory\tShow memory usage\tfree -h\tShows RAM and swap use.\tA memory table.\tlow\tfalse\tMost Linux distributions\tmemory,ram",
        "disk-space\tStorage\tCapacity\tShow disk space\tdf -hT\tShows filesystem capacity.\tA filesystem table.\tlow\tfalse\tMost Linux distributions\tdisk,space"
    ).joinToString("\n")

    @Test
    fun parsesAndPreservesTreeOrder() {
        val items = LinuxCommandCatalog.parse(sample)
        assertEquals(3, items.size)
        assertEquals(
            listOf("Network", "Performance", "Storage"),
            LinuxCommandCatalog.categories(items).map { it.name }
        )
        assertEquals("Interfaces", LinuxCommandCatalog.topics(items, "Network").single().name)
    }

    @Test
    fun searchUsesTaskWordsAndSynonyms() {
        val items = LinuxCommandCatalog.parse(sample)
        assertEquals("net-status", LinuxCommandCatalog.search(items, "internet status").first().id)
        assertEquals("mem-free", LinuxCommandCatalog.search(items, "ram").first().id)
        assertEquals("disk-space", LinuxCommandCatalog.search(items, "disk space").first().id)
    }

    @Test
    fun blankSearchDoesNotDumpTheWholeCatalog() {
        val items = LinuxCommandCatalog.parse(sample)
        assertTrue(LinuxCommandCatalog.search(items, "   ").isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdentifiersAreRejected() {
        LinuxCommandCatalog.parse(sample + "\n" + sample.lineSequence().drop(1).first())
    }

    @Test(expected = IllegalArgumentException::class)
    fun controlCharactersInCommandsAreRejected() {
        val invalid = listOf(
            "id\tcategory\ttopic\ttitle\tcommand\texplanation\texpected\trisk\tadmin\tcompatibility\ttags",
            "bad-command\tSystem\tTest\tBad command\tuname\n-a\tBad.\tBad.\tlow\tfalse\tLinux\tbad"
        ).joinToString("\n")
        LinuxCommandCatalog.parse(invalid)
    }
}
