package app.godeye
import org.junit.Assert.*
import org.junit.Test
class ReportParserTest {
    @Test fun parsesStructuredOutput() {
        val result = ReportParser.parse("""{"summary":"Scene", "observations":["A table is visible"], "uncertainties":["Location is unclear"]}""")
        assertEquals("Scene", result.summary)
        assertEquals(2, result.sections.size)
        assertTrue(result.sections.first().text.contains("table"))
    }
    @Test fun acceptsJsonFence() {
        val result = ReportParser.parse("```json\n{\"observations\":[\"test\"]}\n```")
        assertTrue(result.sections.first().text.contains("test"))
    }
    @Test fun keepsPlainText() {
        val result = ReportParser.parse("Plain text")
        assertEquals("Plain text", result.sections.single().text)
    }
    @Test fun keepsUnrecognizedJson() {
        val raw = """{"answer":"hello"}"""
        assertEquals(raw, ReportParser.parse(raw).sections.single().text)
    }
    @Test fun omitsNullAndEmptyLayers() {
        val result = ReportParser.parse("""{"observations":["visible"],"hypotheses":null,"uncertainties":[]}""")
        assertEquals(1, result.sections.size)
    }
}
