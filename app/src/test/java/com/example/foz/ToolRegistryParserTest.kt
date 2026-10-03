package com.example.foz

import com.example.foz.ai.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryParserTest {

    @Test
    fun `plain answer without json returns null`() {
        assertNull(ToolRegistry.parseToolCallStatic("The Titanic was released in 1997."))
    }

    @Test
    fun `simple tool call is parsed`() {
        val call = ToolRegistry.parseToolCallStatic(
            """{"tool": "get_events", "arguments": {"range": "today"}}"""
        )
        assertNotNull(call)
        assertEquals("get_events", call!!.tool)
        assertEquals("today", call.arguments.optString("range"))
    }

    @Test
    fun `fenced tool call is parsed`() {
        val call = ToolRegistry.parseToolCallStatic(
            "```json\n{\"tool\": \"set_alarm\", \"arguments\": {\"hour\": 7, \"minute\": 30}}\n```"
        )
        assertNotNull(call)
        assertEquals("set_alarm", call!!.tool)
        assertEquals(7, call.arguments.optInt("hour"))
        assertEquals(30, call.arguments.optInt("minute"))
    }

    @Test
    fun `tool call with leading text is parsed`() {
        val call = ToolRegistry.parseToolCallStatic(
            "Let me check that for you. {\"tool\": \"list_notes\", \"arguments\": {}}"
        )
        assertNotNull(call)
        assertEquals("list_notes", call!!.tool)
        assertEquals(0, call.arguments.length())
    }

    @Test
    fun `missing arguments becomes empty object`() {
        val call = ToolRegistry.parseToolCallStatic("""{"tool": "get_weather"}""")
        assertNotNull(call)
        assertEquals("get_weather", call!!.tool)
        assertEquals(0, call.arguments.length())
    }

    @Test
    fun `malformed json returns null`() {
        assertNull(ToolRegistry.parseToolCallStatic("{tool: broken"))
        assertNull(ToolRegistry.parseToolCallStatic(""))
        assertNull(ToolRegistry.parseToolCallStatic("just text with no braces"))
    }

    @Test
    fun `json without tool key returns null`() {
        assertNull(ToolRegistry.parseToolCallStatic("""{"foo": "bar"}"""))
    }

    @Test
    fun `nested arguments survive extraction`() {
        val call = ToolRegistry.parseToolCallStatic(
            """{"tool": "create_event", "arguments": {"title": "Dentist", "date": "2026-10-04", "time": "15:00", "duration_minutes": 45}}"""
        )
        assertNotNull(call)
        assertEquals("create_event", call!!.tool)
        assertTrue(call.arguments.length() == 4)
        assertEquals("Dentist", call.arguments.optString("title"))
    }
}
