package com.mnemo.intelligence.intent

import com.mnemo.core.model.AssistantIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RuleBasedCommandParserTest {

    private lateinit var parser: RuleBasedCommandParser

    @Before
    fun setUp() {
        parser = RuleBasedCommandParser()
    }

    @Test
    fun testFindContactCommand() {
        val cmd = parser.parse("Find Rahul's phone number.")
        assertEquals(AssistantIntent.FIND_CONTACT, cmd.intent)
        assertEquals("Rahul", cmd.arguments["query"])
        assertTrue(cmd.confidenceScore > 0.8f)
    }

    @Test
    fun testShowCalendarEventsCommand() {
        val cmd = parser.parse("Show my upcoming calendar events.")
        assertEquals(AssistantIntent.SHOW_CALENDAR_EVENTS, cmd.intent)
        assertTrue(cmd.confidenceScore > 0.8f)
    }

    @Test
    fun testSearchMessagesCommand() {
        val cmd = parser.parse("Find the message where someone mentioned a meeting.")
        assertEquals(AssistantIntent.SEARCH_MESSAGES, cmd.intent)
        assertTrue(cmd.confidenceScore > 0.8f)
    }

    @Test
    fun testOpenAppCommand() {
        val cmd = parser.parse("Open the calculator.")
        assertEquals(AssistantIntent.OPEN_APPLICATION, cmd.intent)
        assertEquals("calculator.", cmd.arguments["appName"])
        assertTrue(cmd.confidenceScore > 0.9f)
    }

    @Test
    fun testFindFilesCommand() {
        val cmd = parser.parse("Find my downloaded PDF files.")
        assertEquals(AssistantIntent.FIND_FILES, cmd.intent)
        assertEquals("pdf", cmd.arguments["extension"])
    }

    @Test
    fun testSummarizeTodayCommand() {
        val cmd = parser.parse("Give me a summary of today's important events.")
        assertEquals(AssistantIntent.SUMMARIZE_TODAY, cmd.intent)
    }

    @Test
    fun testExtractCommitmentsCommand() {
        val cmd = parser.parse("What commitments did I make this week?")
        assertEquals(AssistantIntent.EXTRACT_COMMITMENTS, cmd.intent)
    }

    @Test
    fun testBlankQueryFallback() {
        val cmd = parser.parse("   ")
        assertEquals(AssistantIntent.UNKNOWN, cmd.intent)
        assertEquals(0.0f, cmd.confidenceScore, 0.001f)
    }
}
