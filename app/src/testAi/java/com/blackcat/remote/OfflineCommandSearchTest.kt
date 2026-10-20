package com.blackcat.remote

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class OfflineCommandSearchTest {
    companion object {
        private val data by lazy { OfflineLearningCatalog.parse(listOf(File("../catalog/offline-catalog.json"), File("catalog/offline-catalog.json")).first { it.exists() }.readText()) }
        private val index by lazy { OfflineCommandSearch(data) }
    }
    @Test fun ordinaryTaskLanguageFindsConnectionInspectionBeforeRiskierChanges() {
        val hits = index.search("I want to check all internet connections", "Ubuntu Linux")
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.take(12).any { data.commandById[it.id]?.command?.startsWith("ss ") == true || it.title == "ss" })
        assertTrue(hits.map { it.topic }.distinct().size > 1)
        assertTrue(hits.take(5).mapNotNull { data.commandById[it.id] }.all { it.risk == LinuxCommandRisk.LOW })
    }
    @Test fun commonTasksFindRealCommandsAndRetainTheirTopics() {
        for ((query, binary) in listOf("my disk is full" to "df", "show memory usage" to "free",
            "why is my wifi broken" to "nmcli", "look up a domain dns" to "dig",
            "check failed services" to "systemctl", "show current processes" to "ps")) {
            val hits = index.search(query, "Kali Linux")
            assertTrue("$query missing $binary", hits.take(30).any { h -> h.title == binary || data.commandById[h.id]?.command?.substringBefore(' ') == binary })
            assertTrue(hits.all { it.topic.isNotBlank() })
        }
    }
    @Test fun exactSubcommandsAndSingleCharacterBuiltinsRemainFindable() {
        for (query in listOf("git add", "[", ".", ":", "nmap")) {
            val entry = data.entries.first { it.invocation == query && it.id in data.workflows.commands }
            assertTrue("Exact match lost: $query", index.search(query, "Kali Linux").take(5).any { it.id == entry.id })
        }
    }
    @Test fun osScopeIncludesCommonLinuxAndExcludesUnverifiedKaliOnlyTools() {
        assertTrue(index.search("nmap", "Kali Linux").any { it.title == "nmap" })
        assertFalse(index.search("nmap", "Ubuntu Linux").any { it.kind == OfflineCommandSearch.Kind.COMMAND && it.title == "nmap" })
        assertTrue(index.search("ss", "Ubuntu Linux").any { it.title == "ss" })
        assertFalse(index.search("installed packages", "Fedora Linux").any { data.commandById[it.id]?.command?.startsWith("apt ") == true })
        assertFalse(index.search("installed packages", "Alpine Linux").any { data.commandById[it.id]?.compatibility?.contains("systemd") == true })
        assertTrue(index.search("show connections", "Windows / PowerShell").isEmpty())
    }
    @Test fun searchIsBoundedDeterministicAndDoesNotInventCommands() {
        assertTrue(index.search("I want to please", "Kali Linux").isEmpty())
        assertTrue(index.search("zzyxnoexistent", "Kali Linux").isEmpty())
        val hits = index.search("network", "Kali Linux")
        assertTrue(hits.size > 60)
        assertEquals(hits, index.search("network", "Kali Linux"))
        assertTrue(hits.all { it.id in data.commandById || it.id in data.entryById || it.id in data.toolById })
    }
    @Test fun conversationalCheckDoesNotAddUnrelatedTopicsJustContainingThatVerb() {
        val concise = index.search("internet connections", "Kali Linux")
        val conversational = index.search("I want to check all internet connections", "Kali Linux")
        assertEquals(concise.map { it.id }.toSet(), conversational.map { it.id }.toSet())
        assertEquals(concise.map { it.topic }.toSet(), conversational.map { it.topic }.toSet())
    }
}
